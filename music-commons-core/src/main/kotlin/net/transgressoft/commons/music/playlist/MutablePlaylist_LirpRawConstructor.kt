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

import net.transgressoft.lirp.persistence.LirpRawConstructor

/**
 * Co-located construction SPI for [MutablePlaylist].
 *
 * Resolved at runtime by `SqlRepository.loadFromStore` (via a `RawConstructibleTableDef`) through
 * `Class.forName` on the entity's binary name plus the `_LirpRawConstructor` suffix. Living in the
 * entity's own module, it reaches the `internal` constructor a persistence module cannot call. Only
 * the identity/scalar constructor parameters are supplied here; the aggregate collections default to
 * empty and are populated afterward through [MutablePlaylist_LirpRefAccessor], and the scalar
 * reactive fields are re-applied by [MutablePlaylist_LirpRawInitializer].
 *
 * The [construct] `params` map is keyed by constructor parameter name and produced by the
 * persistence-side `RawConstructibleTableDef.constructorParams`. Expected keys: `id` ([Int]),
 * `name` ([String]), `isDirectory` ([Boolean]).
 */
@Suppress("ClassName")
internal class MutablePlaylist_LirpRawConstructor : LirpRawConstructor<MutablePlaylist> {
    override fun construct(params: Map<String, Any?>): MutablePlaylist =
        MutablePlaylist(
            id = params["id"] as Int,
            name = params["name"] as String,
            isDirectory = params["isDirectory"] as Boolean
        )
}