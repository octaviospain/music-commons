package net.transgressoft.commons.music.playlist

import com.google.common.truth.Truth.assertThat
import net.transgressoft.commons.music.MusicLibraryTestBase
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.ImmutableAudioItem
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Scanner
import kotlin.io.path.exists

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

        val audioItems = createTestAudioItemsList(4)

        playlist1.audioItems.addAll(audioItems)

        assertThat(playlist1.audioItems).hasSize(4)
        assertThat(playlist1.audioItemsAllMatch { it.title == "Song title" }).isFalse()

        val customAudioItem = createTestAudioItem("Song title")

        playlist1.audioItems.add(customAudioItem)

        assertThat(playlist1.audioItems).hasSize(5)
        assertThat(playlist1.audioItemsAnyMatch { it.title == "Song title" }).isTrue()
        playlist1.audioItems.removeAll(audioItems)
        assertThat(playlist1.audioItems).hasSize(1)
        assertThat(playlist1.audioItemsAllMatch { it.title == "Song title" }).isTrue()

        val playlist2 = MutablePlaylist(1, false, "Modified playlist1", listOf(customAudioItem))

        assertThat(playlist1).isEqualTo(playlist2)
        assertThat(playlist1).isEquivalentAccordingToCompareTo(playlist2)
        playlist1.audioItems.clear()
    }

    @Test
    fun `Mutable audio directory attributes and operations`() {
        val directory1 = MutablePlaylist(1, true, "Directory1")

        assertThat(directory1.isDirectory).isTrue()
        assertThat(directory1.playlists).isEmpty()
        assertThat(directory1.audioItems).isEmpty()
        assertThat(directory1.toString()).isEqualTo("MutablePlaylist(id=1, isDirectory=true, name='Directory1', audioItems=[], playlists=[])")

        val audioItems = createTestAudioItemsList(5)
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
    @DisplayName("Export to .m3u file")
    fun exportToM3uFileTest() {
        val tempDirectory = Files.createTempDirectory("playlist-export-test")
        tempDirectory.toFile().deleteOnExit()
        assertThat(tempDirectory.exists()).isTrue()

        val numberOfAudioItems = 5
        val randomQueenPlaylist = randomQueenAudioItems(tempDirectory, size = numberOfAudioItems)
        val aNightAtTheOperaPlaylist = randomQueenAudioItems(tempDirectory, "A night at the opera", numberOfAudioItems)
        val aKindOfMagicPlaylist = randomQueenAudioItems(tempDirectory, "A kind of magic", numberOfAudioItems)
        val playlists = setOf(aNightAtTheOperaPlaylist, aKindOfMagicPlaylist)
        val playlist = ImmutablePlaylist(1, true, "Queen", randomQueenPlaylist.audioItems, playlists)
        assertThat(playlist.audioItems).containsExactlyElementsIn(randomQueenPlaylist.audioItems)
        assertThat(playlist.playlists).containsExactly(aKindOfMagicPlaylist, aNightAtTheOperaPlaylist)
        assertThat(playlist.audioItemsRecursive).hasSize(numberOfAudioItems * 3)

        val randomQueenPlaylistPath = tempDirectory.resolve("${playlist.name}.m3u")
        playlist.exportToM3uFile(randomQueenPlaylistPath)

        assertThat(tempDirectory.toFile().list()).hasLength(2)
        assertThat(tempDirectory.toFile().listFiles { _, name -> name.equals("${playlist.name}.m3u") }).hasLength(1)
        assertThat(tempDirectory.toFile().listFiles { _, name -> name.equals(playlist.name) }).hasLength(1)

        val aNightAtTheOperaPlaylistPath = tempDirectory.resolve(playlist.name).resolve("${aNightAtTheOperaPlaylist.name}.m3u")
        val aKindOfMagicPlaylistPath = tempDirectory.resolve(playlist.name).resolve("${aKindOfMagicPlaylist.name}.m3u")
        assertThat(aNightAtTheOperaPlaylistPath.exists()).isTrue()
        assertThat(aKindOfMagicPlaylistPath.exists()).isTrue()

        assertPlaylistM3uFile(randomQueenPlaylistPath, playlist)
    }

    private fun randomQueenAudioItems(tempDirectory: Path, albumName: String = "", size: Int) =
        ImmutablePlaylist(-1, false, albumName, buildList {
            for (i in 0 until size) {
                val title = "Song $i - $albumName"
                val artistName = "Queen"
                val duration = Duration.ofSeconds((60 + i).toLong())
                add(
                    (createTestAudioItem(title, artistName) as ImmutableAudioItem)
                        .copy(path = tempDirectory.resolve("$title.mp3"), duration = duration)
                )
            }
        })

    private fun assertPlaylistM3uFile(m3uFilePath: Path, playlist: AudioPlaylist<AudioItem>) {
        Scanner(m3uFilePath).use {scanner ->
            assertThat(scanner.nextLine()).isEqualTo("#EXTM3U")

            if (playlist.isDirectory && playlist.playlists.isNotEmpty()) {
                playlist.playlists.forEach {
                    val containedPlaylistPath = m3uFilePath.parent.resolve(playlist.name).resolve("${it.name}.m3u")
                    assertThat(scanner.nextLine()).isEqualTo(m3uFilePath.parent.relativize(containedPlaylistPath).toString())
                    assertPlaylistM3uFile(containedPlaylistPath, it)
                }
            }
            playlist.audioItems.forEach {
                assertThat(scanner.nextLine()).isEqualTo("#EXTALB: ${it.album.name}")
                assertThat(scanner.nextLine()).isEqualTo("#EXTART: ${it.artist.name}")
                assertThat(scanner.nextLine()).isEqualTo("#EXTINF: ${it.duration.seconds},${it.title}")
                assertThat(scanner.nextLine()).isEqualTo(m3uFilePath.parent.relativize(it.path).toString())
            }
        }
    }
}