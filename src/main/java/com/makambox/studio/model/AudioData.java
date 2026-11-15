package com.makambox.studio.model;

import com.makambox.studio.i18n.I18n;

import java.nio.file.Path;

public record AudioData(String name, Path source, float sampleRate, float[] samples,
                        AudioChannelMode channelMode, int sourceChannelCount) {
    public AudioData(String name, Path source, float sampleRate, float[] samples) {
        this(name, source, sampleRate, samples, AudioChannelMode.MIX, 1);
    }

    public AudioData(String name, Path source, float sampleRate, float[] samples,
                     AudioChannelMode channelMode) {
        this(name, source, sampleRate, samples, channelMode, 1);
    }

    public AudioData {
        channelMode = channelMode == null ? AudioChannelMode.MIX : channelMode;
        sourceChannelCount = Math.max(1, sourceChannelCount);
    }

    public double durationSeconds() { return samples.length / (double) sampleRate; }

    public String channelDescription() {
        return sourceChannelCount == 1
                ? I18n.get().textOrDefault("audio.channel.mono", "Tek kanal")
                : channelMode.toString();
    }
}
