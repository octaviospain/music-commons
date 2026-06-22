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

package net.transgressoft.commons.persistence.fx.music.playlist

import net.transgressoft.commons.fx.music.playlist.ObservablePlaylist
import net.transgressoft.lirp.persistence.ColumnDef
import net.transgressoft.lirp.persistence.ColumnType
import net.transgressoft.lirp.persistence.LirpTableDef

/**
 * SQL table descriptor for the JavaFX playlist entity.
 *
 * Stays a bare [LirpTableDef] column descriptor rather than a `RawConstructibleTableDef`: only
 * `last_date_modified` is a mapped scalar column. The playlist's identity (`id`), `name`,
 * `is_directory` flag, and the aggregate `audioItems`/`playlists` collections are resolved at the
 * application level through the entity's co-located reference accessor and lirp's junction-row
 * wiring, so this table carries no construction parameters and needs no in-base raw constructor here.
 */
object ObservablePlaylistSqlTableDef : LirpTableDef<ObservablePlaylist> {
    override val tableName: String = "fx_playlist"
    override val columns: List<ColumnDef> =
        listOf(
            ColumnDef(name = "last_date_modified", type = ColumnType.DateTimeType, nullable = false, primaryKey = false, isVersion = false)
        )
}