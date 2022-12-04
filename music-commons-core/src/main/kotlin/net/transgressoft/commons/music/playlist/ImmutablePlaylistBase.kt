package net.transgressoft.commons.music.playlist

import mu.KotlinLogging
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.query.QueryEntity
import java.io.IOException
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.function.Predicate
import kotlin.streams.toList

abstract class ImmutablePlaylistBase<I : AudioItem>(
    override val id: Int,
    override val isDirectory: Boolean,
    override val name: String,
    override val audioItems: List<I> = emptyList(),
    override val playlists: Set<AudioPlaylist<I>> = emptySet()
) : AudioPlaylist<I> {

    private val logger = KotlinLogging.logger {}

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
        if (destinationPath.toFile().exists()) {
            logger.debug { "Destination file already exists: $destinationPath" }
        } else {
            Files.createFile(destinationPath)
            PrintWriter(destinationPath.toFile(), StandardCharsets.UTF_8.name()).use { printWriter ->
                printPlaylist(printWriter, destinationPath)
            }
        }
    }

//    override fun exportToM3uFile(destinationPath: Path) {
//        if (destinationPath.toFile().exists()) {
//            logger.debug { "Destination file already exists: $destinationPath" }
//        } else {
//            Files.createFile(destinationPath)
//            printDescendantPlaylistsToM3uFile(destinationPath)
//            if (_playlists.isNotEmpty()) {
//                val playlistDirectoryPath: Path = destinationPath.resolve(name)
//                val playlistDirectoryFile = playlistDirectoryPath.toFile()
//                if (!playlistDirectoryPath.toFile().exists()) {
//                    Files.createDirectory(playlistDirectoryFile.toPath())
//                    for (playlist in _playlists) {
//                        playlist.exportToM3uFile(playlistDirectoryPath.resolve(playlist.name))
//                    }
//                }
//            }
//        }
//    }
//
//    @Throws(IOException::class)
//    private fun printDescendantPlaylistsToM3uFile(playlistFolderPath: Path) {
//        PrintWriter(playlistFolderPath.toString(), StandardCharsets.UTF_8.name()).use { printWriter ->
//            printWriter.println("#EXTM3U")
//            _playlists.forEach {
//                val descendantPlaylistPath = playlistFolderPath.parent.relativize(playlistFolderPath.resolve(it.name))
//                printWriter.println(descendantPlaylistPath)
//                super.printPlaylist(printWriter, playlistFolderPath)
//            }
//        }
//    }

    protected fun printPlaylist(printWriter: PrintWriter, playlistPath: Path) {
        logger.info { "Writing playlist '$name' to file $playlistPath" }
        printWriter.println("#EXTM3U")
        audioItems.forEach {
            printWriter.println("#EXTALB: ${it.album}")
            printWriter.println("#EXTART:${it.artist}")
            printWriter.print("#EXTINF:${it.duration.seconds}")
            printWriter.println(",${it.title}")
            val parent = playlistPath.parent
            val trackPath = parent.relativize(it.path)
            printWriter.println(trackPath)
        }
    }

    override fun compareTo(other: AudioPlaylist<I>) =
        Comparator.comparing(QueryEntity::uniqueId, java.lang.String.CASE_INSENSITIVE_ORDER).compare(this, other)

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

    override fun toString() = "ImmutablePlaylistBase(id=$id, isDirectory=$isDirectory, name='$name', audioItems=$audioItems, playlists=$playlists)"
}