package net.transgressoft.commons.music.itunes

import net.transgressoft.commons.music.CoreMusicLibrary
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.playlist.MutableAudioPlaylist
import net.transgressoft.commons.util.OsDetector
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

@DisplayName("ItunesPlaylistBuilder")
internal class ItunesPlaylistBuilderTest : StringSpec({

    lateinit var musicLibrary: CoreMusicLibrary
    lateinit var builder: ItunesPlaylistBuilder<AudioItem, MutableAudioPlaylist>

    beforeEach {
        musicLibrary = CoreMusicLibrary.builder().build()
        builder = ItunesPlaylistBuilder(musicLibrary)
    }

    afterEach {
        musicLibrary.close()
    }

    fun playlistFor(
        name: String,
        persistentId: String,
        isFolder: Boolean = false,
        parentId: String? = null,
        trackIds: List<Int> = emptyList()
    ): ItunesPlaylist =
        ItunesPlaylist(
            name = name,
            persistentId = persistentId,
            parentPersistentId = parentId,
            isFolder = isFolder,
            trackIds = trackIds
        )

    "ItunesPlaylistBuilder expandWithAncestors preserves ancestor folders when only leaf playlists are selected" {
        val grandparent = playlistFor("Root Folder", "GF1", isFolder = true)
        val parent = playlistFor("Sub Folder", "PF1", isFolder = true, parentId = "GF1")
        val leaf = playlistFor("Leaf Playlist", "LEAF1", parentId = "PF1")
        val itunesLibrary = ItunesLibrary(emptyMap(), listOf(grandparent, parent, leaf))

        val expanded = builder.expandWithAncestors(listOf(leaf), itunesLibrary)

        // All three playlists should be in the expansion
        expanded shouldHaveSize 3
        expanded.map { it.persistentId } shouldContain "GF1"
        expanded.map { it.persistentId } shouldContain "PF1"
        expanded.map { it.persistentId } shouldContain "LEAF1"
    }

    "ItunesPlaylistBuilder expandWithAncestors returns only selected playlists when they have no ancestors" {
        val standalone = playlistFor("Standalone", "S1")
        val itunesLibrary = ItunesLibrary(emptyMap(), listOf(standalone))

        val expanded = builder.expandWithAncestors(listOf(standalone), itunesLibrary)

        expanded shouldHaveSize 1
        expanded.first().persistentId shouldBe "S1"
    }

    "ItunesPlaylistBuilder createPlaylists creates folder directory and regular playlist" {
        val folder = playlistFor("My Folder", "F1", isFolder = true)
        val playlist = playlistFor("My Playlist", "P1", parentId = "F1")

        val rejected = builder.createPlaylists(listOf(folder, playlist), emptyMap(), null)

        rejected shouldHaveSize 0
        musicLibrary.findPlaylistByName("My Folder").isPresent shouldBe true
        musicLibrary.findPlaylistByName("My Playlist").isPresent shouldBe true
    }

    "ItunesPlaylistBuilder createPlaylists rejects a Windows-invalid playlist name on Windows" {
        val forbidden = playlistFor("Bad|Name", "F1")
        OsDetector.withOverriddenIsWindows(true) {
            val rejected = builder.createPlaylists(listOf(forbidden), emptyMap(), null)

            rejected shouldHaveSize 1
            rejected.first().name shouldBe "Bad|Name"
            rejected.first().reason shouldBe RejectionReason.ForbiddenChar('|')
        }
    }
})