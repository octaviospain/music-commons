@file:Suppress("ktlint:standard:filename")

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

package net.transgressoft.commons.media.waveform

import net.transgressoft.lirp.persistence.LirpRawInitializer
import net.transgressoft.lirp.persistence.RawInitEntry

/**
 * Co-located population SPI for [ScalableAudioWaveform].
 *
 * Resolved at runtime by lirp via `Class.forName` on the entity's binary name plus the
 * `_LirpRawInitializer` suffix. Restores the `lastDateModified` timestamp into an already-constructed
 * instance without firing reactive events.
 *
 * Construction of the cache-bearing instance is handled by the co-located
 * `ScalableAudioWaveform_LirpRawConstructor`; this initializer only restores the reactive
 * timestamp afterward. Waveform persistence is JSON-only, so no SQL bulk-load path is involved.
 */
@Suppress("UNCHECKED_CAST", "ClassName")
internal class ScalableAudioWaveform_LirpRawInitializer : LirpRawInitializer<ScalableAudioWaveform> {
    override val entries: List<RawInitEntry<ScalableAudioWaveform>> =
        listOf(
            RawInitEntry(
                name = "lastDateModified",
                silentSetter = { entity, value ->
                    entity.lastDateModified = value as java.time.LocalDateTime
                }
            )
        )
}