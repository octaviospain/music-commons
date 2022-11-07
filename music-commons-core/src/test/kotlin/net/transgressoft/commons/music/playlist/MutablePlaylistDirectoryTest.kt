package net.transgressoft.commons.music.playlist

import com.google.common.truth.Truth.assertThat
import net.transgressoft.commons.music.MusicLibraryTestBase
import net.transgressoft.commons.music.audio.AudioItem
import org.junit.jupiter.api.Test

internal class MutablePlaylistDirectoryTest : MusicLibraryTestBase() {

    @Test
    fun `Mutable audio directory attributes and operations`() {
        val directory1 = MutablePlaylist<AudioItem>(1, true,"Directory1")

        assertThat(directory1.isDirectory).isTrue()
        assertThat(directory1.playlists).isEmpty()
        assertThat(directory1.toString()).isEqualTo("ImmutablePlaylist(id=1, isDirectory=true, name='Directory1', audioItems=[], playlists=[])")

        val audioItems = createTestAudioItemsSet(5)
        val p1 = MutablePlaylist(10, true, "p1", audioItems = audioItems.toMutableList())
        val p2 = MutablePlaylist<AudioItem>(11, true, "p2")
        val d1 = MutablePlaylist(12, true, "d1", audioItems = mutableListOf(createTestAudioItem("One")))

        directory1.playlists.addAll(listOf(p1, p2, d1))
        assertThat(directory1.playlists).hasSize(3)
        assertThat(directory1.playlists.contains(d1)).isTrue()

        d1.audioItems.clear()
        assertThat(d1.audioItems).isEmpty()

        val directory2 = MutablePlaylist<AudioItem>(1, true, "Directory1")

        assertThat(directory1).isEqualTo(directory2)
    }
}