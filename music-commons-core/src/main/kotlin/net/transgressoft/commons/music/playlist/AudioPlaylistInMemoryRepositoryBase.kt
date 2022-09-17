package net.transgressoft.commons.music.playlist

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Multimap
import com.google.common.collect.MultimapBuilder
import mu.KotlinLogging
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.query.Attribute
import net.transgressoft.commons.query.BooleanQueryTerm
import net.transgressoft.commons.query.InMemoryRepository
import net.transgressoft.commons.query.RepositoryException
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.stream.Collectors

@Suppress("UNCHECKED_CAST")
abstract class AudioPlaylistInMemoryRepositoryBase<I : AudioItem, P : AudioPlaylist<I>, D : AudioPlaylistDirectory<I>, MP : MutableAudioPlaylist<I>, MD : MutableAudioPlaylistDirectory<I>>
protected constructor(
    playlistsById: MutableMap<Int, MP>,
    directoriesById: MutableMap<Int, MD>,
) : AudioPlaylistRepository<I, P, D> {

    private val logger = KotlinLogging.logger {}

    private val idCounter = AtomicInteger(1)
    private val idSet: MutableSet<Int> = HashSet()
    private val playlists: InMemoryRepository<MP>
    private val directories: InMemoryRepository<MD>
    private val playlistsMultiMap: Multimap<String, String> = MultimapBuilder.treeKeys().treeSetValues().build()

    init {
        playlists = InMemoryRepository(playlistsById, null)
        directories = InMemoryRepository(directoriesById, null)
    }

    protected abstract fun toMutablePlaylist(playlistDirectory: P): MP
    protected abstract fun toMutablePlaylists(audioPlaylists: Set<P>): Set<MP>
    protected abstract fun toMutableDirectory(playlistDirectory: D): MD
    protected abstract fun toImmutablePlaylist(audioPlaylist: MutableAudioPlaylist<I>): P
    protected abstract fun toImmutablePlaylist(audioPlaylist: P): P
    protected abstract fun toImmutablePlaylistDirectory(playlistDirectory: MutableAudioPlaylistDirectory<I>): P // TODO change to nullable?
    protected abstract fun toImmutablePlaylistDirectories(audioPlaylists: Set<P>): Set<P>

    protected fun getNewId(): Int {
        var id: Int
        do {
            id = idCounter.getAndIncrement()
        } while (idSet.contains(id))
        idSet.add(id)
        return id
    }

    override fun add(entity: P) = addOrReplaceAll(setOf(entity))

    override fun addOrReplace(entity: P) = addOrReplaceAll(setOf(entity))

    override fun addOrReplaceAll(entities: Set<P>): Boolean {
        val added = AtomicBoolean(false)
        entities.forEach { added.set(added.get() || addInternal(it)) }
        return added.get()
    }

    private fun addInternal(playlist: P): Boolean {
        var added = false
        if (playlist.isDirectory) {
            if (directories.findByAttribute(PlaylistStringAttribute.NAME as Attribute<MD, String>, playlist.name).isEmpty()) {
                val mutableDirectory = toMutableDirectory(playlist as D)
                added = directories.add(mutableDirectory)
                added = added or addRecursive(mutableDirectory, mutableDirectory.descendantPlaylists())
            }
        } else {
            if (playlists.findByAttribute(PlaylistStringAttribute.NAME as Attribute<MP, String>, playlist.name).isEmpty()) {
                added = playlists.add(toMutablePlaylist(playlist))
            }
        }
        return added
    }

    private fun addRecursive(parent: MD, mutablePlaylistNodes: Set<MP>): Boolean {
        var result = false
        for (playlist in mutablePlaylistNodes) {
            playlistsMultiMap.put(parent.uniqueId, playlist.uniqueId)
            result = result or addInternal(playlist as P)
        }
        return result
    }

    override fun remove(entity: P) = removeAll(setOf(entity))

    override fun removeAll(entities: Set<P>) = removeRecursive(entities)

    private fun removeRecursive(mutablePlaylistNodes: Set<P>): Boolean {
        val result = AtomicBoolean(false)
        for (p in mutablePlaylistNodes) {
            playlistsMultiMap.asMap().remove(p.uniqueId)
            if (p.isDirectory) {
                result.set(removeRecursive(findDirectory(p.id).descendantPlaylists()) || result.get())
            }
            directories.findById(p.id).ifPresent { result.set(directories.remove(it) || result.get()) }
            playlists.findById(p.id).ifPresent { result.set(playlists.remove(it) || result.get()) }
        }
        return result.get()
    }

    private fun findDirectory(id: Int): MD =
        directories.findById(id).orElseThrow { RuntimeException("Playlist Directory not found by id: $id") }

    override fun clear() {
        playlists.clear()
        directories.clear()
        playlistsMultiMap.clear()
    }

    override operator fun contains(id: Int): Boolean {
        val existsIdInPlaylists = playlists.contains(id)
        val existsIdInDirectories = directories.contains(id)
        assert(!(existsIdInPlaylists && existsIdInDirectories)) { "same id exists in playlists and directories: $id" }
        return existsIdInPlaylists || existsIdInDirectories
    }

    override operator fun contains(query: BooleanQueryTerm<P>) =
        playlists.contains(query as BooleanQueryTerm<MP>) || directories.contains(query as BooleanQueryTerm<MD>)

    override fun search(query: BooleanQueryTerm<P>): List<P> =
        ImmutableList.builder<P>()
            .addAll(playlists.search((query as BooleanQueryTerm<MP>)).stream()
                .map { this.toImmutablePlaylist(it) }
                .toList())
            .addAll(directories.search((query as BooleanQueryTerm<MD>)).stream()
                .map { toImmutablePlaylistDirectory(it) }
                .toList()).build()

    override fun findById(id: Int): Optional<P> = findByIdInternal(id).map { this.toImmutablePlaylist(it) }

    private fun findByIdInternal(id: Int) = playlists.findById(id).or { directories.findById(id) as Optional<out MP> }

    override fun findByUniqueId(uniqueId: String): Optional<P> =
        playlists.findByUniqueId(uniqueId)
            .map { this.toImmutablePlaylist(it) }
            .or { directories.findByUniqueId(uniqueId).map { toImmutablePlaylistDirectory(it) } }

    override fun <A : Attribute<P, V>, V : Any> findByAttribute(attribute: A, value: V): List<P> =
        ImmutableList.builder<P>()
            .addAll(playlists.findByAttribute(attribute as Attribute<MP, V>, value).stream()
                .map { this.toImmutablePlaylist(it) }
                .toList())
            .addAll(directories.findByAttribute(attribute as Attribute<MD, V>, value).stream()
                .map { toImmutablePlaylistDirectory(it) }
                .toList()).build()

    @Throws(RepositoryException::class)
    override fun <A : Attribute<P, V>, V : Any> findSingleByAttribute(attribute: A, value: V): Optional<P> {
        val foundPlaylist = playlists.findSingleByAttribute(attribute as Attribute<MP, V>, value).orElse(null)
        val foundDirectory = directories.findSingleByAttribute(attribute as Attribute<MD, V>, value).orElse(null)
        if (foundPlaylist != null && foundDirectory != null)
            throw RepositoryException("Found 2 entities with the same attribute: [$foundPlaylist, $foundDirectory]")

        return if (foundPlaylist != null)
            Optional.of(toImmutablePlaylist(foundPlaylist))
        else Optional.ofNullable(
            toImmutablePlaylistDirectory(foundDirectory)
        )
    }

    override fun size() = playlists.size() + directories.size()

    override val isEmpty: Boolean
        get() = playlists.isEmpty && directories.isEmpty

    @Suppress("UnstableApiUsage")
    override fun iterator(): Iterator<P> {
        val setBuilder = ImmutableSet.builderWithExpectedSize<P>(playlists.size() + directories.size())
        playlists.forEach(Consumer { setBuilder.add(toImmutablePlaylist(it)) })
        directories.forEach(Consumer { setBuilder.add(toImmutablePlaylistDirectory(it)) })
        return setBuilder.build().iterator()
    }

    override fun numberOfPlaylists() = playlists.size()

    override fun numberOfPlaylistDirectories() = directories.size()

    @Throws(RepositoryException::class)
    override fun createPlaylist(name: String) = createPlaylist(name, emptyList())

    @Throws(RepositoryException::class)
    override fun createPlaylist(name: String, audioItems: List<I>): P =
        if (findSinglePlaylistByName(name).isEmpty) {
            val playlist = MutablePlaylist(getNewId(), name, audioItems) as MP
            playlists.add(playlist)
            toImmutablePlaylist(playlist)
        } else {
            throw RepositoryException("Playlist with name '$name' already exists")
        }

    @Throws(RepositoryException::class)
    override fun createPlaylistDirectory(name: String) = createPlaylistDirectory(name, emptyList())

    @Throws(RepositoryException::class)
    override fun createPlaylistDirectory(name: String, audioItems: List<I>): D =
        if (findSingleDirectoryByName(name).isEmpty) {
            val playlistDirectory = MutablePlaylistDirectory(getNewId(), name, audioItems) as MD
            directories.add(playlistDirectory)
            toImmutablePlaylistDirectory(playlistDirectory) as D
        } else {
            throw RepositoryException("Playlist with name '$name' already exists")
        }

    override fun addAudioItemsToPlaylist(audioItems: Collection<I>, playlist: P) {
        playlists.findById(playlist.id)
            .or { directories.findById(playlist.id) as Optional<out MP> }
            .ifPresent { it.addAudioItems(audioItems) }
    }

    override fun addPlaylistsToDirectory(playlist: Set<P>, directory: D) {
        val mutablePlaylists = toMutablePlaylists(playlist)
        directories.findById(directory.id)
            .ifPresent {
                it.addPlaylists(mutablePlaylists)
                playlistsMultiMap.putAll(
                    it.uniqueId,
                    mutablePlaylists.stream().map { d -> d.uniqueId }.collect(Collectors.toSet())
                )
            }
    }

    override fun movePlaylist(playlistToMove: P, destinationPlaylist: D) {
        findByIdInternal(playlistToMove.id)
            .ifPresent { playlist: MP ->
                directories.findById(destinationPlaylist.id).ifPresent { playlistDirectory: MD ->
                    ancestor(playlistToMove).ifPresent { ancestor: MD ->
                        playlistsMultiMap.remove(ancestor.uniqueId, playlist.uniqueId)
                        ancestor.removePlaylists(playlist)
                    }
                    playlistsMultiMap.put(playlistDirectory.uniqueId, playlist.uniqueId)
                    playlistDirectory.addPlaylists(playlist)
                    logger.debug { "Playlist '${playlistToMove.name}' moved to '${destinationPlaylist.name}'" }
                }
            }
    }

    private fun ancestor(playlistNode: P): Optional<MD> =
        if (playlistsMultiMap.containsValue(playlistNode.uniqueId)) {
            playlistsMultiMap.entries().stream()
                .filter { playlistNode.uniqueId == it.value }
                .map { directories.findByUniqueId(it.key).get() }
                .findFirst()
        } else {
            Optional.empty()
        }

    override fun removeAudioItemsFromPlaylist(audioItems: Collection<I>, playlist: P) {
        findByIdInternal(playlist.id).ifPresent { it.removeAudioItems(audioItems) }
    }

    override fun removeAudioItems(audioItems: Collection<I>) {
        playlists.forEach { it.removeAudioItems(audioItems) }
        directories.forEach { it.removeAudioItems(audioItems) }
    }

    override fun findAllByName(name: String): ImmutableList<P> =
        ImmutableList.builder<P>()
            .addAll(playlists.findByAttribute(PlaylistStringAttribute.NAME as Attribute<MP, String>, name).stream()
                .map { this.toImmutablePlaylist(it) }
                .collect(Collectors.toSet()))
            .addAll(directories.findByAttribute(PlaylistStringAttribute.NAME as Attribute<MD, String>, name).stream()
                .map { toImmutablePlaylistDirectory(it) }
                .collect(Collectors.toSet())).build()

    override fun findSinglePlaylistByName(name: String): Optional<P> =
        try {
            playlists.findSingleByAttribute(PlaylistStringAttribute.NAME as Attribute<MP, String>, name)
                .map { this.toImmutablePlaylist(it) }
        } catch (exception: RepositoryException) {
            throw IllegalStateException(exception)
        }

    override fun findSingleDirectoryByName(name: String): Optional<D> =
        try {
            directories.findSingleByAttribute(PlaylistStringAttribute.NAME as Attribute<MD, String>, name)
                .map { toImmutablePlaylistDirectory(it) as D }
        } catch (exception: RepositoryException) {
            throw IllegalStateException(exception)
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as AudioPlaylistInMemoryRepositoryBase<I, P, D, MP, MD>
        return playlistsMultiMap == that.playlistsMultiMap && playlists == that.playlists && directories == that.directories
    }

    override fun hashCode(): Int {
        return Objects.hash(playlistsMultiMap, playlists, directories)
    }
}