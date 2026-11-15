package com.makambox.studio.dsp;

import com.makambox.studio.model.AudioData;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.concurrent.CancellationException;

public final class Spectrogram {
    private Spectrogram() {}

    public static BufferedImage render(AudioData audio, int width, int height) {
        int fftSize = 2048;
        int sampleCount = audio.samples().length;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        double minHz = 45.0, maxHz = Math.min(4_000.0, audio.sampleRate() / 2.0);
        double floor = -78.0, ceiling = -18.0;
        double[] real = new double[fftSize];
        double[] imag = new double[fftSize];
        double[] magnitudes = new double[fftSize / 2];

        for (int x = 0; x < width; x++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException("Spektrogram oluşturma iptal edildi");
            }
            int center = (int) Math.round(x / (double) Math.max(1, width - 1) * Math.max(0, sampleCount - 1));
            int start = center - fftSize / 2;
            for (int i = 0; i < fftSize; i++) {
                int source = start + i;
                double sample = source >= 0 && source < sampleCount ? audio.samples()[source] : 0.0;
                double window = 0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / (fftSize - 1));
                real[i] = sample * window;
                imag[i] = 0.0;
            }
            fft(real, imag);
            for (int bin = 0; bin < magnitudes.length; bin++) {
                magnitudes[bin] = 20.0 * Math.log10(Math.hypot(real[bin], imag[bin]) / fftSize + 1e-9);
            }
            for (int y = 0; y < height; y++) {
                double position = 1.0 - y / (double) Math.max(1, height - 1);
                double hz = minHz * Math.pow(maxHz / minHz, position);
                double binPosition = hz * fftSize / audio.sampleRate();
                int bin = Math.max(0, Math.min(magnitudes.length - 2, (int) Math.floor(binPosition)));
                double fraction = binPosition - bin;
                double db = magnitudes[bin] * (1.0 - fraction) + magnitudes[bin + 1] * fraction;
                double normalized = Math.max(0.0, Math.min(1.0, (db - floor) / (ceiling - floor)));
                image.setRGB(x, y, colour(normalized).getRGB());
            }
        }
        return image;
    }

    private static Color colour(double value) {
        float hue = (float) (0.70 - value * 0.63);
        float saturation = (float) (0.45 + value * 0.50);
        float brightness = (float) (0.08 + value * 0.90);
        return Color.getHSBColor(hue, saturation, brightness);
    }

    private static void fft(double[] real, double[] imag) {
        int n = real.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                double temp = real[i]; real[i] = real[j]; real[j] = temp;
                temp = imag[i]; imag[i] = imag[j]; imag[j] = temp;
            }
        }
        for (int length = 2; length <= n; length <<= 1) {
            double angle = -2.0 * Math.PI / length;
            double rootReal = Math.cos(angle), rootImag = Math.sin(angle);
            for (int i = 0; i < n; i += length) {
                double wr = 1.0, wi = 0.0;
                for (int j = 0; j < length / 2; j++) {
                    int even = i + j, odd = even + length / 2;
                    double tr = wr * real[odd] - wi * imag[odd];
                    double ti = wr * imag[odd] + wi * real[odd];
                    real[odd] = real[even] - tr; imag[odd] = imag[even] - ti;
                    real[even] += tr; imag[even] += ti;
                    double nextWr = wr * rootReal - wi * rootImag;
                    wi = wr * rootImag + wi * rootReal;
                    wr = nextWr;
                }
            }
        }
    }
}
