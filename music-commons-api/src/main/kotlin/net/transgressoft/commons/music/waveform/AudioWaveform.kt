package net.transgressoft.commons.music.waveform

import net.transgressoft.commons.query.QueryEntity

/**
 * @author Octavio Calleya
 */
interface AudioWaveform : QueryEntity {
    val width: Int
    val height: Int
    val amplitudes: FloatArray
    fun scale(width: Int, height: Int): AudioWaveform
}