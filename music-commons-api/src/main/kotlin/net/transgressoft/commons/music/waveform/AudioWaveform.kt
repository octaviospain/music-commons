/******************************************************************************
 * Copyright (C) 2025  Octavio Calleya Garcia                                 *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 * (at your option) any later version.                                        *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.     *
 ******************************************************************************/

package net.transgressoft.commons.music.waveform

import net.transgressoft.commons.entity.ReactiveEntity
import java.awt.Color
import java.io.File
import java.nio.file.Path
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Represents an audio waveform with capabilities to generate amplitude data and visual representations.
 */
interface AudioWaveform : ReactiveEntity<Int, AudioWaveform> {

    val audioFilePath: Path

    /**
     * Calculates the waveform amplitudes for the specified dimensions.
     *
     * @throws AudioWaveformProcessingException if an error occurs during amplitude calculation
     */
    @Throws(AudioWaveformProcessingException::class)
    suspend fun amplitudes(width: Int, height: Int): FloatArray

    /**
     * Creates a visual image of the waveform with the specified colors and dimensions.
     */
    suspend fun createImage(
        outputFile: File,
        waveformColor: Color,
        backgroundColor: Color,
        width: Int,
        height: Int,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    )
}