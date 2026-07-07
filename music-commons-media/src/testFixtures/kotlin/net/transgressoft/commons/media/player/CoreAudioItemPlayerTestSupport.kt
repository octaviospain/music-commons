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
import java.nio.file.Path
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.SourceDataLine

/**
 * Constructs a [CoreAudioItemPlayer] wired with test-friendly defaults.
 *
 * The frozen `nanoTime` clock and a stall threshold disabled via [Long.MAX_VALUE] make
 * playback state-machine tests deterministic without real audio hardware. The caller
 * supplies the PCM stream source and line factory to control what the pump reads and
 * where it writes.
 *
 * @param pcmStreamFactory produces the decoded PCM stream for a given audio file path;
 *   defaults to the real [decodeToPcmStream] decoder for tests that exercise actual fixtures.
 * @param lineFactory produces the audio output line; defaults to [FakeAudioLine].
 * @return a fully-wired [CoreAudioItemPlayer] ready for test-driven playback control.
 */
fun testPlayer(
    pcmStreamFactory: (Path) -> AudioInputStream = ::decodeToPcmStream,
    lineFactory: (AudioFormat) -> SourceDataLine = { FakeAudioLine() }
): CoreAudioItemPlayer =
    CoreAudioItemPlayer(
        pcmStreamFactory = pcmStreamFactory,
        lineFactory = lineFactory,
        nanoTime = { 0L },
        stallThresholdNanos = Long.MAX_VALUE
    )