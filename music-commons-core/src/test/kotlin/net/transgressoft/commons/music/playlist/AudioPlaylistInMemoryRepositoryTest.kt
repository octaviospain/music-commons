package net.transgressoft.commons.music.playlist

import com.google.common.truth.Truth.assertThat
import net.transgressoft.commons.music.MusicLibraryTestBase
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.AudioItemAttribute.DURATION
import net.transgressoft.commons.music.audio.AudioItemAttribute.TITLE
import net.transgressoft.commons.music.playlist.PlaylistAttribute.NAME
import net.transgressoft.commons.query.RepositoryException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.Duration
import java.util.*

internal class AudioPlaylistInMemoryRepositoryTest : MusicLibraryTestBase() {
    lateinit var audioPlaylistRepository: AudioPlaylistRepository<AudioItem, AudioPlaylist<AudioItem>, AudioPlaylistDirectory<AudioItem, AudioPlaylist<AudioItem>>>

    // ├──Best hits
    // │  ├──50s
    // │  │  ├──Rock
    // │  │  │  ├──:50s Rock hit 1
    // │  │  │  └──:50s Rock hit 2 my fav
    // │  │  ├──Pop
    // │  │  ├──:50s hit 1
    // │  │  └──:50s favorite song
    // │  └──60s favorites
    // └──This weeks' favorites songs
    @Test
    fun `Mixed playlists hierarchy structure and audio items search`() {
        audioPlaylistRepository = AudioPlaylistInMemoryRepository()

        val rockAudioItems = listOf(
            createTestAudioItem("50s Rock hit 1", Duration.ofSeconds(60)),
            createTestAudioItem("50s Rock hit 2 my fav", Duration.ofSeconds(230))
        )
        var rock = audioPlaylistRepository.createPlaylist("Rock", rockAudioItems)
        assertThat(audioPlaylistRepository.findSinglePlaylistByName(rock.name).orElseThrow()).isEqualTo(rock)
        val playlistsThatContainsAllAudioItemsWith50sInTitle =
            audioPlaylistRepository.search(PlaylistAttribute.containsAllAudioItemsMatching(TITLE.contains("50s")))
        assertThat(playlistsThatContainsAllAudioItemsWith50sInTitle).containsExactly(rock)

        val pop = audioPlaylistRepository.createPlaylist("Pop")
        assertThat(audioPlaylistRepository.size()).isEqualTo(2)
        assertThat(audioPlaylistRepository.numberOfPlaylists()).isEqualTo(2)

        var fifties = audioPlaylistRepository.createPlaylistDirectory("50s")
        audioPlaylistRepository.addPlaylistsToDirectory(setOf(pop, rock), fifties)
        assertThat(audioPlaylistRepository.findSingleDirectoryByName(fifties.name).orElseThrow().descendantPlaylists())
            .containsExactly(pop, rock)
        assertThat(audioPlaylistRepository.numberOfPlaylistDirectories()).isEqualTo(1)

        val sixties = audioPlaylistRepository.createPlaylistDirectory("60s")
        assertThat(sixties.descendantPlaylists()).isEmpty()
        assertThat(audioPlaylistRepository.numberOfPlaylistDirectories()).isEqualTo(2)
        assertThat(audioPlaylistRepository.findByUniqueId(sixties.id.toString() + "-D-" + sixties.name)).isEqualTo(Optional.of(sixties))

        val bestHits = audioPlaylistRepository.createPlaylistDirectory("Best hits")
        audioPlaylistRepository.addPlaylistsToDirectory(java.util.Set.of(fifties, sixties), bestHits)
        assertThat(bestHits.descendantPlaylists()).isEmpty() // Fails because bestHits is an old immutable copy
        assertThat(audioPlaylistRepository.findSingleDirectoryByName(bestHits.name).orElseThrow().descendantPlaylists())
            .containsExactly(fifties, sixties) // bestHits is an updated immutable copy

        val thisWeeksFavorites = audioPlaylistRepository.createPlaylist("This weeks' favorites songs")
        assertThat(audioPlaylistRepository.search(NAME.contains("favorites"))).containsExactly(thisWeeksFavorites)
        assertThat(audioPlaylistRepository.size()).isEqualTo(6)
        audioPlaylistRepository.addOrReplaceAll(java.util.Set.of(bestHits, thisWeeksFavorites))
        assertThat(audioPlaylistRepository.size()).isEqualTo(6)
        assertThat(audioPlaylistRepository.search(PlaylistAttribute.isNotDirectory)).containsExactly(rock, pop, thisWeeksFavorites)
        assertThat(audioPlaylistRepository.search(PlaylistAttribute.isDirectory)).containsExactly(fifties, sixties, bestHits)

        rock = audioPlaylistRepository.findById(rock.id).orElseThrow()
        fifties = audioPlaylistRepository.findSingleDirectoryByName(fifties.name).orElseThrow()
        val fiftiesItems = java.util.List.of(
            createTestAudioItem("50s hit", Duration.ofSeconds(30)),
            createTestAudioItem("50s favorite song", Duration.ofSeconds(120))
        )

        audioPlaylistRepository.addAudioItemsToPlaylist(fiftiesItems, fifties)
        val playlistsThatContainsAnyAudioItemsWithHitInTitle =
            audioPlaylistRepository.search(PlaylistAttribute.containsAnyAudioItemsMatching(TITLE.contains("hit")))
        fifties = audioPlaylistRepository.findSingleDirectoryByName(fifties.name).orElseThrow()
        assertThat(playlistsThatContainsAnyAudioItemsWithHitInTitle).containsExactly(rock, fifties)
        val playlistsThatContainsAudioItemsWithDurationBelow60 = audioPlaylistRepository.search(
            PlaylistAttribute.containsAnyAudioItemsMatching(DURATION.isShorterThan(Duration.ofSeconds(61)))
        )
        assertThat(playlistsThatContainsAudioItemsWithDurationBelow60).containsExactly(rock, fifties)

        audioPlaylistRepository.removeAudioItemsFromPlaylist(fiftiesItems, fifties)

        assertThat(audioPlaylistRepository.findById(fifties.id).get().audioItems()).isEmpty()
        audioPlaylistRepository.removeAudioItems(rockAudioItems)
        assertThat(audioPlaylistRepository.findById(rock.id).get().audioItems()).isEmpty()

        audioPlaylistRepository.clear()
        assertThat(audioPlaylistRepository).isEmpty()
    }

    // ├──Best hits
    // │  └──50s
    // │     ├──Rock
    // │     └──Pop
    // └──Selection of playlists
    @Test
    fun `Move playlists in the hierarchy`() {
        audioPlaylistRepository = AudioPlaylistInMemoryRepository()

        val rock = audioPlaylistRepository.createPlaylist("Rock")
        assertThat(audioPlaylistRepository.findSingleByAttribute(NAME, rock.name).orElseThrow()).isEqualTo(rock)
        val pop = audioPlaylistRepository.createPlaylist("Pop")
        var fifties = audioPlaylistRepository.createPlaylistDirectory("50s")
        audioPlaylistRepository.addPlaylistsToDirectory(setOf(rock, pop), fifties)
        val bestHits = audioPlaylistRepository.createPlaylistDirectory("Best hits")
        audioPlaylistRepository.addPlaylistsToDirectory(setOf(fifties), bestHits)
        var selection = audioPlaylistRepository.createPlaylistDirectory("Selection of playlists")
        assertThat(audioPlaylistRepository.size()).isEqualTo(5)

        audioPlaylistRepository.movePlaylist(rock, selection)

        // ├──Best hits
        // │  └──50s
        // │     └──Pop
        // └──Selection of playlists
        //    └──Rock

        assertThat(audioPlaylistRepository.size()).isEqualTo(5)
        assertThat(audioPlaylistRepository.findSingleDirectoryByName(selection.name).orElseThrow().descendantPlaylists()).containsExactly(rock)
        assertThat(audioPlaylistRepository.findSingleDirectoryByName(fifties.name).orElseThrow().descendantPlaylists()).doesNotContain(rock)

        // --

        fifties = audioPlaylistRepository.findSingleDirectoryByName(fifties.name).orElseThrow()
        selection = audioPlaylistRepository.findSingleDirectoryByName(selection.name).orElseThrow()

        audioPlaylistRepository.movePlaylist(selection, fifties)

        // └──Best hits
        //    └──50s
        //       ├──Pop
        //       └──Selection of playlists
        //          └──Rock

        assertThat(audioPlaylistRepository.size()).isEqualTo(5)
        assertThat(audioPlaylistRepository.findSingleDirectoryByName(selection.name).orElseThrow().descendantPlaylists()).containsExactly(rock)
        assertThat(audioPlaylistRepository.findSingleDirectoryByName(fifties.name).orElseThrow().descendantPlaylists()).containsExactly(pop, selection)
    }

    @Test
    fun `Create playlists with existing name`() {
        audioPlaylistRepository = AudioPlaylistInMemoryRepository()

        val newPlaylistDirectory = audioPlaylistRepository.createPlaylistDirectory("New playlist")
        assertThat(assertThrows(RepositoryException::class.java) { audioPlaylistRepository.createPlaylistDirectory("New playlist") })
            .hasMessageThat().isEqualTo("Playlist with name 'New playlist' already exists")

        val newPlaylist: AudioPlaylist<AudioItem> = audioPlaylistRepository.createPlaylist("New playlist")
        assertThat(assertThrows(RepositoryException::class.java) { audioPlaylistRepository.createPlaylist("New playlist") })
            .hasMessageThat().isEqualTo("Playlist with name 'New playlist' already exists")
        assertThat(audioPlaylistRepository.size()).isEqualTo(2)
        assertThat(audioPlaylistRepository.findAllByName("New playlist")).containsExactly(newPlaylistDirectory, newPlaylist)

        audioPlaylistRepository.movePlaylist(newPlaylist, newPlaylistDirectory)

        assertThat(audioPlaylistRepository.size()).isEqualTo(2)
        assertThat(audioPlaylistRepository.findByUniqueId(newPlaylist.id.toString() + "-New playlist").orElseThrow()).isEqualTo(newPlaylist)
        assertThat(audioPlaylistRepository.findByAttribute(NAME, "New playlist")).containsExactly(newPlaylist, newPlaylistDirectory)
        assertThat(audioPlaylistRepository.findAllByName("New playlist")).containsExactly(newPlaylistDirectory, newPlaylist)
        assertThat(audioPlaylistRepository.removeAll(setOf(newPlaylist, newPlaylistDirectory))).isTrue()
        assertThat(audioPlaylistRepository.isEmpty).isTrue()
    }

    @Test
    fun `Add playlists not created with the repository`() {
        audioPlaylistRepository = AudioPlaylistInMemoryRepository()
        audioPlaylistRepository.createPlaylist("Best hits")
        audioPlaylistRepository.createPlaylistDirectory("Nina Simone discography")
        assertThat(audioPlaylistRepository).hasSize(2)

        val bestHits = mock<MutablePlaylist<AudioItem>> {
            on { name } doReturn "Best hits"
            on { isDirectory } doReturn false
        }
        val rock = mock<MutablePlaylist<AudioItem>> {
            on { name } doReturn "Best hits - Rock"
            on { isDirectory } doReturn false
        }
        audioPlaylistRepository.addOrReplaceAll(setOf(bestHits, rock))
        assertThat(audioPlaylistRepository).hasSize(3)

        val ninaSimoneDiscography = mock<ImmutablePlaylistDirectory<AudioItem, AudioPlaylist<AudioItem>>> {
            on { name } doReturn "Nina Simone discography"
            on { isDirectory } doReturn true
        }
        val revolver = mock<MutablePlaylist<AudioItem>> {
            on { name } doReturn "Revolver"
            on { isDirectory } doReturn false
        }
        val beatlesDiscography = mock<ImmutablePlaylistDirectory<AudioItem, AudioPlaylist<AudioItem>>> {
            on { name } doReturn "The Beatles' discography"
            on { isDirectory } doReturn true
            on { descendantPlaylists() } doReturn setOf(revolver)
        }
        audioPlaylistRepository.addOrReplaceAll(setOf(ninaSimoneDiscography, beatlesDiscography))

        assertThat(audioPlaylistRepository).hasSize(5)
        assertThat(audioPlaylistRepository.findSingleDirectoryByName(beatlesDiscography.name).orElseThrow().descendantPlaylists()).isNotEmpty()
    }
}