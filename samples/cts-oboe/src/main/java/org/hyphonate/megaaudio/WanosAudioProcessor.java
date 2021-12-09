package org.hyphonate.megaaudio;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;

import org.hyphonate.megaaudio.common.BuilderBase;
import org.hyphonate.megaaudio.common.StreamBase;
import org.hyphonate.megaaudio.player.AudioSourceProvider;
import org.hyphonate.megaaudio.player.JavaSourceProxy;
import org.hyphonate.megaaudio.player.Player;
import org.hyphonate.megaaudio.player.PlayerBuilder;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WanosAudioProcessor implements AudioProcessor {

    private static final String TAG = "NewTVTeeAudioProcessor";

    /**
     * A sink for audio buffers handled by the audio processor.
     */
    public interface AudioBufferSink {
        /**
         * Called when the audio param is written
         */
        void configure(int sampleRate, int channelCount, int encoding);

        /**
         * Called when the audio processor is flushed with a format of subsequent input.
         */
        void flush(int sampleRateHz, int channelCount, @C.PcmEncoding int encoding);

        /**
         * Called when data is written to the audio processor.
         *
         * @param buffer A read-only buffer containing input which the audio processor will handle.
         */
        boolean handleBuffer(ByteBuffer buffer);

        void reset();
    }

    private final AudioBufferSink audioBufferSink;

    private int sampleRateHz;
    private int channelCount;
    private @C.Encoding
    int encoding;
    private boolean isActive;

    private ByteBuffer buffer;
    private ByteBuffer outputBuffer;
    private boolean inputEnded;

    private ByteBuffer zeroBuffer;


    /**
     * Creates a new tee audio processor, sending incoming data to the given
     * {@link com.google.android.exoplayer2.audio.TeeAudioProcessor.AudioBufferSink}.
     *
     * @param audioBufferSink The audio buffer sink that will receive input queued to this audio
     *                        processor.
     */
    public WanosAudioProcessor(AudioBufferSink audioBufferSink) {
        this.audioBufferSink = Assertions.checkNotNull(audioBufferSink);

        buffer = EMPTY_BUFFER;
        outputBuffer = EMPTY_BUFFER;
        channelCount = Format.NO_VALUE;
        sampleRateHz = Format.NO_VALUE;
    }

    @Override
    public AudioFormat configure(AudioFormat audioFormat) throws UnhandledAudioFormatException {
        this.sampleRateHz = audioFormat.sampleRate;
        this.channelCount = audioFormat.channelCount;
        this.encoding = audioFormat.encoding;
        audioBufferSink.configure(sampleRateHz, channelCount, encoding);
        isActive = true;
        return audioFormat;
    }

    @Override
    public boolean isActive() {
        return isActive;
    }


    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        int remaining = inputBuffer.remaining();
        if (remaining == 0) {
            return;
        }

        boolean handled = audioBufferSink.handleBuffer(inputBuffer);

        if (this.buffer.capacity() < remaining) {
            this.buffer = ByteBuffer.allocateDirect(remaining).order(ByteOrder.nativeOrder());
        } else {
            this.buffer.clear();
        }

        if (handled) {
            if (zeroBuffer == null || zeroBuffer.capacity() < remaining) {
                zeroBuffer = ByteBuffer.allocate(remaining);
            }
            zeroBuffer.rewind();
            this.buffer.put(zeroBuffer);
            inputBuffer.position(inputBuffer.position() + remaining);
        } else {
            this.buffer.put(inputBuffer);
        }

        this.buffer.flip();
        outputBuffer = this.buffer;
    }

    @Override
    public void queueEndOfStream() {
        inputEnded = true;
    }

    @Override
    public ByteBuffer getOutput() {
        ByteBuffer outputBuffer = this.outputBuffer;
        this.outputBuffer = EMPTY_BUFFER;
        return outputBuffer;
    }

    @Override
    public boolean isEnded() {
        return inputEnded && outputBuffer == EMPTY_BUFFER;
    }

    @Override
    public void flush() {
        outputBuffer = EMPTY_BUFFER;
        inputEnded = false;
        audioBufferSink.flush(sampleRateHz, channelCount, encoding);
    }

    @Override
    public void reset() {
        flush();
        buffer = EMPTY_BUFFER;
        sampleRateHz = Format.NO_VALUE;
        channelCount = Format.NO_VALUE;
        encoding = Format.NO_VALUE;
        audioBufferSink.reset();
    }

    public static class OboeAudioBufferSink implements AudioBufferSink {
        private static final String TAG = "OboeAudioBufferSink";

        private int mNumPlayerChannels = 2;
        private int mPlayerSampleRate = 48000;
        private int mEncoding = C.ENCODING_PCM_16BIT;
        private int mNumPlayerBufferFrames;
        private Player mPlayer;
        private AudioSourceProvider mSourceProvider;
        private int mPlayerType = BuilderBase.TYPE_NONE;

        private ArrayBlockingQueue<Float> cacheBuffer;
        private static final float MAX_TOUCH_LATENCY = 0.200f;
        private static final float MAX_OUTPUT_LATENCY = 0.600f;
        private static final float ANALYSIS_TIME_MARGIN = 0.250f;

        private static final float ANALYSIS_TIME_DELAY = MAX_OUTPUT_LATENCY;
        private static final float ANALYSIS_TIME_TOTAL = MAX_TOUCH_LATENCY + MAX_OUTPUT_LATENCY;
        private static final float ANALYSIS_TIME_MAX = ANALYSIS_TIME_TOTAL + ANALYSIS_TIME_MARGIN;
        private static final int ANALYSIS_SAMPLE_RATE = 48000; // need not match output rate

        private HandlerThread oboeThread;
        private Handler oboeHandler;
        private ExecutorService mExecutorService;
        ConcurrentLinkedQueue<ByteBuffer> bufferContainer;
        static {
            try {
                System.loadLibrary("megaaudio_jni");
            } catch (UnsatisfiedLinkError e) {
                android.util.Log.e(TAG, "Error loading MegaAudio JNI library");
                android.util.Log.e(TAG, "e: " + e);
                e.printStackTrace();
            }
        }

        public OboeAudioBufferSink() {
            int numBufferSamples = (int) (ANALYSIS_TIME_MAX * ANALYSIS_SAMPLE_RATE);
            cacheBuffer = new ArrayBlockingQueue<>(numBufferSamples);
            if (oboeThread == null){
                oboeThread = new HandlerThread("Oboe Audio Thread", Process.THREAD_PRIORITY_AUDIO);
                oboeThread.start();
                oboeHandler = new OboeAudioHandler(oboeThread.getLooper());
            }
            this.mExecutorService = Executors.newSingleThreadExecutor();

            oboeHandler.sendEmptyMessage(OboeAudioHandler.INIT_EVENT);

        }

        @Override
        public void configure(int sampleRate, int channelCount, int encoding) {
            Log.e(TAG, "sampleRate:" + sampleRate + ",channelCount:" + channelCount
                    + ",encoding:" + encoding);
            this.mNumPlayerChannels = channelCount;
            this.mPlayerSampleRate = sampleRate;
            this.mEncoding = encoding;
           oboeHandler.sendEmptyMessage(OboeAudioHandler.CONFIGURE_EVENT);
        }

        @Override
        public void flush(int sampleRateHz, int channelCount, int encoding) {

        }

        @Override
        public boolean handleBuffer(ByteBuffer buffer) {
//            float[] data = Utils.convertPcm16ToFloat(buffer, mEncoding);
            if (bufferContainer == null) {
                bufferContainer = new ConcurrentLinkedQueue<ByteBuffer>();
            }
            ByteBuffer temp = ByteBuffer.allocate(buffer.limit());
            temp.put(buffer);
            temp.flip();
            buffer.flip();
            bufferContainer.offer(temp);
            mExecutorService.execute(new Runnable() {
                @Override
                public void run() {
                    ByteBuffer poll = bufferContainer.poll();
                    if (poll != null) {
                        float[] data = Utils.convertPcm16ToFloat(poll, mEncoding,mNumPlayerChannels);
                        Log.e(TAG, "Byte buffer size:" + poll.limit()
                                               + ",convert to float array size:" + data.length);
                        Log.e(TAG, "Write Thread:" + Thread.currentThread().getName());
                        for (int i = 0; i < data.length; i++) {
                            try {
                                cacheBuffer.put(data[i]);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            });

            return true;
        }

        @Override
        public void reset() {
            Log.e(TAG,"Oboe reset to stop the player...");
            if (oboeHandler != null){
                oboeHandler.sendEmptyMessage(OboeAudioHandler.STOP_EVENT);
            }

//            try {
//                oboeThread.join();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }

        }


        private void configureAndStartOboeAudioPlayer() {
            mNumPlayerBufferFrames =
                    Player.calcMinBufferFrames(mNumPlayerChannels, mPlayerSampleRate);

            android.util.Log.e(TAG,"configure oboe audio player with channelCount:" + mNumPlayerChannels
                    + ", sampleRateHz:" + mPlayerSampleRate
                    + ",miniBufferFrames:" + mNumPlayerBufferFrames);
            try {
                mPlayer = new PlayerBuilder()
                        .setPlayerType(mPlayerType)
                        .setSourceProvider(mSourceProvider)
                        .build();
                int errorCode = mPlayer.setupStream(
                        mNumPlayerChannels, mPlayerSampleRate, mNumPlayerBufferFrames);
                if (errorCode != StreamBase.OK) {
                    android.util.Log.e(TAG, "Player - setupStream() failed code: " + errorCode);
                }
                errorCode = mPlayer.startStream();
                if (errorCode != StreamBase.OK) {
                    android.util.Log.e(TAG, "Player - startStream() failed code: " + errorCode);
                }
            } catch (PlayerBuilder.BadStateException ex) {
                android.util.Log.e(TAG, "Player - BadStateException" + ex);
            }
        }


        private void stopOboeAudioPlayer(){
            int playerResult = StreamBase.OK;
            if (mPlayer != null) {
                int result1 = mPlayer.stopStream();
                int result2 = mPlayer.teardownStream();
                playerResult = result1 != StreamBase.OK ? result1 : result2;
            }

            if (playerResult != StreamBase.OK){
                android.util.Log.e(TAG, "Player - stopStream() failed code: " + playerResult);
            }
        }


        class OboeAudioHandler extends Handler{
            private static final int INIT_EVENT = 1;
            private static final int CONFIGURE_EVENT = 2;
            private static final int STOP_EVENT = 3;

            public OboeAudioHandler(Looper looper) {
                super(looper);
            }

            @Override
            public void handleMessage(@NonNull Message msg) {
                switch (msg.what){
                    case INIT_EVENT:
                        JavaSourceProxy.initN();
                        mSourceProvider = new OboeAudioSourceProvider(cacheBuffer);
                        mPlayerType = BuilderBase.TYPE_OBOE | BuilderBase.SUB_TYPE_OBOE_AAUDIO;
                        break;

                    case CONFIGURE_EVENT:
                        configureAndStartOboeAudioPlayer();
                        break;

                    case STOP_EVENT:
                        Log.e(TAG,"receive stop event...");
                        stopOboeAudioPlayer();
                        break;
                    default:
                        break;
                }

            }
        }

    }
}
