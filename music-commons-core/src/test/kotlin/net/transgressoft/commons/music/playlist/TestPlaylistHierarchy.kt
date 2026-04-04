package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.lirp.entity.CascadeAction
import net.transgressoft.lirp.persistence.AggregateCollectionRef
import net.transgressoft.lirp.persistence.CollectionRefEntry
import net.transgressoft.lirp.persistence.LirpRefAccessor
import net.transgressoft.lirp.persistence.RefEntry
import net.transgressoft.lirp.persistence.RegistryBase
import net.transgressoft.lirp.persistence.Repository
import net.transgressoft.lirp.persistence.VolatileRepository
import net.transgressoft.lirp.persistence.mutableAggregateList
import net.transgressoft.lirp.persistence.mutableAggregateSet

/**
 * Test stub for [PlaylistHierarchyBase] used to test base class behavior without serialization dependencies.
 *
 * Creates [MutableAudioPlaylist] instances with minimal configuration to enable isolated
 * unit testing of the base class playlist hierarchy and audio item synchronization behavior.
 */
internal class TestPlaylistHierarchy(
    repository: Repository<Int, MutableAudioPlaylist> = VolatileRepository("TestPlaylistHierarchy")
) : PlaylistHierarchyBase<AudioItem, MutableAudioPlaylist>(repository) {

    init {
        RegistryBase.deregisterRepository(MutableAudioPlaylist::class.java)
        RegistryBase.registerRepository(MutableAudioPlaylist::class.java, repository)
    }

    override fun createPlaylist(name: String): MutableAudioPlaylist {
        require(findByName(name).isEmpty) { "Playlist with name '$name' already exists" }
        return TestMutablePlaylist(newId(), name, false).also(::add)
    }

    override fun createPlaylist(name: String, audioItems: List<AudioItem>): MutableAudioPlaylist {
        require(findByName(name).isEmpty) { "Playlist with name '$name' already exists" }
        return TestMutablePlaylist(newId(), name, false, audioItems.map { it.id }).also(::add)
    }

    override fun createPlaylistDirectory(name: String): MutableAudioPlaylist {
        require(findByName(name).isEmpty) { "Playlist with name '$name' already exists" }
        return TestMutablePlaylist(newId(), name, true).also(::add)
    }

    override fun createPlaylistDirectory(name: String, audioItems: List<AudioItem>): MutableAudioPlaylist {
        require(findByName(name).isEmpty) { "Playlist with name '$name' already exists" }
        return TestMutablePlaylist(newId(), name, true, audioItems.map { it.id }).also(::add)
    }

    override fun close() {
        super.close()
        RegistryBase.deregisterRepository(MutableAudioPlaylist::class.java)
    }

    inner class TestMutablePlaylist(
        id: Int,
        name: String,
        isDirectory: Boolean,
        initialAudioItemIds: List<Int> = emptyList(),
        initialPlaylistIds: Set<Int> = emptySet()
    ) : MutablePlaylistBase<AudioItem, MutableAudioPlaylist>(id, name, isDirectory),
        MutableAudioPlaylist {

        override val audioItems by mutableAggregateList<Int, AudioItem>(initialAudioItemIds)

        override val playlists by mutableAggregateSet<Int, MutableAudioPlaylist>(initialPlaylistIds)

        override fun clone(): TestMutablePlaylist =
            TestMutablePlaylist(id, name, isDirectory, audioItems.referenceIds.toList(), LinkedHashSet(playlists.referenceIds))
    }
}

/**
 * Manually-written aggregate reference accessor for [TestPlaylistHierarchy.TestMutablePlaylist].
 *
 * Required because KSP cannot generate a public accessor for a private inner class.
 * The naming convention `{EntityJvmName}_LirpRefAccessor` is used so that
 * [net.transgressoft.lirp.persistence.RegistryBase.discoverRefs] locates it via
 * [Class.forName] at runtime.
 */
@Suppress("ClassName")
internal class `TestPlaylistHierarchy$TestMutablePlaylist_LirpRefAccessor` : LirpRefAccessor<MutableAudioPlaylist> {

    override val entries: List<RefEntry<*, MutableAudioPlaylist>> = emptyList()

    @Suppress("UNCHECKED_CAST")
    override val collectionEntries: List<CollectionRefEntry<*, MutableAudioPlaylist>> =
        listOf(
            CollectionRefEntry(
                refName = "audioItems",
                idsGetter = { playlist ->
                    (playlist.audioItems as AggregateCollectionRef<*, *>).referenceIds
                },
                delegateGetter = { playlist ->
                    playlist.audioItems as AggregateCollectionRef<*, *>
                },
                referencedClass = AudioItem::class.java,
                cascadeAction = CascadeAction.NONE,
                isOrdered = true
            ),
            CollectionRefEntry(
                refName = "playlists",
                idsGetter = { playlist ->
                    (playlist.playlists as AggregateCollectionRef<*, *>).referenceIds
                },
                delegateGetter = { playlist ->
                    playlist.playlists as AggregateCollectionRef<*, *>
                },
                referencedClass = MutableAudioPlaylist::class.java,
                cascadeAction = CascadeAction.NONE,
                isOrdered = false
            )
        )

    override fun cancelAllBubbleUp(entity: MutableAudioPlaylist) {
        entries.forEach { entry -> entry.delegateGetter(entity).cancelBubbleUp() }
    }
}