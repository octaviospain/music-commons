package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.IdentifiableEntity
import net.transgressoft.commons.music.audio.ReactiveAudioItem
import java.io.IOException
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Predicate
import kotlin.io.path.exists

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
        get() = buildList {
            addAll(audioItems)
            addAll(playlists.stream().flatMap { it.audioItemsRecursive.stream() }.toList())
        }

    val playlists: Set<AudioPlaylist<I>>

    fun audioItemsAllMatch(predicate: Predicate<I>) = audioItems.stream().allMatch { predicate.test(it) }

    fun audioItemsAnyMatch(predicate: Predicate<I>) = audioItems.stream().anyMatch { predicate.test(it) }

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

    override fun compareTo(other: AudioPlaylist<I>) =
        Comparator.comparing(IdentifiableEntity<Int>::uniqueId, java.lang.String.CASE_INSENSITIVE_ORDER).compare(this, other)
}