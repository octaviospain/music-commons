package net.transgressoft.commons.music.playlist

import mu.KotlinLogging
import net.transgressoft.commons.IdentifiableEntity
import net.transgressoft.commons.music.audio.AudioItem
import java.io.IOException
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.function.Predicate
import kotlin.io.path.exists
import kotlin.streams.toList

abstract class ImmutablePlaylistBase<I : AudioItem>(
    override val id: Int,
    override val isDirectory: Boolean,
    override val name: String,
    override val audioItems: List<I> = emptyList(),
    override val playlists: Set<AudioPlaylist<I>> = emptySet()
) : AudioPlaylist<I> {

    private val logger = KotlinLogging.logger(javaClass.name)

    override val uniqueId: String
        get() {
            val stringJoiner = StringJoiner("-")
                .add(id.toString())
            if (isDirectory) {
                stringJoiner.add("D")
            }
            return stringJoiner.add(name).toString()
        }

    override val audioItemsRecursive: List<I>
        get() = buildList {
            addAll(audioItems)
            addAll(playlists.stream().flatMap { it.audioItemsRecursive.stream() }.toList())
        }

    override fun audioItemsAllMatch(predicate: Predicate<AudioItem>) = audioItems.stream().allMatch { predicate.test(it) }

    override fun audioItemsAnyMatch(predicate: Predicate<AudioItem>) = audioItems.stream().anyMatch { predicate.test(it) }

    @Throws(IOException::class)
    override fun exportToM3uFile(destinationPath: Path) {
        if (destinationPath.exists()) {
            throw IOException("Destination file already exists: $destinationPath").also {
                logger.error("Error trying to export playlist '$name' to '$destinationPath'", it)
            }
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
                    throw IOException("Contained playlists directory already exists: $playlistDirectoryPath").also {
                        logger.error("Error trying to export contained playlists inside '$name' to '$playlistDirectoryPath'", it)
                    }
                }
            }
        }
    }

    private fun printPlaylist(printWriter: PrintWriter, playlistPath: Path) {
        logger.info { "Writing playlist '$name' to file $playlistPath" }
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ImmutablePlaylistBase<*>

        if (isDirectory != other.isDirectory) return false
        if (name != other.name) return false
        if (audioItems != other.audioItems) return false
        if (playlists != other.playlists) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isDirectory.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + audioItems.hashCode()
        result = 31 * result + playlists.hashCode()
        return result
    }

    override fun toString() = "ImmutablePlaylistBase(id=$id, isDirectory=$isDirectory, name='$name', playlists=${playlists.size}, audioItems=$audioItems)"
}