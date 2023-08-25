package net.transgressoft.commons.music.waveform

import javafx.scene.paint.Color
import net.transgressoft.commons.IdentifiableEntity
import java.io.File

/**
 * @author Octavio Calleya
 */
interface AudioWaveform : IdentifiableEntity<Int> {

    suspend fun amplitudes(width: Int, height: Int): FloatArray

    suspend fun createImage(outputFile: File, waveformColor: Color, backgroundColor: Color, width: Int, height: Int)
}