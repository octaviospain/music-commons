package net.transgressoft.commons.music.waveform

import net.transgressoft.commons.query.QueryEntity
import java.awt.Color
import java.io.File

/**
 * @author Octavio Calleya
 */
interface AudioWaveform : QueryEntity {

    fun amplitudes(width: Int, height: Int): FloatArray

    fun createImage(outputFile: File, waveformColor: Color, backgroundColor: Color, width: Int, height: Int)
}