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

import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.lirp.entity.IdentifiableEntity
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
 * @since 1.0
 */
public interface AudioPlaylist<I : ReactiveAudioItem<I>> : IdentifiableEntity<Int>, Comparable<AudioPlaylist<I>> {

    /**
     * Stable, human-readable identity string used for sorting and equality checks. Prefixed
     * with `"D-"` for directory playlists so directories sort predictably alongside leaf playlists.
     */
    override val uniqueId: String
        get() {
            return buildString {
                if (isDirectory) {
                    append("D-")
                }
                append(name)
            }
        }

    /**
     * Whether this playlist acts as a directory that contains nested playlists.
     * @since 1.0
     */
    public val isDirectory: Boolean

    /**
     * Display name of this playlist, unique within its parent directory.
     * @since 1.0
     */
    public val name: String

    /**
     * Ordered list of audio items directly held by this playlist.
     * @since 1.0
     */
    public val audioItems: List<I>

    /**
     * Flattened list of all audio items in this playlist and all nested [playlists], in depth-first order.
     * @since 1.0
     */
    public val audioItemsRecursive: List<I>
        get() =
            buildList {
                addAll(audioItems)
                addAll(playlists.stream().flatMap { it.audioItemsRecursive.stream() }.toList())
            }

    /**
     * Nested playlists directly contained within this playlist directory. Empty for leaf playlists.
     * @since 1.0
     */
    public val playlists: Set<AudioPlaylist<I>>

    /**
     * Returns `true` if all audio items in this playlist satisfy [predicate].
     *
     * @param predicate the condition to test against each item
     * @since 1.0
     */
    public fun audioItemsAllMatch(predicate: Predicate<I>): Boolean = audioItems.stream().allMatch { predicate.test(it) }

    /**
     * Returns `true` if at least one audio item in this playlist satisfies [predicate].
     *
     * @param predicate the condition to test against each item
     * @since 1.0
     */
    public fun audioItemsAnyMatch(predicate: Predicate<I>): Boolean = audioItems.stream().anyMatch { predicate.test(it) }

    /**
     * Exports this playlist to an M3U file at the specified destination path.
     *
     * If this is a directory playlist containing nested playlists, they will be exported
     * to a subdirectory alongside the main playlist file.
     *
     * @throws IOException if the destination file or directory already exists
     * @since 1.0
     */
    @Throws(IOException::class)
    public fun exportToM3uFile(destinationPath: Path) {
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

    /**
     * Returns a JSON snippet with this playlist's numeric ID as the key and its metadata as the value,
     * including the audio item IDs and nested playlist IDs.
     *
     * @return JSON key-value string suitable for embedding in a larger JSON map
     * @since 1.0
     */
    public fun asJsonKeyValue(): String {
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
                "audioItems": $audioItemsString
                "playlists": $playlistIds,
                "isDirectory": $isDirectory,
                "name": "$name"
            }"""
    }

    override fun compareTo(other: AudioPlaylist<I>): Int =
        Comparator.comparing(IdentifiableEntity<Int>::uniqueId, java.lang.String.CASE_INSENSITIVE_ORDER).compare(this, other)
}

/**
 * Converts a collection of audio playlists to a JSON string with playlists as key-value pairs.
 * @since 1.0
 */
public fun <A : AudioPlaylist<*>> Collection<A>.asJsonKeyValues(): String =
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