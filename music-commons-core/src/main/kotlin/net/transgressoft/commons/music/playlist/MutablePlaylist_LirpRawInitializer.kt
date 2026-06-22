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

package net.transgressoft.commons.music.playlist

import net.transgressoft.lirp.persistence.LirpRawInitializer
import net.transgressoft.lirp.persistence.RawInitEntry
import net.transgressoft.lirp.persistence.writeReactivePropertyBackingField

/**
 * Co-located population SPI for [MutablePlaylist].
 *
 * Resolved at runtime by lirp via `Class.forName` on the entity's binary name plus the
 * `_LirpRawInitializer` suffix. Writes the scalar reactive fields (`name`, `isDirectory`) and the
 * `lastDateModified` timestamp into an already-constructed instance without firing reactive events
 * during SQL bulk-load. The `id` identity field is supplied by [MutablePlaylist_LirpRawConstructor];
 * the aggregate collections are wired separately by [MutablePlaylist_LirpRefAccessor].
 */
@Suppress("UNCHECKED_CAST", "ClassName")
internal class MutablePlaylist_LirpRawInitializer : LirpRawInitializer<MutablePlaylist> {
    override val entries: List<RawInitEntry<MutablePlaylist>> =
        listOf(
            RawInitEntry(
                name = "isDirectory",
                silentSetter = { entity, value ->
                    writeReactivePropertyBackingField<Any?>(entity, "isDirectory", value)
                }
            ),
            RawInitEntry(
                name = "name",
                silentSetter = { entity, value ->
                    writeReactivePropertyBackingField<Any?>(entity, "name", value)
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