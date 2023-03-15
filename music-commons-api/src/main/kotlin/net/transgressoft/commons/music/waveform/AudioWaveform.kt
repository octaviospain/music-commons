package net.transgressoft.commons.music.waveform

import javafx.scene.paint.Color
import net.transgressoft.commons.query.QueryEntity
import java.io.File

/**
 * @author Octavio Calleya
 */
interface AudioWaveform : QueryEntity {

    suspend fun amplitudes(width: Int, height: Int): FloatArray

    suspend fun createImage(outputFile: File, waveformColor: Color, backgroundColor: Color, width: Int, height: Int)
}