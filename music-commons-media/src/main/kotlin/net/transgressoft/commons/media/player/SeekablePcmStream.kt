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

import javax.sound.sampled.AudioInputStream

/**
 * Decoded PCM stream and the absolute decoded byte offset at which decoding begins.
 *
 * Returned by [PcmStreamSeeker.open] implementations. [startByteOffset] is aligned
 * to the PCM frame size by the seeker so [CoreAudioItemPlayer] can set its frame position correctly.
 *
 * @property stream the decoded PCM audio input stream positioned at [startByteOffset]
 * @property startByteOffset the absolute decoded-byte position at which decoding begins, aligned to the PCM frame size
 */
internal data class SeekablePcmStream(
    val stream: AudioInputStream,
    val startByteOffset: Long
)