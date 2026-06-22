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

package net.transgressoft.commons.music.audio

import net.transgressoft.lirp.persistence.LirpRawInitializer
import net.transgressoft.lirp.persistence.RawInitEntry
import net.transgressoft.lirp.persistence.writeReactivePropertyBackingField

/**
 * Co-located population SPI for [MutableAudioItem].
 *
 * Resolved at runtime by lirp via `Class.forName` on the entity's binary name plus the
 * `_LirpRawInitializer` suffix. Writes per-row values into the entity's reactive and non-constructor
 * backing fields without firing reactive events during SQL bulk-load; the constructor-supplied
 * identity fields (`path`, `id`) are not listed here — they are set by the entity's
 * [MutableAudioItem_LirpRawConstructor]. JSON deserialization restores values through the reflective
 * serializer instead and does not consult this initializer.
 */
@Suppress("UNCHECKED_CAST", "ClassName")
internal class MutableAudioItem_LirpRawInitializer : LirpRawInitializer<MutableAudioItem> {
    override val entries: List<RawInitEntry<MutableAudioItem>> =
        listOf(
            RawInitEntry(
                name = "metadata",
                silentSetter = { entity, value ->
                    writeReactivePropertyBackingField<Any?>(entity, "metadata", value)
                }
            ),
            RawInitEntry(
                name = "title",
                silentSetter = { entity, value ->
                    writeReactivePropertyBackingField<Any?>(entity, "title", value)
                }
            ),
            RawInitEntry(
                name = "artist",
                silentSetter = { entity, value ->
                    writeReactivePropertyBackingField<Any?>(entity, "artist", value)
                }
            ),
            RawInitEntry(
                name = "genres",
                silentSetter = { entity, value ->
                    writeReactivePropertyBackingField<Any?>(entity, "genres", value)
                }
            ),
            RawInitEntry(
                name = "comments",
                silentSetter = { entity, value ->
                    writeReactivePropertyBackingField<Any?>(entity, "comments", value)
                }
            ),
            RawInitEntry(
                name = "trackNumber",
                silentSetter = { entity, value ->
                    writeReactivePropertyBackingField<Any?>(entity, "trackNumber", value)
                }
            ),
            RawInitEntry(
                name = "discNumber",
                silentSetter = { entity, value ->
                    writeReactivePropertyBackingField<Any?>(entity, "discNumber", value)
                }
            ),
            RawInitEntry(
                name = "bpm",
                silentSetter = { entity, value ->
                    writeReactivePropertyBackingField<Any?>(entity, "bpm", value)
                }
            ),
            RawInitEntry(
                name = "album",
                silentSetter = { entity, value ->
                    writeReactivePropertyBackingField<Any?>(entity, "album", value)
                }
            ),
            RawInitEntry(
                name = "lastDateModified",
                silentSetter = { entity, value ->
                    entity.lastDateModified = value as java.time.LocalDateTime
                }
            )
        )
}