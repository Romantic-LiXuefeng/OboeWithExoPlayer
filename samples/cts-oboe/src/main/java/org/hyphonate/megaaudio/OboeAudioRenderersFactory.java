package org.hyphonate.megaaudio;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.audio.AudioCapabilities;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.audio.DefaultAudioSink;
import com.google.android.exoplayer2.audio.MediaCodecAudioRenderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;

import java.util.ArrayList;

public class OboeAudioRenderersFactory extends DefaultRenderersFactory {
    private static final String TAG = "OboeAudioRenderersFactory";
    public OboeAudioRenderersFactory(Context context) {
        super(context);
    }

    @Override
    protected void buildAudioRenderers(
            Context context,
            int extensionRendererMode,
            MediaCodecSelector mediaCodecSelector,
            boolean enableDecoderFallback,
            AudioSink audioSink,
            Handler eventHandler,
            AudioRendererEventListener eventListener,
            ArrayList<Renderer> out) {
        MediaCodecAudioRenderer audioRenderer
                = new MediaCodecAudioRenderer(
                        context,
                        mediaCodecSelector,
                        enableDecoderFallback,
                        eventHandler,
                        eventListener,
                        audioSink);
        out.add(audioRenderer);
    }

    @Nullable
    @Override
    protected AudioSink buildAudioSink(
            Context context,
            boolean enableFloatOutput,
            boolean enableAudioTrackPlaybackParams,
            boolean enableOffload) {
        return new DefaultAudioSink(
                AudioCapabilities.getCapabilities(context),
                new DefaultAudioSink.DefaultAudioProcessorChain(this.buildAudioProcessor()),
                        enableFloatOutput,
                        enableAudioTrackPlaybackParams,
                        enableOffload ? DefaultAudioSink.OFFLOAD_MODE_ENABLED_GAPLESS_REQUIRED
                                                : DefaultAudioSink.OFFLOAD_MODE_DISABLED);

    }

    protected AudioProcessor[] buildAudioProcessor() {
        AudioProcessor[] processors = new AudioProcessor[]{
                new OboeAudioProcessor(
                        new OboeAudioProcessor.OboeAudioBufferSink())};
        return processors;
    }
}
