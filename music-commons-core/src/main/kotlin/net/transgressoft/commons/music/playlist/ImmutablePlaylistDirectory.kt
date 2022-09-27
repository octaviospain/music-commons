package net.transgressoft.commons.music.playlist

import com.google.common.base.MoreObjects
import com.google.common.base.Objects
import mu.KotlinLogging
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.query.Attribute
import net.transgressoft.commons.query.QueryEntity
import java.io.IOException
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

internal open class ImmutablePlaylistDirectory<I : AudioItem, N : AudioPlaylist<I>>(
    id: Int,
    name: String,
    audioItems: List<I>? = null,
    playlists: Set<N>? = null,
) : ImmutablePlaylist<I>(id, name, audioItems), AudioPlaylistDirectory<I, N> {

    private val logger = KotlinLogging.logger {}

    private val _playlists: MutableSet<N> = mutableSetOf<N>().apply { playlists?.let { addAll(it) } }

    override fun containsPlaylist(playlist: N) = _playlists.contains(playlist)

    protected fun addAll(playlists: Set<N>) {
        if (_playlists.addAll(playlists)) {
            logger.debug { "Added playlists to playlist directory '$name': $playlists" }
        }
    }

    protected fun removeAll(playlists: Set<N>) {
        if (_playlists.removeAll(playlists)) {
            logger.debug { "Playlists removed from playlist directory '$name': $playlists" }
        }
    }

    override fun descendantPlaylists(): Set<N> {
        return _playlists.toSet()
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
            if (_playlists.isNotEmpty()) {
                val playlistDirectoryPath: Path = destinationPath.resolve(name)
                val playlistDirectoryFile = playlistDirectoryPath.toFile()
                if (!playlistDirectoryPath.toFile().exists()) {
                    Files.createDirectory(playlistDirectoryFile.toPath())
                    for (playlist in _playlists) {
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
            _playlists.forEach {
                val descendantPlaylistPath = playlistFolderPath.parent.relativize(playlistFolderPath.resolve(it.name))
                printWriter.println(descendantPlaylistPath)
                super.printPlaylist(printWriter, playlistFolderPath)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <A : Attribute<E, V>, E : QueryEntity, V : Any> get(attribute: A): V? {
        return if (attribute == PlaylistAttribute.PLAYLISTS)
            _playlists as V
        else
            super.get(attribute)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as ImmutablePlaylistDirectory<*, *>
        return Objects.equal(name, that.name) && Objects.equal(id, that.id)
    }

    override fun hashCode() = Objects.hashCode(name, id)

    override fun toString(): String {
        return MoreObjects.toStringHelper(this)
            .add("id", id)
            .add("name", name)
            .add("descendantPlaylists", _playlists.size)
            .add("audioItems", audioItems().size)
            .toString()
    }
}