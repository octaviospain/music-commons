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

import net.transgressoft.commons.music.audio.Album
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.AudioItemSerializerBase
import net.transgressoft.commons.music.audio.Genre
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

@get:JvmName("ObservableAudioItemMapSerializer")
internal val ObservableAudioItemMapSerializer: KSerializer<Map<Int, ObservableAudioItem>> = MapSerializer(Int.serializer(), ObservableAudioItemSerializer)

/**
 * Kotlinx serialization serializer for [ObservableAudioItem] instances.
 *
 * Serializes JavaFX audio items to JSON, preserving all metadata while excluding
 * transient JavaFX properties. Creates [FXAudioItem] instances during deserialization
 * that automatically reconstruct JavaFX property bindings.
 */
internal object ObservableAudioItemSerializer : AudioItemSerializerBase<ObservableAudioItem>() {

    override fun constructEntity(
        path: Path,
        id: Int,
        title: String,
        duration: Duration,
        bitRate: Int,
        artist: Artist,
        album: Album,
        genres: Set<Genre>,
        comments: String?,
        trackNumber: Short?,
        discNumber: Short?,
        bpm: Float?,
        encoder: String?,
        encoding: String?,
        dateOfCreation: LocalDateTime,
        lastDateModified: LocalDateTime,
        playCount: Short
    ): ObservableAudioItem =
        FXAudioItem(
            path = path,
            id = id,
            title = title,
            duration = duration,
            bitRate = bitRate,
            artist = artist,
            album = album,
            genres = genres,
            comments = comments,
            trackNumber = trackNumber,
            discNumber = discNumber,
            bpm = bpm,
            encoder = encoder,
            encoding = encoding,
            dateOfCreation = dateOfCreation,
            lastDateModified = lastDateModified,
            playCount = playCount
        )
}