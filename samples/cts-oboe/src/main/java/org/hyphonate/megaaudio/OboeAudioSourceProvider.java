package org.hyphonate.megaaudio;

import org.hyphonate.megaaudio.player.AudioSource;
import org.hyphonate.megaaudio.player.AudioSourceProvider;
import org.hyphonate.megaaudio.player.NativeAudioSource;

import java.util.concurrent.ArrayBlockingQueue;

public class OboeAudioSourceProvider implements AudioSourceProvider {

    private AudioSource oboeAudioSource;
    public OboeAudioSourceProvider(ArrayBlockingQueue<Float> cacheBuffer) {
        oboeAudioSource = new OboeAudioSource(cacheBuffer);
    }

    @Override
    public AudioSource getJavaSource() {
        return oboeAudioSource;
    }

    @Override
    public NativeAudioSource getNativeSource() {
        return null;
    }
}
