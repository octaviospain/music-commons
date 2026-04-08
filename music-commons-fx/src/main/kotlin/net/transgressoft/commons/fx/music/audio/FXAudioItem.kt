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

package net.transgressoft.commons.fx.music.audio

import net.transgressoft.commons.music.AudioUtils.audioItemTrackDiscNumberComparator
import net.transgressoft.commons.music.AudioUtils.getArtistsNamesInvolved
import net.transgressoft.commons.music.audio.Album
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.AudioItemMetadataUtils.readMetadata
import net.transgressoft.commons.music.audio.AudioItemMetadataUtils.writeMetadataToFile
import net.transgressoft.commons.music.audio.Genre
import net.transgressoft.commons.music.audio.ImmutableArtist
import net.transgressoft.commons.music.audio.UNASSIGNED_ID
import net.transgressoft.lirp.entity.ReactiveEntityBase
import javafx.beans.property.ObjectProperty
import javafx.beans.property.ReadOnlyIntegerProperty
import javafx.beans.property.ReadOnlyObjectProperty
import javafx.beans.property.ReadOnlySetProperty
import javafx.beans.property.SimpleFloatProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleSetProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import javafx.collections.FXCollections
import javafx.scene.image.Image
import mu.KotlinLogging
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime
import java.util.Objects
import java.util.Optional
import kotlin.io.path.extension
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

/**
 * JavaFX implementation of [ObservableAudioItem] with bidirectional property binding.
 *
 * Provides JavaFX properties that automatically sync with the underlying metadata values.
 * Changes to JavaFX properties trigger reactive change events, while direct property
 * modifications update the corresponding JavaFX properties. Includes special handling for
 * cover images converted to JavaFX [Image] instances for UI display.
 */
@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(with = ObservableAudioItemSerializer::class)
class FXAudioItem internal constructor(override val path: Path, override val id: Int = UNASSIGNED_ID): ObservableAudioItem, Comparable<ObservableAudioItem>,
    ReactiveEntityBase<Int, ObservableAudioItem>() {

        @Transient
        private val logger = KotlinLogging.logger {}

        private val ioScope =
            CoroutineScope(
                Dispatchers.IO + SupervisorJob() +
                    CoroutineExceptionHandler { _, exception ->
                        val errorText = "Error writing metadata of $this"
                        logger.error(errorText, exception)
                    }
            )

        internal constructor(
            path: Path,
            id: Int,
            title: String,
            duration: Duration,
            bitRate: Int,
            artist: Artist,
            album: Album,
            genre: Genre,
            comments: String?,
            trackNumber: Short?,
            discNumber: Short?,
            bpm: Float?,
            encoder: String?,
            encoding: String?,
            dateOfCreation: LocalDateTime,
            lastDateModified: LocalDateTime,
            playCount: Short
        ): this(path, id) {
            disableEvents()
            this.title = title
            this._duration = duration
            this.artist = artist
            this.genre = genre
            this.comments = comments
            this.trackNumber = trackNumber
            this.discNumber = discNumber
            this.bpm = bpm
            this.album = album
            this._bitRate = bitRate
            this._encoder = encoder
            this._encoding = encoding
            this._dateOfCreation = dateOfCreation
            this.lastDateModified = lastDateModified
            _playCountProperty.set(playCount.toInt())
            enableEvents()
        }

        init {
            require(Files.exists(path)) { "File '${path.toAbsolutePath()}' does not exist" }
        }

        @Transient
        private val metadata = readMetadata(path)

        /** Immutable properties */

        private var _bitRate: Int = metadata.bitRate

        @Serializable
        override val bitRate: Int = _bitRate

        private var _duration: Duration = metadata.duration

        override val duration: Duration = _duration

        private var _encoder: String? = metadata.encoder?.takeIf { it.isNotEmpty() }

        @Serializable
        override val encoder: String? = _encoder

        private var _encoding: String? = metadata.encoding?.takeIf { it.isNotEmpty() }

        @Serializable
        override val encoding: String? = _encoding

        private var _dateOfCreation: LocalDateTime = LocalDateTime.now()

        @Serializable
        override val dateOfCreation: LocalDateTime
            get() = _dateOfCreation

        @Transient
        override val dateOfCreationProperty: ReadOnlyObjectProperty<LocalDateTime> = SimpleObjectProperty(this, "date of creation", _dateOfCreation)

        override val fileName by lazy {
            path.fileName.toString()
        }

        override val extension by lazy {
            path.extension
        }

        override val length by lazy {
            path.toFile().length()
        }

        override val uniqueId
            get() =
                buildString {
                    append(path.fileName.toString().replace(' ', '_'))
                    append("-$title")
                    append("-${duration.toSeconds()}")
                    append("-$bitRate")
                }

        /** Mutable properties */

        override var title: String = metadata.title
            set(value) {
                mutateAndPublish {
                    field = value
                    artistsInvolvedProperty.clear()
                    artistsInvolvedProperty.addAll(
                        getArtistsNamesInvolved(
                            value, artistProperty.value.name, albumProperty.value.albumArtist.name
                        ).map { ImmutableArtist.of(it) }.toSet()
                    )
                }
            }

        @Transient
        override val titleProperty: StringProperty =
            SimpleStringProperty(this, "title", title).apply {
                addListener { _, _, newTitle ->
                    title = newTitle
                }
            }

        override var artist: Artist = metadata.artist
            set(value) {
                mutateAndPublish {
                    field = value
                    artistsInvolvedProperty.clear()
                    artistsInvolvedProperty.addAll(
                        getArtistsNamesInvolved(
                            titleProperty.value, value.name, albumProperty.value.albumArtist.name
                        ).map { ImmutableArtist.of(it) }.toSet()
                    )
                }
            }

        @Transient
        override val artistProperty: ObjectProperty<Artist> =
            SimpleObjectProperty(this, "artist", artist).apply {
                addListener { _, _, newArtist ->
                    artist = newArtist
                }
            }

        override var album: Album = metadata.album
            set(value) {
                mutateAndPublish {
                    field = value
                    artistsInvolvedProperty.clear()
                    artistsInvolvedProperty.addAll(
                        getArtistsNamesInvolved(
                            titleProperty.value, artistProperty.value.name, value.albumArtist.name
                        ).map { ImmutableArtist.of(it) }.toSet()
                    )
                }
            }

        @Transient
        override val albumProperty: ObjectProperty<Album> =
            SimpleObjectProperty(this, "album", album).apply {
                addListener { _, _, newAlbum ->
                    album = newAlbum
                }
            }

        @Transient
        override val genreProperty = SimpleObjectProperty(this, "genre", metadata.genre)

        override var genre: Genre by reactiveProperty({ genreProperty.value }, { genreProperty.set(it) })

        @Transient
        override val commentsProperty = SimpleStringProperty(this, "comments", metadata.comments ?: "")

        override var comments: String? by reactiveProperty({ commentsProperty.value }, { commentsProperty.set(it) })

        @Transient
        override val trackNumberProperty = SimpleIntegerProperty(this, "track number", metadata.trackNumber?.toInt() ?: -1)

        override var trackNumber: Short? by reactiveProperty(
            { trackNumberProperty.get().toShort().takeIf { it >= 0 } },
            { trackNumberProperty.set(it?.toInt() ?: -1) }
        )

        @Transient
        override val discNumberProperty = SimpleIntegerProperty(this, "disc number", metadata.discNumber?.toInt() ?: -1)

        override var discNumber: Short? by reactiveProperty(
            { discNumberProperty.get().toShort().takeIf { it >= 0 } },
            { discNumberProperty.set(it?.toInt() ?: -1) }
        )

        @Transient
        override val bpmProperty = SimpleFloatProperty(this, "bpm", metadata.bpm ?: -1f)

        override var bpm: Float? by reactiveProperty(
            { bpmProperty.get().takeUnless { it == -1f } },
            { bpmProperty.set(it ?: -1f) }
        )

        @Serializable
        override var lastDateModified: LocalDateTime = dateOfCreation
            set(value) {
                field = value
                _lastDateModifiedProperty.set(value)
            }

        private val _lastDateModifiedProperty = SimpleObjectProperty(this, "date of last modification", lastDateModified)

        @Transient
        override val lastDateModifiedProperty: ReadOnlyObjectProperty<LocalDateTime> = _lastDateModifiedProperty

        override var coverImageBytes: ByteArray? = metadata.coverBytes
            set(value) {
                mutateAndPublish {
                    field = value
                    _coverImageProperty.set(Optional.ofNullable(value).map { bytes: ByteArray -> Image(ByteArrayInputStream(bytes)) })
                }
            }

        private val _coverImageProperty =
            SimpleObjectProperty(
                this,
                "cover image",
                Optional.ofNullable(coverImageBytes)
                    .map { bytes: ByteArray -> Image(ByteArrayInputStream(bytes)) }
            )

        @Transient
        override val coverImageProperty: ReadOnlyObjectProperty<Optional<Image>> = _coverImageProperty

        @Transient
        override val artistsInvolvedProperty: ReadOnlySetProperty<Artist> =
            SimpleSetProperty(
                this, "artists involved",
                FXCollections.observableSet(
                    getArtistsNamesInvolved(
                        titleProperty.value, artistProperty.value.name, albumProperty.value.albumArtist.name
                    ).map { ImmutableArtist.of(it) }.toMutableSet()
                )
            )

        override val artistsInvolved: Set<Artist>
            get() = artistsInvolvedProperty.value

        @Serializable
        override val playCount: Short
            get() = _playCountProperty.get().toShort()

        private val _playCountProperty = SimpleIntegerProperty(this, "play count", 0)

        @Transient
        override val playCountProperty: ReadOnlyIntegerProperty = _playCountProperty

        override fun writeMetadata(): Job =
            ioScope.launch {
                logger.debug { "Writing metadata of $this to file '${path.toAbsolutePath()}'" }
                writeMetadataToFile(
                    path, title, album, artist, genre,
                    comments, trackNumber, discNumber, bpm, encoder,
                    coverImageBytes, fileName, logger
                )
                logger.debug { "Metadata of $this successfully written to file" }
            }

        internal fun incrementPlayCount() {
            mutateAndPublish {
                _playCountProperty.set(_playCountProperty.get() + 1)
            }
        }

        override operator fun compareTo(other: ObservableAudioItem) = audioItemTrackDiscNumberComparator<ObservableAudioItem>().compare(this, other)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || javaClass != other.javaClass) return false
            val that = other as FXAudioItem
            return trackNumber == that.trackNumber &&
                discNumber == that.discNumber &&
                bpm == that.bpm &&
                path == that.path &&
                title == that.title &&
                artist == that.artist &&
                album == that.album &&
                genre === that.genre &&
                comments == that.comments &&
                duration == that.duration &&
                playCount == that.playCount &&
                coverImageBytes.contentEquals(that.coverImageBytes)
        }

        override fun hashCode() =
            Objects.hash(path, title, artist, album, genre, comments, trackNumber, discNumber, bpm, duration, playCount, coverImageBytes.contentHashCode())

        override fun clone(): FXAudioItem =
            FXAudioItem(
                path, id, title, duration, bitRate, artist, album, genre,
                comments, trackNumber, discNumber, bpm, encoder, encoding,
                dateOfCreation, lastDateModified, playCount
            )

        override fun toString() = "ObservableAudioItem(id=$id, path=$path, title=$title, artist=${artist.name})"
    }

val observableAudioItemSerializerModule =
    SerializersModule {
        polymorphic(ObservableAudioItem::class, ObservableAudioItemSerializer)
    }