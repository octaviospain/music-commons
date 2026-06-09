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

package net.transgressoft.commons.fx.music.playlist

import net.transgressoft.lirp.persistence.ColumnDef
import net.transgressoft.lirp.persistence.ColumnType
import net.transgressoft.lirp.persistence.LirpTableDef

/**
 * SQL table descriptor for [FXPlaylist].
 *
 * Written manually because KSP cannot generate a TableDef for [FXPlaylist] via
 * `@PersistenceMapping` annotation processing: [FXPlaylist] is an `internal class` in
 * the `music-commons-fx` module, and its aggregate properties (`FxAggregateList`,
 * `FxAggregateSet`) reference types from the same compilation unit, causing KSP's
 * `validate()` to return false across all processing rounds. The manual [FXPlaylist_LirpRefAccessor]
 * companion mirrors the same approach already used for aggregate wiring.
 *
 * Column set mirrors [net.transgressoft.commons.music.playlist.MutablePlaylist_LirpTableDef]:
 * only `last_date_modified` is a mapped scalar column; `name`, `is_directory`, and the aggregate
 * collections are handled at the application level via the FX accessor infrastructure.
 */
@Suppress("ktlint:standard:class-naming")
internal object FXPlaylist_LirpTableDef : LirpTableDef<FXPlaylist> {
    override val tableName: String = "fx_playlist"
    override val columns: List<ColumnDef> =
        listOf(
            ColumnDef(name = "last_date_modified", type = ColumnType.DateTimeType, nullable = false, primaryKey = false, isVersion = false)
        )
}