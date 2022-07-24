package net.transgressoft.commons.music.waveform;

import net.transgressoft.commons.query.QueryEntity;

/**
 * @author Octavio Calleya
 */
public interface AudioWaveform extends QueryEntity {

    int width();

    int height();

    float[] amplitudes();

    AudioWaveform scale(int width, int height);
}
