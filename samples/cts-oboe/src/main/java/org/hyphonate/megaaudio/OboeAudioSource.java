package org.hyphonate.megaaudio;

import android.util.Log;

import org.hyphonate.megaaudio.player.AudioSource;

import java.util.concurrent.ArrayBlockingQueue;

public class OboeAudioSource extends AudioSource {

    private static final String TAG = "OboeAudioSource";
    private ArrayBlockingQueue<Float> cacheBuffer;


    public OboeAudioSource(ArrayBlockingQueue<Float> cacheBuffer) {
        this.cacheBuffer = cacheBuffer;
        Log.e(TAG,"OboeAudioSource pull Thread:" + Thread.currentThread().getId());

    }

    @Override
    public int pull(float[] buffer, int numFrames, int numChannels) {
        Log.e(TAG,"buffer length:" + buffer.length + ", numFrames:" + numFrames);
        for (int i = 0; i < buffer.length; i++) {
            try {
                buffer[i] = cacheBuffer.take();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return buffer.length / 2 ;
    }
}
