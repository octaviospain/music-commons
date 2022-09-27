package net.transgressoft.commons.music.playlist

import com.google.common.truth.Truth.assertThat
import net.transgressoft.commons.music.MusicLibraryTestBase
import net.transgressoft.commons.music.audio.AudioItem
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

internal class MutablePlaylistDirectoryTest : MusicLibraryTestBase() {

    @Test
    fun `Mutable audio directory attributes and operations`() {
        val directory1 = MutablePlaylistDirectory<AudioItem, AudioPlaylist<AudioItem>>(1, "Directory1")

        assertThat(directory1.isDirectory).isTrue()
        assertThat(directory1.descendantPlaylists()).isEmpty()
        assertThat(directory1.toString()).isEqualTo("MutablePlaylistDirectory{id=1, name=Directory1, descendantPlaylists=0, audioItems=0}")

        val audioItems = createTestAudioItemsSet(5)
        val p1 = MutablePlaylist(10, "p1", audioItems)
        val p2 = MutablePlaylist<AudioItem>(11, "p2")
        val d1 = MutablePlaylistDirectory(12, "d1", listOf(createTestAudioItem("One")))

        directory1.addPlaylists(p1, p2, d1)
        assertThat(directory1.descendantPlaylists()).hasSize(3)
        assertThat(directory1.containsPlaylist(d1)).isTrue()

        d1.clearAudioItems()
        assertThat(d1.audioItems()).isEmpty()

        val directory2 = MutablePlaylistDirectory<AudioItem, AudioPlaylist<AudioItem>>(1, "Directory1")

        assertThat(directory1).isEqualTo(directory2)
    }
}