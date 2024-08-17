package net.transgressoft.commons.music.waveform

import net.transgressoft.commons.ReactiveEntity
import java.awt.Color
import java.io.File
import java.nio.file.Path

/**
 * @author Octavio Calleya
 */
interface AudioWaveform : ReactiveEntity<Int, AudioWaveform> {

    val audioFilePath: Path

    @Throws(AudioWaveformProcessingException::class)
    suspend fun amplitudes(width: Int, height: Int): FloatArray

    suspend fun createImage(outputFile: File, waveformColor: Color, backgroundColor: Color, width: Int, height: Int)
}