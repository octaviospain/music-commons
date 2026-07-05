/******************************************************************************
 * Copyright (C) 2026  Octavio Calleya Garcia                                 *
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

package net.transgressoft.commons.media.player

import net.transgressoft.commons.media.util.decodeToPcmStream
import net.transgressoft.commons.music.audio.AudioFileTagType
import net.transgressoft.commons.music.audio.AudioFileTagType.FLAC
import net.transgressoft.commons.music.audio.AudioFileTagType.ID3_V_24
import net.transgressoft.commons.music.audio.AudioFileTagType.MP4_INFO
import net.transgressoft.commons.music.audio.AudioFileTagType.VORBIS_COMMENT
import net.transgressoft.commons.music.audio.AudioFileTagType.WAV
import io.mockk.every
import io.mockk.mockk
import java.nio.file.Path
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.SourceDataLine

/**
 * The five audio formats the media pipeline supports end-to-end, keyed by the display name used
 * in data-driven test tables and mapped to the tag type that selects the matching real fixture.
 */
val SUPPORTED_FORMATS: Map<String, AudioFileTagType> =
    mapOf(
        "mp3" to ID3_V_24,
        "m4a" to MP4_INFO,
        "wav" to WAV,
        "flac" to FLAC,
        "ogg" to VORBIS_COMMENT
    )

/**
 * A relaxed mock [SourceDataLine] that reports itself open and echoes back the byte count handed
 * to `write`, so the pump advances without real audio hardware.
 */
fun fakeLine(): SourceDataLine =
    mockk(relaxed = true) {
        every { isOpen } returns true
        every { write(any(), any(), any()) } answers { thirdArg() }
    }

/**
 * Builds a [CoreAudioItemPlayer] wired with the hardware-free test defaults: a frozen `nanoTime`
 * clock and a stall threshold disabled via [Long.MAX_VALUE]. The PCM stream source and line
 * factory vary per test.
 *
 * @param pcmStreamFactory produces the decoded PCM stream for a given audio file path; defaults
 *   to the real [decodeToPcmStream] decoder for tests that exercise actual fixtures.
 * @param lineFactory produces the audio output line; defaults to a [fakeLine] mock.
 */
fun testPlayer(
    pcmStreamFactory: (Path) -> AudioInputStream = ::decodeToPcmStream,
    lineFactory: (AudioFormat) -> SourceDataLine = { fakeLine() }
): CoreAudioItemPlayer =
    CoreAudioItemPlayer(
        pcmStreamFactory = pcmStreamFactory,
        lineFactory = lineFactory,
        nanoTime = { 0L },
        stallThresholdNanos = Long.MAX_VALUE
    )