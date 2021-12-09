package org.hyphonate.megaaudio;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Assertions;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Utils {
    public static float kScaleI16ToFloat = (1.0f / 32768.0f);

    public static float[] convertPcm16ToFloat(ByteBuffer inputBuffer, int encoding,int channelCount){
        Assertions.checkState(encoding == C.ENCODING_PCM_16BIT,
          "MediaCodec decoder can only decode to int16, we need to convert to floats");
//        int numSamples = inputBuffer.remaining() / encoding;
//        float[] outputBuffer = new float[numSamples];
//        for(int i = 0; i < outputBuffer.length; i++){
//            float val = 0;
//            if (encoding == C.ENCODING_PCM_16BIT) {
//                val = inputBuffer.asShortBuffer().get(i) * kScaleI16ToFloat;
//            }
//            outputBuffer[i] = val;
//        }
//        return outputBuffer;


        byte[] array = new byte[inputBuffer.remaining()];
        inputBuffer.get(array);

        float[] audioDataF = shortToFloat(byteToShort(array));
        for (int i = 0; i < audioDataF.length; i++) {
            audioDataF[i] /= 32768.0;
        }
        return audioDataF;


    }

//    int WavStreamReader::getDataFloat_PCM16(float *buff, int numFrames) {
//        int numChannels = mFmtChunk->mNumChannels;
//
//        int buffOffset = 0;
//        int totalFramesRead = 0;
//
//        static constexpr int kSampleSize = sizeof(int16_t);
//        static constexpr float kSampleFullScale = (float) 0x8000;
//        static constexpr float kInverseScale = 1.0f / kSampleFullScale;
//
//        int16_t readBuff[kConversionBufferFrames * numChannels];
//        int framesLeft = numFrames;
//        while (framesLeft > 0) {
//            int framesThisRead = std::min(framesLeft, kConversionBufferFrames);
//            //__android_log_print(ANDROID_LOG_INFO, TAG, "read(%d)", framesThisRead);
//            int numFramesRead =
//                    mStream->read(readBuff, framesThisRead * kSampleSize * numChannels) /
//                            (kSampleSize * numChannels);
//            totalFramesRead += numFramesRead;
//
//            // Convert & Scale
//            for (int offset = 0; offset < numFramesRead * numChannels; offset++) {
//                buff[buffOffset++] = (float) readBuff[offset] * kInverseScale;
//            }
//
//            if (numFramesRead < framesThisRead) {
//                break; // none left
//            }
//
//            framesLeft -= framesThisRead;
//        }
//
//        return totalFramesRead;
//    }


    /**
     * Convert from array of short to array of float.
     * @param shortArray short array
     * @return float array
     */
    public static float[] shortToFloat(final short[] shortArray) {
        float[] floatOut = new float[shortArray.length];
        for (int i = 0; i < shortArray.length; i++) {
            floatOut[i] = shortArray[i];
        }
        return floatOut;
    }

    /**
     * Convert from array of byte to array of short.
     * @param byteArray byte array
     * @return short array
     */
    public static short[] byteToShort(final byte[] byteArray) {
        short[] shortOut = new short[byteArray.length / 2];
        ByteBuffer byteBuffer = ByteBuffer.wrap(byteArray);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortOut);
        return shortOut;
    }

}
