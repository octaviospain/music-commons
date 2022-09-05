package net.transgressoft.commons.music.playlist

import com.google.common.base.MoreObjects
import com.google.common.base.Objects
import mu.KotlinLogging
import net.transgressoft.commons.music.audio.AudioItem
import java.io.IOException
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentSkipListSet

internal open class ImmutablePlaylistDirectory<I : AudioItem>(
    id: Int,
    name: String,
    audioItems: List<I>,
    playlists: Set<AudioPlaylist<I>>,
) : ImmutablePlaylist<I>(id, name, audioItems), AudioPlaylistDirectory<I> {

    private val logger = KotlinLogging.logger {}

    private val descendantPlaylists: MutableSet<AudioPlaylist<I>> = ConcurrentSkipListSet(playlists)

    override fun <N : AudioPlaylist<I>> containsPlaylist(playlist: N): Boolean {
        return descendantPlaylists.contains(playlist)
    }

    protected fun <N : AudioPlaylist<I>> addAll(playlists: Set<N>) {
        if (descendantPlaylists.addAll(playlists)) {
            logger.debug { "Added playlists to playlist directory '$name': $playlists" }
        }
    }

    protected fun <N : AudioPlaylist<I>> removeAll(playlists: Set<N>) {
        if (descendantPlaylists.removeAll(playlists)) {
            logger.debug { "Playlists removed from playlist directory '$name': $playlists" }
        }
    }

    override fun <N : AudioPlaylist<I>> descendantPlaylists(): Set<N> {
        return descendantPlaylists as Set<N>
    }

    override val isDirectory: Boolean
        get() = true

    @Throws(IOException::class)
    override fun exportToM3uFile(destinationPath: Path) {
        if (destinationPath.toFile().exists()) {
            logger.debug { "Destination file already exists: $destinationPath" }
        } else {
            Files.createFile(destinationPath)
            printDescendantPlaylistsToM3uFile(destinationPath)
            if (descendantPlaylists.isNotEmpty()) {
                val playlistDirectoryPath: Path = destinationPath.resolve(name)
                val playlistDirectoryFile = playlistDirectoryPath.toFile()
                if (!playlistDirectoryPath.toFile().exists()) {
                    Files.createDirectory(playlistDirectoryFile.toPath())
                    for (playlist in descendantPlaylists) {
                        playlist.exportToM3uFile(playlistDirectoryPath.resolve(playlist.name))
                    }
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun printDescendantPlaylistsToM3uFile(playlistFolderPath: Path) {
        PrintWriter(playlistFolderPath.toString(), StandardCharsets.UTF_8.name()).use { printWriter ->
            printWriter.println("#EXTM3U")
            descendantPlaylists.forEach {
                val descendantPlaylistPath = playlistFolderPath.parent.relativize(playlistFolderPath.resolve(it.name))
                printWriter.println(descendantPlaylistPath)
                super.printPlaylist(printWriter, playlistFolderPath)
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as ImmutablePlaylistDirectory<*>
        return Objects.equal(name, that.name) && Objects.equal(id, that.id)
    }

    override fun hashCode() = Objects.hashCode(name, id)

    override fun toString(): String {
        return MoreObjects.toStringHelper(this)
            .add("id", id)
            .add("name", name)
            .add("descendantPlaylists", descendantPlaylists.size)
            .add("audioItems", audioItems().size)
            .toString()
    }
}