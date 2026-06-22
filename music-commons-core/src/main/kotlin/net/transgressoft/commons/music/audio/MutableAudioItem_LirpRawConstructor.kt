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

import net.transgressoft.lirp.persistence.LirpRawConstructor
import java.nio.file.Path
import java.time.LocalDateTime

/**
 * Co-located construction SPI for [MutableAudioItem].
 *
 * Resolved at runtime by `SqlRepository.loadFromStore` (via a `RawConstructibleTableDef`) through
 * `Class.forName` on the entity's binary name plus the `_LirpRawConstructor` suffix. Living in the
 * entity's own module, it reaches the `internal` deserialization constructor that a persistence
 * module cannot call directly. The remaining reactive/non-constructor fields are populated afterward
 * by [MutableAudioItem_LirpRawInitializer].
 *
 * The [construct] `params` map is keyed by constructor parameter name and produced by the
 * persistence-side `RawConstructibleTableDef.constructorParams`. Expected keys:
 * `path` ([Path]), `id` ([Int]), `metadata` ([AudioItemMetadata]), `dateOfCreation`
 * ([LocalDateTime]), `lastDateModified` ([LocalDateTime]), `playCount` ([Short]).
 */
@Suppress("ClassName")
internal class MutableAudioItem_LirpRawConstructor : LirpRawConstructor<MutableAudioItem> {
    override fun construct(params: Map<String, Any?>): MutableAudioItem =
        MutableAudioItem(
            params["path"] as Path,
            params["id"] as Int,
            params["metadata"] as AudioItemMetadata,
            params["dateOfCreation"] as LocalDateTime,
            params["lastDateModified"] as LocalDateTime,
            params["playCount"] as Short
        )
}