package org.hyphonate.megaaudio;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;

import org.hyphonate.megaaudio.common.BuilderBase;
import org.hyphonate.megaaudio.common.StreamBase;
import org.hyphonate.megaaudio.duplex.DuplexAudioManager;
import org.hyphonate.megaaudio.player.AudioSource;
import org.hyphonate.megaaudio.player.AudioSourceProvider;
import org.hyphonate.megaaudio.player.JavaSourceProxy;
import org.hyphonate.megaaudio.player.Player;
import org.hyphonate.megaaudio.player.PlayerBuilder;
import org.hyphonate.megaaudio.recorder.AudioSinkProvider;
import org.hyphonate.megaaudio.recorder.sinks.AppCallbackAudioSinkProvider;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "AudioTap2ToneActivity";
    // JNI load
    static {
        try {
            System.loadLibrary("megaaudio_jni");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Error loading MegaAudio JNI library");
            Log.e(TAG, "e: " + e);
            e.printStackTrace();
        }

        /* TODO: gracefully fail/notify if the library can't be loaded */
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        JavaSourceProxy.initN();
        configureOboeAudioPlayer(0,0);
        startOboeAudioPlayer();
    }


    @Override
    protected void onPause() {
        super.onPause();
        stopOboeAudioPlayer();
    }

    private int mNumPlayerChannels = 2;
    private int mPlayerSampleRate = 48000;
    private int mNumPlayerBufferFrames;
    private Player mPlayer;
    private AudioSourceProvider mSourceProvider;
    private int mPlayerType = BuilderBase.TYPE_NONE;

    private void configureOboeAudioPlayer(int channelCount, int sampleRateHz){
        if (channelCount != 0 && sampleRateHz != 0){
            this.mNumPlayerChannels = channelCount;
            this.mPlayerSampleRate = sampleRateHz;
        }
        mNumPlayerBufferFrames =
                Player.calcMinBufferFrames(mNumPlayerChannels, mPlayerSampleRate);
        Log.e(TAG,"configure oboe audio player with channelCount:" + mNumPlayerChannels
                + ", sampleRateHz:" + mPlayerSampleRate
                + ",miniBufferFrames:" + mNumPlayerBufferFrames);

        mSourceProvider = new BlipAudioSourceProvider();
        mPlayerType = BuilderBase.TYPE_OBOE | BuilderBase.SUB_TYPE_OBOE_AAUDIO;
    }


    private void startOboeAudioPlayer() {
        try {
            mPlayer = new PlayerBuilder()
                    .setPlayerType(mPlayerType)
                    .setSourceProvider(mSourceProvider)
                    .build();
            int errorCode = mPlayer.setupStream(
                    mNumPlayerChannels, mPlayerSampleRate, mNumPlayerBufferFrames);
            if (errorCode != StreamBase.OK) {
                Log.e(TAG, "Player - setupStream() failed code: " + errorCode);
            }
            errorCode = mPlayer.startStream();
            if (errorCode != StreamBase.OK) {
                Log.e(TAG, "Player - startStream() failed code: " + errorCode);
            }
        } catch (PlayerBuilder.BadStateException ex) {
            Log.e(TAG, "Player - BadStateException" + ex);
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


}