package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.lirp.persistence.Repository
import net.transgressoft.lirp.persistence.VolatileRepository

/**
 * Test stub for [PlaylistHierarchyBase] used to test base class behavior without serialization dependencies.
 *
 * Creates [MutableAudioPlaylist] instances with minimal configuration to enable isolated
 * unit testing of the base class playlist hierarchy and audio item synchronization behavior.
 */
internal class TestPlaylistHierarchy(
    repository: Repository<Int, MutableAudioPlaylist> = VolatileRepository("TestPlaylistHierarchy")
) : PlaylistHierarchyBase<AudioItem, MutableAudioPlaylist>(repository) {

    override fun createPlaylist(name: String): MutableAudioPlaylist {
        require(findByName(name).isEmpty) { "Playlist with name '$name' already exists" }
        return TestMutablePlaylist(newId(), false, name).also { add(it) }
    }

    override fun createPlaylist(name: String, audioItems: List<AudioItem>): MutableAudioPlaylist {
        require(findByName(name).isEmpty) { "Playlist with name '$name' already exists" }
        return TestMutablePlaylist(newId(), false, name, audioItems).also { add(it) }
    }

    override fun createPlaylistDirectory(name: String): MutableAudioPlaylist {
        require(findByName(name).isEmpty) { "Playlist with name '$name' already exists" }
        return TestMutablePlaylist(newId(), true, name).also { add(it) }
    }

    override fun createPlaylistDirectory(name: String, audioItems: List<AudioItem>): MutableAudioPlaylist {
        require(findByName(name).isEmpty) { "Playlist with name '$name' already exists" }
        return TestMutablePlaylist(newId(), true, name, audioItems).also { add(it) }
    }

    private inner class TestMutablePlaylist(
        id: Int,
        isDirectory: Boolean,
        name: String,
        audioItems: List<AudioItem> = listOf(),
        playlists: Set<MutableAudioPlaylist> = setOf()
    ) : MutablePlaylistBase(id, isDirectory, name, audioItems, playlists), MutableAudioPlaylist {

        override fun clone(): TestMutablePlaylist = TestMutablePlaylist(id, isDirectory, name, audioItems.toList(), playlists.toSet())
    }
}