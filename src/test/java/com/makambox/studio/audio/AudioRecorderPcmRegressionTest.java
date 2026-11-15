package com.makambox.studio.audio;

/** Verifies the microphone PCM conversion without accessing audio hardware. */
public final class AudioRecorderPcmRegressionTest {
    public static void main(String[] args) {
        byte[] pcm = {
                0x00, 0x00,
                (byte) 0xff, 0x7f,
                0x00, (byte) 0x80,
                0x00, 0x40,
                0x00
        };
        float[] decoded = AudioRecorder.decodePcm16LittleEndian(pcm);
        if (decoded.length != 4
                || Math.abs(decoded[0]) > 1e-7
                || Math.abs(decoded[1] - 32767f / 32768f) > 1e-7
                || Math.abs(decoded[2] + 1f) > 1e-7
                || Math.abs(decoded[3] - .5f) > 1e-7) {
            throw new AssertionError("PCM16 little-endian microphone decoding changed");
        }
        System.out.println("AUDIO_RECORDER_PCM_OK samples=4 oddTailIgnored=true");
    }
}
