package net.transgressoft.commons.music.playlist

import com.google.common.truth.Truth.assertThat
import net.transgressoft.commons.music.MusicLibraryTestBase
import net.transgressoft.commons.music.audio.AudioItem
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * @author Octavio Calleya
 */
internal class MutablePlaylistTest : MusicLibraryTestBase() {

    @Test
    fun `Mutable audio playlist attributes and operations`() {
        val playlist1 = MutablePlaylist(1, false, "Playlist1")

        assertThat(playlist1.id).isEqualTo(1)
        assertThat(playlist1.isDirectory).isFalse()
        assertThat(playlist1.name).isEqualTo("Playlist1")
        assertThat(playlist1.uniqueId).isEqualTo("1-Playlist1")
        assertThat(playlist1.audioItems).isEmpty()
        assertThat(playlist1.toString()).isEqualTo("MutablePlaylist(id=1, isDirectory=false, name='Playlist1', audioItems=[], playlists=[])")

        playlist1.name = "Modified playlist1"

        assertThat(playlist1.name).isEqualTo("Modified playlist1")
        assertThat(playlist1.uniqueId).isEqualTo("1-Modified playlist1")

        val audioItems = createTestAudioItemsSet(4)

        playlist1.audioItems.addAll(audioItems)

        assertThat(playlist1.audioItems).hasSize(4)
        assertThat(playlist1.audioItemsAllMatch { it.title == "Song title" } ).isFalse()

        val customAudioItem = createTestAudioItem("Song title")

        playlist1.audioItems.add(customAudioItem)

        assertThat(playlist1.audioItems).hasSize(5)
        assertThat(playlist1.audioItemsAnyMatch { it.title == "Song title" } ).isTrue()
        playlist1.audioItems.removeAll(audioItems)
        assertThat(playlist1.audioItems).hasSize(1)
        assertThat(playlist1.audioItemsAllMatch { it.title == "Song title" } ).isTrue()

        val playlist2 = MutablePlaylist(1, false, "Modified playlist1", listOf(customAudioItem))

        assertThat(playlist1).isEqualTo(playlist2)
        assertThat(playlist1).isEquivalentAccordingToCompareTo(playlist2)
        playlist1.audioItems.clear()
    }

    @Test
    fun `Mutable audio directory attributes and operations`() {
        val directory1 = MutablePlaylist(1, true,"Directory1")

        assertThat(directory1.isDirectory).isTrue()
        assertThat(directory1.playlists).isEmpty()
        assertThat(directory1.audioItems).isEmpty()
        assertThat(directory1.toString()).isEqualTo("MutablePlaylist(id=1, isDirectory=true, name='Directory1', audioItems=[], playlists=[])")

        val audioItems = createTestAudioItemsSet(5)
        val p1 = MutablePlaylist(10, true, "p1", audioItems = audioItems)
        val p2 = MutablePlaylist(11, true, "p2")
        val d1 = MutablePlaylist(12, true, "d1", audioItems = listOf(createTestAudioItem("One")))

        directory1.playlists.addAll(listOf(p1, p2, d1))
        assertThat(directory1.playlists).hasSize(3)
        assertThat(directory1.audioItems).isEmpty()
        assertThat(directory1.audioItemsRecursive).hasSize(6)
        assertThat(directory1.playlists.contains(d1)).isTrue()

        d1.audioItems.clear()
        p1.audioItems.clear()
        assertThat(directory1.audioItems).isEmpty()
        assertThat(directory1.audioItemsRecursive).isEmpty()

        directory1.playlists.clear()

        val directory2 = MutablePlaylist(1, true, "Directory1")

        assertThat(directory1).isEqualTo(directory2)
    }

    @Test
    @DisplayName("Export to M3u file")
    fun exportToM3uFileTest() {
    }
}