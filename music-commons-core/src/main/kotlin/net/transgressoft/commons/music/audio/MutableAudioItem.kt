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

package net.transgressoft.commons.music.audio

import net.transgressoft.commons.util.WindowsPathValidator
import net.transgressoft.lirp.entity.ReactiveEntityBase
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime
import java.util.Objects
import kotlin.io.path.extension
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

const val UNASSIGNED_ID = 0

/**
 * Marker interface representing a concrete audio item implementation.
 *
 * Extends [ReactiveAudioItem] with self-referential type parameter to enable
 * type-safe operations while providing a non-generic entry point for audio item usage.
 */
interface AudioItem : ReactiveAudioItem<AudioItem> {

    override fun clone(): AudioItem
}

// Serializer is wired via `audioItemSerializerModule` polymorphic registration (see DefaultAudioLibrary).
// The serializer is a stateful class (AudioItemSerializer(FileSystem)) so it cannot be referenced as a
// bare `@Serializable(with = ...)` target — kotlinx-serialization requires KSerializer-typed ctor params.

/**
 * Mutable implementation of [AudioItem].
 *
 * Pure value holder: the primary constructor seeds reactive properties from an [AudioItemMetadata]
 * value object. File-level concerns (existence checks, tag IO) live one layer up in
 * [DefaultAudioLibrary]; this class only validates the path shape via [WindowsPathValidator] and
 * exposes reactive mutators.
 *
 * @see <a href=https://www.jthink.net/jaudiotagger/>JAudioTagger website</a>
 */
internal class MutableAudioItem
    @JvmOverloads
    constructor(
        override val path: Path,
        override val id: Int = UNASSIGNED_ID,
        metadata: AudioItemMetadata
    ) : AudioItem, ReactiveEntityBase<Int, AudioItem>() {

        // Constructor for deserialization & iTunes import
        internal constructor(
            path: Path,
            id: Int,
            metadata: AudioItemMetadata,
            dateOfCreation: LocalDateTime,
            lastDateModified: LocalDateTime,
            playCount: Short
        ) : this(path, id, metadata) {
            disableEvents()
            this._dateOfCreation = dateOfCreation
            this.lastDateModified = lastDateModified
            this._playCount = playCount
            enableEvents()
        }

        init {
            WindowsPathValidator.validatePath(path)
        }

        private var _bitRate: Int = metadata.bitRate

        @Serializable
        override val bitRate: Int
            get() = _bitRate

        private var _duration: Duration = metadata.duration

        @Serializable
        override val duration: Duration
            get() = _duration

        private var _encoder: String? = metadata.encoder?.takeIf { it.isNotEmpty() }

        @Serializable
        override val encoder: String?
            get() = _encoder

        private var _encoding: String? = metadata.encoding?.takeIf { it.isNotEmpty() }

        @Serializable
        override val encoding: String?
            get() = _encoding

        private var _dateOfCreation: LocalDateTime = LocalDateTime.now()

        @Serializable
        override val dateOfCreation: LocalDateTime
            get() = _dateOfCreation

        @Serializable
        override var lastDateModified: LocalDateTime = _dateOfCreation

        private var _playCount: Short by reactiveProperty(0.toShort())

        @Serializable
        override val playCount: Short
            get() = _playCount

        override val fileName by lazy {
            path.fileName.toString()
        }

        override val extension by lazy {
            path.extension
        }

        override val artistsInvolved
            get() = getArtistsNamesInvolved(title, artist.name, album.albumArtist.name).map { ImmutableArtist.of(it) }.toSet()

        override val length by lazy {
            Files.size(path)
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

        @Serializable
        override var title: String by reactiveProperty(metadata.title)

        @Serializable
        override var artist: Artist by reactiveProperty(metadata.artist)

        @Serializable
        override var genres: Set<Genre> by reactiveProperty(metadata.genres)

        @Transient
        private var _comments: String? = metadata.comments

        @Serializable
        override var comments: String?
            by reactiveProperty(
                getter = { _comments },
                setter = { _comments = it?.takeIf { s -> s.isNotEmpty() } }
            )

        @Serializable
        override var trackNumber: Short? by reactiveProperty(metadata.trackNumber)

        @Serializable
        override var discNumber: Short? by reactiveProperty(metadata.discNumber)

        @Serializable
        override var bpm: Float? by reactiveProperty(metadata.bpm)

        @Serializable
        override var album: Album by reactiveProperty(metadata.album)

        @Transient
        private var _coverImageBytes: ByteArray? = metadata.coverBytes?.copyOf()

        @Transient
        override var coverImageBytes: ByteArray?
            by reactiveProperty(
                getter = { _coverImageBytes?.copyOf() },
                setter = { _coverImageBytes = it?.copyOf() }
            )

        internal fun incrementPlayCount() = _playCount++

        override fun setPlayCount(count: Short) {
            require(count >= 0) { "Play count cannot be negative" }
            withEventsDisabled { _playCount = count }
        }

        override operator fun compareTo(other: AudioItem) = audioItemTrackDiscNumberComparator<AudioItem>().compare(this, other)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || javaClass != other.javaClass) return false
            val that = other as MutableAudioItem
            return trackNumber == that.trackNumber &&
                discNumber == that.discNumber &&
                bpm == that.bpm &&
                path == that.path &&
                title == that.title &&
                artist == that.artist &&
                album == that.album &&
                genres == that.genres &&
                comments == that.comments &&
                duration == that.duration
        }

        override fun hashCode() = Objects.hash(path, title, artist, album, genres, comments, trackNumber, discNumber, bpm, duration)

        override fun clone(): MutableAudioItem =
            MutableAudioItem(
                path,
                id,
                AudioItemMetadata(
                    title = title,
                    artist = artist,
                    album = album,
                    genres = genres,
                    comments = comments,
                    trackNumber = trackNumber,
                    discNumber = discNumber,
                    bpm = bpm,
                    encoder = encoder,
                    encoding = encoding,
                    bitRate = bitRate,
                    duration = duration,
                    coverBytes = _coverImageBytes?.copyOf()
                ),
                dateOfCreation,
                lastDateModified,
                _playCount
            )

        override fun toString() = "AudioItem(id=$id, path=$path, title=$title, artist=${artist.name})"
    }