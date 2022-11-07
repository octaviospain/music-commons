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
        val playlist1 = MutablePlaylist<AudioItem>(1, false, "Playlist1")

        assertThat(playlist1.id).isEqualTo(1)
        assertThat(playlist1.isDirectory).isFalse()
        assertThat(playlist1.name).isEqualTo("Playlist1")
        assertThat(playlist1.uniqueId).isEqualTo("1-Playlist1")
        assertThat(playlist1.audioItems).isEmpty()
        assertThat(playlist1.toString()).isEqualTo("ImmutablePlaylist(id=1, isDirectory=false, name='Playlist1', audioItems=[], playlists=[])")

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

        val playlist2 = MutablePlaylist<AudioItem>(1, false, "Modified playlist1")

        assertThat(playlist1).isEqualTo(playlist2)
        assertThat(playlist1).isEquivalentAccordingToCompareTo(playlist2)
        playlist1.audioItems.clear()
    }

    @Test
    @DisplayName("Export to M3u file")
    fun exportToM3uFileTest() {
    }
}