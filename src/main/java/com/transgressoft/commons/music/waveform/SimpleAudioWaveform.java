package com.transgressoft.commons.music.waveform;

import com.google.common.base.Objects;

/**
 * @author Octavio Calleya
 */
public class SimpleAudioWaveform implements AudioWaveform {

    private final float[] amplitudes;
    private final int width;
    private final int height;

    public SimpleAudioWaveform(float[] amplitudes, int width, int height) {
        this.amplitudes = amplitudes;
        this.width = width;
        this.height = height;
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public float[] amplitudes() {
        return amplitudes;
    }

    @Override
    public SimpleAudioWaveform scale(int width, int height) {
        throw new UnsupportedOperationException("Not implemented");
        // TODO Do some math and figure out how to scale the amplitudes given the new width and height without processing again
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SimpleAudioWaveform that = (SimpleAudioWaveform) o;
        return width == that.width &&
                height == that.height &&
                Objects.equal(amplitudes, that.amplitudes);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(width, height, amplitudes);
    }
}
