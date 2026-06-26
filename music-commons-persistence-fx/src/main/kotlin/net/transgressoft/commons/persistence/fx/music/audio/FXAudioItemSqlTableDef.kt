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

package net.transgressoft.commons.persistence.fx.music.audio

import net.transgressoft.commons.fx.music.audio.ObservableAudioItem
import net.transgressoft.commons.music.audio.AlbumDetails
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.AudioItemMetadata
import net.transgressoft.commons.music.audio.Label
import net.transgressoft.commons.persistence.music.audio.CountryConverter
import net.transgressoft.commons.persistence.music.audio.GenreConverter
import net.transgressoft.lirp.persistence.ColumnDef
import net.transgressoft.lirp.persistence.ColumnType
import net.transgressoft.lirp.persistence.DurationColumnConverter
import net.transgressoft.lirp.persistence.LirpRawInitializer
import net.transgressoft.lirp.persistence.PathColumnConverter
import net.transgressoft.lirp.persistence.sql.RawConstructibleTableDef
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.serialization.json.Json

/**
 * Construction-free [RawConstructibleTableDef] mapping the JavaFX audio item to a SQLite table.
 *
 * The JavaFX audio item's constructor is `internal` to the reactive FX module, so this mapping never
 * constructs the entity itself: [entityClassName] points lirp's SQL loader at the entity's co-located
 * `_LirpRawConstructor`, which it resolves reflectively and feeds with [constructorParams]. [toParams]
 * writes the row using the shared `ColumnConverter`s, and [applyScalarRow] populates the reactive and
 * non-constructor fields after construction via the entity's `LirpRawInitializer` silent-setter path.
 * There is no `fromRow` override — construction is delegated entirely to lirp.
 *
 * The column shape mirrors the core-tier audio-item table so the two tiers stay interchangeable for
 * consumers; the `date_of_creation` and `play_count` columns back the constructor parameters of the
 * same name.
 */
object FXAudioItemSqlTableDef : RawConstructibleTableDef<ObservableAudioItem> {

    override val tableName: String = "fx_audio_item"

    override val entityClassName: String = "net.transgressoft.commons.fx.music.audio.FXAudioItem"

    override val columns: List<ColumnDef> =
        listOf(
            ColumnDef(name = "path", type = PathColumnConverter.sqlType, nullable = false, primaryKey = false, isVersion = false),
            ColumnDef(name = "id", type = ColumnType.IntType, nullable = false, primaryKey = true, isVersion = false),
            ColumnDef(name = "metadata_encoder", type = ColumnType.TextType, nullable = true, primaryKey = false, isVersion = false),
            ColumnDef(name = "metadata_encoding", type = ColumnType.TextType, nullable = true, primaryKey = false, isVersion = false),
            ColumnDef(name = "metadata_bit_rate", type = ColumnType.IntType, nullable = false, primaryKey = false, isVersion = false),
            ColumnDef(name = "metadata_duration", type = DurationColumnConverter.sqlType, nullable = false, primaryKey = false, isVersion = false),
            ColumnDef(name = "date_of_creation", type = ColumnType.DateTimeType, nullable = false, primaryKey = false, isVersion = false),
            ColumnDef(name = "last_date_modified", type = ColumnType.DateTimeType, nullable = false, primaryKey = false, isVersion = false),
            ColumnDef(name = "play_count", type = ColumnType.IntType, nullable = false, primaryKey = false, isVersion = false),
            ColumnDef(name = "title", type = ColumnType.TextType, nullable = false, primaryKey = false, isVersion = false),
            ColumnDef(name = "artist_name", type = ColumnType.TextType, nullable = false, primaryKey = false, isVersion = false),
            ColumnDef(name = "artist_country_code", type = CountryConverter.sqlType, nullable = false, primaryKey = false, isVersion = false),
            ColumnDef(name = "genres", type = ColumnType.TextType, nullable = false, primaryKey = false, isVersion = false, defaultExpression = "[]"),
            ColumnDef(name = "comments", type = ColumnType.TextType, nullable = true, primaryKey = false, isVersion = false),
            ColumnDef(name = "track_number", type = ColumnType.IntType, nullable = true, primaryKey = false, isVersion = false),
            ColumnDef(name = "disc_number", type = ColumnType.IntType, nullable = true, primaryKey = false, isVersion = false),
            ColumnDef(name = "bpm", type = ColumnType.FloatType, nullable = true, primaryKey = false, isVersion = false),
            ColumnDef(name = "album_name", type = ColumnType.TextType, nullable = false, primaryKey = false, isVersion = false),
            ColumnDef(name = "album_album_artist_name", type = ColumnType.TextType, nullable = false, primaryKey = false, isVersion = false),
            ColumnDef(name = "album_album_artist_country_code", type = CountryConverter.sqlType, nullable = false, primaryKey = false, isVersion = false),
            ColumnDef(name = "album_is_compilation", type = ColumnType.BooleanType, nullable = false, primaryKey = false, isVersion = false),
            ColumnDef(name = "album_year", type = ColumnType.IntType, nullable = true, primaryKey = false, isVersion = false),
            ColumnDef(name = "album_label_name", type = ColumnType.TextType, nullable = false, primaryKey = false, isVersion = false),
            ColumnDef(name = "album_label_country_code", type = CountryConverter.sqlType, nullable = false, primaryKey = false, isVersion = false)
        )

    override fun constructorParams(row: ResultRow, table: Table): Map<String, Any?> {
        val column = column(table)
        return mapOf(
            "path" to PathColumnConverter.fromSql(row[column("path")] as String),
            "id" to row[column("id")] as Int,
            "metadata" to
                AudioItemMetadata(
                    encoder = row[column("metadata_encoder")] as? String,
                    encoding = row[column("metadata_encoding")] as? String,
                    bitRate = row[column("metadata_bit_rate")] as Int,
                    duration = DurationColumnConverter.fromSql(row[column("metadata_duration")] as Long),
                    coverBytes = null
                ),
            "dateOfCreation" to (row[column("date_of_creation")] as kotlinx.datetime.LocalDateTime).toJavaLocalDateTime(),
            "lastDateModified" to (row[column("last_date_modified")] as kotlinx.datetime.LocalDateTime).toJavaLocalDateTime(),
            "playCount" to (row[column("play_count")] as Number).toShort()
        )
    }

    override fun toParams(entity: ObservableAudioItem, table: Table): Map<Column<*>, Any?> {
        val cols = table.columns.associateBy { it.name }
        return mapOf(
            cols.getValue("path") to PathColumnConverter.toSql(entity.path),
            cols.getValue("id") to entity.id,
            cols.getValue("metadata_encoder") to entity.encoder,
            cols.getValue("metadata_encoding") to entity.encoding,
            cols.getValue("metadata_bit_rate") to entity.bitRate,
            cols.getValue("metadata_duration") to DurationColumnConverter.toSql(entity.duration),
            cols.getValue("date_of_creation") to entity.dateOfCreation.toKotlinLocalDateTime(),
            cols.getValue("last_date_modified") to entity.lastDateModified.toKotlinLocalDateTime(),
            cols.getValue("play_count") to entity.playCount.toInt(),
            cols.getValue("title") to entity.title,
            cols.getValue("artist_name") to entity.artist.name,
            cols.getValue("artist_country_code") to CountryConverter.toSql(entity.artist.countryCode),
            cols.getValue("genres") to Json.encodeToString(entity.genres.map { GenreConverter.toSql(it) }),
            cols.getValue("comments") to entity.comments,
            cols.getValue("track_number") to entity.trackNumber?.toInt(),
            cols.getValue("disc_number") to entity.discNumber?.toInt(),
            cols.getValue("bpm") to entity.bpm,
            cols.getValue("album_name") to entity.album.name,
            cols.getValue("album_album_artist_name") to entity.album.albumArtist.name,
            cols.getValue("album_album_artist_country_code") to CountryConverter.toSql(entity.album.albumArtist.countryCode),
            cols.getValue("album_is_compilation") to entity.album.isCompilation,
            cols.getValue("album_year") to entity.album.year?.toInt(),
            cols.getValue("album_label_name") to entity.album.label.name,
            cols.getValue("album_label_country_code") to CountryConverter.toSql(entity.album.label.countryCode)
        )
    }

    override fun applyRow(entity: ObservableAudioItem, row: ResultRow, table: Table) {
        val column = column(table)
        entity.title = row[column("title")] as String
        entity.genres =
            Json.decodeFromString<List<String>>(row[column("genres")] as String)
                .map { GenreConverter.fromSql(it) }.toSet()
        entity.comments = row[column("comments")] as? String
        entity.trackNumber = (row[column("track_number")] as? Number)?.toShort()
        entity.discNumber = (row[column("disc_number")] as? Number)?.toShort()
        entity.bpm = row[column("bpm")] as? Float
        entity.artist =
            Artist.of(
                name = row[column("artist_name")] as String,
                countryCode = CountryConverter.fromSql(row[column("artist_country_code")] as String)
            )
        entity.album =
            AlbumDetails(
                name = row[column("album_name")] as String,
                albumArtist =
                    Artist.of(
                        name = row[column("album_album_artist_name")] as String,
                        countryCode = CountryConverter.fromSql(row[column("album_album_artist_country_code")] as String)
                    ),
                isCompilation = row[column("album_is_compilation")] as Boolean,
                year = (row[column("album_year")] as? Number)?.toShort(),
                label =
                    Label.of(
                        name = row[column("album_label_name")] as String,
                        countryCode = CountryConverter.fromSql(row[column("album_label_country_code")] as String)
                    )
            )
    }

    override fun applyScalarRow(
        entity: ObservableAudioItem,
        row: ResultRow,
        table: Table,
        rawInit: LirpRawInitializer<ObservableAudioItem>
    ) {
        val column = column(table)
        for (entry in rawInit.entries) {
            val value: Any? =
                when (entry.name) {
                    "metadata" ->
                        AudioItemMetadata(
                            encoder = row[column("metadata_encoder")] as? String,
                            encoding = row[column("metadata_encoding")] as? String,
                            bitRate = row[column("metadata_bit_rate")] as Int,
                            duration = DurationColumnConverter.fromSql(row[column("metadata_duration")] as Long),
                            coverBytes = null
                        )
                    "title" -> row[column("title")] as String
                    "comments" -> row[column("comments")] as? String
                    "trackNumber" -> (row[column("track_number")] as? Number)?.toShort()
                    "discNumber" -> (row[column("disc_number")] as? Number)?.toShort()
                    "bpm" -> row[column("bpm")] as? Float
                    "lastDateModified" -> (row[column("last_date_modified")] as kotlinx.datetime.LocalDateTime).toJavaLocalDateTime()
                    "artist" ->
                        Artist.of(
                            name = row[column("artist_name")] as String,
                            countryCode = CountryConverter.fromSql(row[column("artist_country_code")] as String)
                        )
                    "genres" ->
                        Json.decodeFromString<List<String>>(row[column("genres")] as String)
                            .map { GenreConverter.fromSql(it) }.toSet()
                    "album" ->
                        AlbumDetails(
                            name = row[column("album_name")] as String,
                            albumArtist =
                                Artist.of(
                                    name = row[column("album_album_artist_name")] as String,
                                    countryCode = CountryConverter.fromSql(row[column("album_album_artist_country_code")] as String)
                                ),
                            isCompilation = row[column("album_is_compilation")] as Boolean,
                            year = (row[column("album_year")] as? Number)?.toShort(),
                            label =
                                Label.of(
                                    name = row[column("album_label_name")] as String,
                                    countryCode = CountryConverter.fromSql(row[column("album_label_country_code")] as String)
                                )
                        )
                    else -> continue
                }
            entry.silentSetter(entity, value)
        }
    }

    private fun column(table: Table): (String) -> Column<*> {
        val byName = table.columns.associateBy { it.name }
        return { name -> byName.getValue(name) }
    }
}