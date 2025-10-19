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

package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.entity.IdentifiableEntity
import net.transgressoft.commons.music.audio.ReactiveAudioItem
import java.io.IOException
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Predicate
import kotlin.io.path.exists

/**
 * Represents a playlist of audio items that can optionally contain nested playlists.
 *
 * Supports exporting to M3U format and provides methods to query audio items within the playlist.
 */
interface AudioPlaylist<I : ReactiveAudioItem<I>> : IdentifiableEntity<Int>, Comparable<AudioPlaylist<I>> {

    override val uniqueId: String
        get() {
            return buildString {
                if (isDirectory) {
                    append("D-")
                }
                append(name)
            }
        }

    val isDirectory: Boolean

    val name: String

    val audioItems: List<I>

    val audioItemsRecursive: List<I>
        get() =
            buildList {
                addAll(audioItems)
                addAll(playlists.stream().flatMap { it.audioItemsRecursive.stream() }.toList())
            }

    val playlists: Set<AudioPlaylist<I>>

    fun audioItemsAllMatch(predicate: Predicate<I>) = audioItems.stream().allMatch { predicate.test(it) }

    fun audioItemsAnyMatch(predicate: Predicate<I>) = audioItems.stream().anyMatch { predicate.test(it) }

    /**
     * Exports this playlist to an M3U file at the specified destination path.
     *
     * If this is a directory playlist containing nested playlists, they will be exported
     * to a subdirectory alongside the main playlist file.
     *
     * @throws IOException if the destination file or directory already exists
     */
    @Throws(IOException::class)
    fun exportToM3uFile(destinationPath: Path) {
        if (destinationPath.exists()) {
            throw IOException("Destination file already exists: $destinationPath")
        } else {
            Files.createFile(destinationPath)
            PrintWriter(destinationPath.toString(), StandardCharsets.UTF_8.name()).use {
                printPlaylist(it, destinationPath)
            }
            if (isDirectory && playlists.isNotEmpty()) {
                val playlistDirectoryPath: Path = destinationPath.parent.resolve(name)
                if (!playlistDirectoryPath.exists()) {
                    Files.createDirectory(playlistDirectoryPath)
                    playlists.forEach {
                        it.exportToM3uFile(playlistDirectoryPath.resolve("${it.name}.m3u"))
                    }
                } else {
                    throw IOException("Contained playlists directory already exists: $playlistDirectoryPath")
                }
            }
        }
    }

    private fun printPlaylist(printWriter: PrintWriter, playlistPath: Path) {
        val parent = playlistPath.parent

        printWriter.println("#EXTM3U")
        if (isDirectory) {
            playlists.forEach {
                val containedPlaylistPath = parent.resolve(name).resolve("${it.name}.m3u")
                printWriter.println(parent.relativize(containedPlaylistPath))
            }
        }
        audioItems.forEach {
            printWriter.println("#EXTALB: ${it.album.name}")
            printWriter.println("#EXTART: ${it.artist.name}")
            printWriter.print("#EXTINF: ${it.duration.seconds}")
            printWriter.println(",${it.title}")
            val trackPath = parent.relativize(it.path)
            printWriter.println(trackPath)
        }
    }

    fun asJsonKeyValue(): String {
        val audioItemsString =
            buildString {
                append("[")
                audioItems.forEachIndexed { index, it ->
                    append(it.id)
                    if (index < audioItems.size - 1) {
                        append(",")
                    }
                }
                append("],")
            }
        val playlistIds =
            buildString {
                append("[")
                playlists.forEachIndexed { index, it ->
                    append(it.id)
                    if (index < playlists.size - 1) {
                        append(",")
                    }
                }
                append("]")
            }
        return """
            "$id": {
                "id": $id,
                "isDirectory": $isDirectory,
                "name": "$name",
                "audioItemIds": $audioItemsString
                "playlistIds": $playlistIds
            }"""
    }

    override fun compareTo(other: AudioPlaylist<I>) =
        Comparator.comparing(IdentifiableEntity<Int>::uniqueId, java.lang.String.CASE_INSENSITIVE_ORDER).compare(this, other)
}

/**
 * Converts a collection of audio playlists to a JSON string with playlists as key-value pairs.
 */
fun <A : AudioPlaylist<*>> Collection<A>.asJsonKeyValues(): String =
    buildString {
        append("{")
        this@asJsonKeyValues.forEachIndexed { index, it ->
            append(it.asJsonKeyValue())
            if (index < this@asJsonKeyValues.size - 1) {
                append(",")
            }
        }
        append("}")
    }