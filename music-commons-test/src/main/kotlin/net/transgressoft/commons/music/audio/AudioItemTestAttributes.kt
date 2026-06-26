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

import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime

/**
 * Test fixture bag combining production [AudioItemMetadata] with the identity and lifecycle fields
 * that live on a full [AudioItem] (path, id, creation/modification timestamps, play count).
 *
 * The metadata block embeds the production type so there is a single source of truth on what
 * "metadata" means. Flat property forwarders are provided as convenience accessors so existing
 * test DSL blocks (`audioItem { artist = X; album = Y }`) keep working — each forwarder rebuilds
 * [metadata] via [AudioItemMetadata.copy] on write. The forwarder for cover bytes is named
 * `coverImageBytes` for symmetry with the [ObservableAudioItem] / [AudioItem] property even though
 * the embedded metadata field is `coverBytes`.
 */
data class AudioItemTestAttributes(
    var path: Path,
    var id: Int = UNASSIGNED_ID,
    var metadata: AudioItemMetadata,
    var dateOfCreation: LocalDateTime,
    var lastDateModified: LocalDateTime,
    var playCount: Short
) {
    var title: String
        get() = metadata.title
        set(value) {
            metadata = metadata.copy(title = value)
        }

    var artist: Artist
        get() = metadata.artist
        set(value) {
            metadata = metadata.copy(artist = value)
        }

    var album: AlbumDetails
        get() = metadata.album
        set(value) {
            metadata = metadata.copy(album = value)
        }

    var genres: Set<Genre>
        get() = metadata.genres
        set(value) {
            metadata = metadata.copy(genres = value)
        }

    var comments: String?
        get() = metadata.comments
        set(value) {
            metadata = metadata.copy(comments = value)
        }

    var trackNumber: Short?
        get() = metadata.trackNumber
        set(value) {
            metadata = metadata.copy(trackNumber = value)
        }

    var discNumber: Short?
        get() = metadata.discNumber
        set(value) {
            metadata = metadata.copy(discNumber = value)
        }

    var bpm: Float?
        get() = metadata.bpm
        set(value) {
            metadata = metadata.copy(bpm = value)
        }

    var encoder: String?
        get() = metadata.encoder
        set(value) {
            metadata = metadata.copy(encoder = value)
        }

    var encoding: String?
        get() = metadata.encoding
        set(value) {
            metadata = metadata.copy(encoding = value)
        }

    var bitRate: Int
        get() = metadata.bitRate
        set(value) {
            metadata = metadata.copy(bitRate = value)
        }

    var duration: Duration
        get() = metadata.duration
        set(value) {
            metadata = metadata.copy(duration = value)
        }

    var coverImageBytes: ByteArray?
        get() = metadata.coverBytes
        set(value) {
            metadata = metadata.copy(coverBytes = value)
        }
}