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
import net.transgressoft.lirp.persistence.AggregateCollectionRef
import net.transgressoft.lirp.persistence.ColumnDef
import net.transgressoft.lirp.persistence.ColumnType
import net.transgressoft.lirp.persistence.LirpRawInitializer
import net.transgressoft.lirp.persistence.sql.RawConstructibleTableDef
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime

/**
 * Construction-free [RawConstructibleTableDef] mapping the JavaFX playlist entity to a SQLite table.
 *
 * Mirrors the core-tier `AudioPlaylistSqlTableDef` so the two tiers stay interchangeable for
 * consumers. The JavaFX playlist's constructor is `internal` to the reactive FX module, so this
 * mapping never constructs the entity itself: [entityClassName] points lirp's SQL loader at the
 * entity's co-located `_LirpRawConstructor`, which it resolves reflectively and feeds with
 * [constructorParams]. The scalar reactive fields (`name`, `isDirectory`, `lastDateModified`) are
 * re-applied after construction via the entity's `LirpRawInitializer` silent-setter path in
 * [applyScalarRow]. There is no `fromRow` override — construction is delegated entirely to lirp.
 *
 * The playlist's aggregate audio-item and child-playlist references are stored as comma-separated
 * TEXT columns (`audio_item_ids`, `playlist_ids`). They are restored at construction time rather than
 * afterward, because the `fxAggregateList`/`fxAggregateSet` delegates capture their backing IDs from
 * the constructor arguments — so the parsed IDs are passed through [constructorParams] as the
 * `initialAudioItemIds`/`initialPlaylistIds` keys the entity's `_LirpRawConstructor` expects.
 *
 * Column order and the `fx_playlist` table name are stable.
 */
object ObservablePlaylistSqlTableDef : RawConstructibleTableDef<ObservablePlaylist> {

    override val tableName: String = "fx_playlist"

    override val entityClassName: String = "net.transgressoft.commons.fx.music.playlist.FXPlaylist"

    override val columns: List<ColumnDef> =
        listOf(
            ColumnDef(name = "id", type = ColumnType.IntType, nullable = false, primaryKey = true, isVersion = false),
            ColumnDef(name = "name", type = ColumnType.TextType, nullable = false, primaryKey = false, isVersion = false),
            ColumnDef(name = "is_directory", type = ColumnType.BooleanType, nullable = false, primaryKey = false, isVersion = false),
            ColumnDef(name = "last_date_modified", type = ColumnType.DateTimeType, nullable = false, primaryKey = false, isVersion = false),
            ColumnDef(name = "audio_item_ids", type = ColumnType.TextType, nullable = false, primaryKey = false, isVersion = false, defaultExpression = ""),
            ColumnDef(name = "playlist_ids", type = ColumnType.TextType, nullable = false, primaryKey = false, isVersion = false, defaultExpression = "")
        )

    override fun constructorParams(row: ResultRow, table: Table): Map<String, Any?> {
        val column = column(table)
        return mapOf(
            "id" to row[column("id")] as Int,
            "name" to row[column("name")] as String,
            "isDirectory" to row[column("is_directory")] as Boolean,
            "initialAudioItemIds" to parseIds(row[column("audio_item_ids")] as String),
            "initialPlaylistIds" to parseIds(row[column("playlist_ids")] as String).toSet()
        )
    }

    override fun toParams(entity: ObservablePlaylist, table: Table): Map<Column<*>, Any?> {
        val cols = table.columns.associateBy { it.name }
        return mapOf(
            cols.getValue("id") to entity.id,
            cols.getValue("name") to entity.name,
            cols.getValue("is_directory") to entity.isDirectory,
            cols.getValue("last_date_modified") to entity.lastDateModified.toKotlinLocalDateTime(),
            cols.getValue("audio_item_ids") to referenceIdsOf(entity.audioItems).joinToString(","),
            cols.getValue("playlist_ids") to referenceIdsOf(entity.playlists).joinToString(",")
        )
    }

    override fun applyRow(entity: ObservablePlaylist, row: ResultRow, table: Table) {
        val column = column(table)
        entity.name = row[column("name")] as String
        entity.isDirectory = row[column("is_directory")] as Boolean
        // audio_item_ids/playlist_ids back the `audioItems`/`playlists` aggregate delegates,
        // captured once at construction time, so their collection state is not reconciled here. This
        // method is only reached on the optimistic-lock reload path, which this entity never triggers
        // (it declares no version column).
    }

    override fun applyScalarRow(
        entity: ObservablePlaylist,
        row: ResultRow,
        table: Table,
        rawInit: LirpRawInitializer<ObservablePlaylist>
    ) {
        val column = column(table)
        for (entry in rawInit.entries) {
            val value: Any? =
                when (entry.name) {
                    "name" -> row[column("name")] as String
                    "isDirectory" -> row[column("is_directory")] as Boolean
                    "lastDateModified" -> (row[column("last_date_modified")] as kotlinx.datetime.LocalDateTime).toJavaLocalDateTime()
                    else -> continue
                }
            entry.silentSetter(entity, value)
        }
    }

    private fun referenceIdsOf(collection: Collection<*>): Collection<*> =
        (collection as AggregateCollectionRef<*, *>).referenceIds

    private fun parseIds(text: String): List<Int> =
        if (text.isBlank()) {
            emptyList()
        } else {
            text.split(",").map { token ->
                token.trim().toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid aggregate id '$token' in persisted playlist reference column '$text'")
            }
        }

    private fun column(table: Table): (String) -> Column<*> {
        val byName = table.columns.associateBy { it.name }
        return { name -> byName.getValue(name) }
    }
}