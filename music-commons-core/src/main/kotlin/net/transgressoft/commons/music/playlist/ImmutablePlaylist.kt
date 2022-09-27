package net.transgressoft.commons.music.playlist

import com.google.common.base.MoreObjects
import com.google.common.base.Objects
import mu.KotlinLogging
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.query.Attribute
import net.transgressoft.commons.query.BooleanQueryTerm
import net.transgressoft.commons.query.QueryEntity
import java.io.IOException
import java.io.PrintWriter
import java.lang.String.CASE_INSENSITIVE_ORDER
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

internal open class ImmutablePlaylist<I : AudioItem>(
    final override val id: Int,
    theName: String,
    audioItems: List<I>?,
) : AudioPlaylist<I> {

    private val logger = KotlinLogging.logger {}

    private var _audioItems: MutableList<I> = mutableListOf<I>().apply { audioItems?.let { addAll(it) } }

    private var _name: String = theName

    override val name: String
        get() = _name

    override val uniqueId: String
        get() {
            val stringJoiner = StringJoiner("-")
                .add(id.toString())
            if (isDirectory) {
                stringJoiner.add("D")
            }
            return stringJoiner.add(name).toString()
        }

    protected open fun setNameInternal(newName: String) {
        if (name != newName) {
            logger.debug { "Changed name of playlist with id $id from '$name' to '$newName'" }
            _name = newName
        }
    }

    override fun audioItems(): List<I> = _audioItems.toList()

    override val isDirectory: Boolean
        get() = false

    protected fun addAll(audioItems: Collection<I>) {
        if (audioItems.isNotEmpty()) {
            this._audioItems.addAll(audioItems)
            logger.debug { "Added audio items to playlist '$name': $audioItems" }
        }
    }

    protected fun removeAll(audioItems: Collection<I>) {
        if (audioItems.isNotEmpty()) {
            audioItems.forEach { _audioItems.remove(it) }
            logger.debug { "Removed audio items from playlist '$name': $audioItems" }
        }
    }

    protected fun clear() {
        if (_audioItems.isNotEmpty()) {
            _audioItems.clear()
            logger.debug { "Playlist '$name' cleared" }
        }
    }

    override fun audioItemsAllMatch(queryPredicate: BooleanQueryTerm<AudioItem>) = _audioItems.stream().allMatch { queryPredicate.apply(it) }

    override fun audioItemsAnyMatch(queryPredicate: BooleanQueryTerm<AudioItem>) = _audioItems.stream().anyMatch { queryPredicate.apply(it) }

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

    protected fun printPlaylist(printWriter: PrintWriter, playlistPath: Path) {
        logger.info { "Writing playlist '$name' to file $playlistPath" }
        printWriter.println("#EXTM3U")
        _audioItems.forEach {
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
        Comparator.comparing(QueryEntity::uniqueId, CASE_INSENSITIVE_ORDER).compare(this, other)

    @Suppress("UNCHECKED_CAST")
    override fun <A : Attribute<E, V>, E : QueryEntity, V : Any> get(attribute: A): V? {
        return when (attribute as PlaylistAttribute<AudioPlaylist<AudioItem>, V>) {
            PlaylistAttribute.AUDIO_ITEMS -> _audioItems as V
            PlaylistAttribute.NAME -> _name as V
            PlaylistAttribute.PLAYLISTS -> emptySet<AudioPlaylist<I>>() as V
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as ImmutablePlaylist<*>
        return Objects.equal(id, that.id)
    }

    override fun hashCode() = Objects.hashCode(id)

    override fun toString(): String {
        return MoreObjects.toStringHelper(this)
            .add("id", id)
            .add("name", name)
            .add("audioItems", _audioItems.size)
            .omitNullValues()
            .toString()
    }
}