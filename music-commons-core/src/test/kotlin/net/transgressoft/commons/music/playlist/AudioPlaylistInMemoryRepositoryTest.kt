package net.transgressoft.commons.music.playlist

import com.google.common.truth.Truth.assertThat
import net.transgressoft.commons.music.MusicLibraryTestBase
import net.transgressoft.commons.music.audio.AudioItem
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.*

internal class AudioPlaylistInMemoryRepositoryTest : MusicLibraryTestBase() {
    lateinit var audioPlaylistRepository: AudioPlaylistRepository<AudioItem, AudioPlaylist<AudioItem>>

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
        assertThat(audioPlaylistRepository.findByName(rock.name)).isEqualTo(rock)
        val playlistsThatContainsAllAudioItemsWith50sInTitle =
            audioPlaylistRepository.search { it.audioItemsAllMatch { audioItem -> audioItem.title.contains("50s") } }
        assertThat(playlistsThatContainsAllAudioItemsWith50sInTitle).containsExactly(rock)

        val pop = audioPlaylistRepository.createPlaylist("Pop")
        assertThat(audioPlaylistRepository.size()).isEqualTo(2)
        assertThat(audioPlaylistRepository.numberOfPlaylists()).isEqualTo(2)

        var fifties = audioPlaylistRepository.createPlaylistDirectory("50s")
        audioPlaylistRepository.addPlaylistsToDirectory(setOf(pop, rock), fifties)
        assertThat(audioPlaylistRepository.findByName(fifties.name)?.playlists)
            .containsExactly(pop, rock)
        assertThat(audioPlaylistRepository.numberOfPlaylistDirectories()).isEqualTo(1)

        var sixties = audioPlaylistRepository.createPlaylistDirectory("60s")
        assertThat(sixties.playlists).isEmpty()
        assertThat(audioPlaylistRepository.numberOfPlaylistDirectories()).isEqualTo(2)
        assertThat(audioPlaylistRepository.findByUniqueId(sixties.id.toString() + "-D-" + sixties.name)).isEqualTo(Optional.of(sixties))

        var bestHits = audioPlaylistRepository.createPlaylistDirectory("Best hits")
        audioPlaylistRepository.addPlaylistsToDirectory(setOf(fifties, sixties), bestHits)
        assertThat(bestHits.playlists).isEmpty() // Fails because bestHits is an old immutable copy
        assertThat(audioPlaylistRepository.findByName(bestHits.name)?.playlists)
            .containsExactly(fifties, sixties) // bestHits is an updated immutable copy

        val thisWeeksFavorites = audioPlaylistRepository.createPlaylist("This weeks' favorites songs")
        assertThat(audioPlaylistRepository.search { it.name.contains("favorites") }).containsExactly(thisWeeksFavorites)
        assertThat(audioPlaylistRepository.size()).isEqualTo(6)
        audioPlaylistRepository.addOrReplaceAll(setOf(bestHits, thisWeeksFavorites))
        assertThat(audioPlaylistRepository.size()).isEqualTo(6)
        assertThat(audioPlaylistRepository.search { it.isDirectory.not() }).containsExactly(rock, pop, thisWeeksFavorites)

        fifties = audioPlaylistRepository.findByName(fifties.name)!!
        sixties = audioPlaylistRepository.findByName(sixties.name)!!
        bestHits = audioPlaylistRepository.findByName(bestHits.name)!!
        assertThat(audioPlaylistRepository.search { it.isDirectory }).containsExactly(fifties, sixties, bestHits)

        rock = audioPlaylistRepository.findById(rock.id).get()
        fifties = audioPlaylistRepository.findByName(fifties.name)!!
        val fiftiesItems = listOf(
            createTestAudioItem("50s hit", Duration.ofSeconds(30)),
            createTestAudioItem("50s favorite song", Duration.ofSeconds(120))
        )

        audioPlaylistRepository.addAudioItemsToPlaylist(fiftiesItems, fifties)
        val playlistsThatContainsAnyAudioItemsWithHitInTitle =
            audioPlaylistRepository.search { it.audioItemsAnyMatch { audioItem -> audioItem.title.contains("hit") } }
        fifties = audioPlaylistRepository.findByName(fifties.name)!!
        rock = audioPlaylistRepository.findByName(rock.name)!!
        assertThat(playlistsThatContainsAnyAudioItemsWithHitInTitle).containsExactly(rock, fifties)
        val playlistsThatContainsAudioItemsWithDurationBelow60 = audioPlaylistRepository.search {
            it.audioItemsAnyMatch { audioItem: AudioItem -> audioItem.duration <= Duration.ofSeconds(60) }
        }
        assertThat(playlistsThatContainsAudioItemsWithDurationBelow60).containsExactly(rock, fifties)

        audioPlaylistRepository.removeAudioItemsFromPlaylist(fiftiesItems, fifties)

        assertThat(audioPlaylistRepository.findById(fifties.id).get().audioItems).isEmpty()
        audioPlaylistRepository.removeAudioItems(rockAudioItems)
        assertThat(audioPlaylistRepository.findById(rock.id).get().audioItems).isEmpty()

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
        assertThat(audioPlaylistRepository.findByName(rock.name)).isEqualTo(rock)
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
        assertThat(audioPlaylistRepository.findByName(selection.name)?.playlists).containsExactly(rock)
        assertThat(audioPlaylistRepository.findById(fifties.id).get().playlists).doesNotContain(rock)

        // --

        fifties = audioPlaylistRepository.findByName(fifties.name)!!
        selection = audioPlaylistRepository.findByName(selection.name)!!

        audioPlaylistRepository.movePlaylist(selection, fifties)

        // └──Best hits
        //    └──50s
        //       ├──Pop
        //       └──Selection of playlists
        //          └──Rock

        assertThat(audioPlaylistRepository.size()).isEqualTo(5)
        assertThat(audioPlaylistRepository.findByName(selection.name)?.playlists).containsExactly(rock)
        assertThat(audioPlaylistRepository.findByName(fifties.name)?.playlists).containsExactly(pop, selection)
    }

    @Test
    fun `Create playlists with existing name`() {
        audioPlaylistRepository = AudioPlaylistInMemoryRepository()

        val newPlaylistDirectory = audioPlaylistRepository.createPlaylistDirectory("New playlist")
        assertThat(assertThrows(AudioPlaylistRepositoryException::class.java) { audioPlaylistRepository.createPlaylistDirectory("New playlist") })
            .hasMessageThat().isEqualTo("Playlist with name 'New playlist' already exists")
        assertThat(audioPlaylistRepository.size()).isEqualTo(1)

        assertThat(assertThrows(AudioPlaylistRepositoryException::class.java) { audioPlaylistRepository.createPlaylist("New playlist") })
            .hasMessageThat().isEqualTo("Playlist with name 'New playlist' already exists")
        assertThat(audioPlaylistRepository.size()).isEqualTo(1)

        assertThat(audioPlaylistRepository.remove(newPlaylistDirectory)).isTrue()
        assertThat(audioPlaylistRepository.isEmpty).isTrue()
    }

    @Test
    fun `Add playlists not created with the repository`() {
        audioPlaylistRepository = AudioPlaylistInMemoryRepository()
        audioPlaylistRepository.createPlaylist("Best hits")
        audioPlaylistRepository.createPlaylistDirectory("Nina Simone discography")
        assertThat(audioPlaylistRepository).hasSize(2)

        val bestHits = MutablePlaylist<AudioItem>(9, false,"Best hits")
        val rock = MutablePlaylist<AudioItem>(10, false, "Best hits - Rock")

        audioPlaylistRepository.addOrReplaceAll(setOf(bestHits, rock))
        assertThat(audioPlaylistRepository).hasSize(3)

        val ninaSimoneDiscography = ImmutablePlaylist<AudioItem>(11, true, "Nina Simone discography")
        val revolver = MutablePlaylist<AudioItem>(12, false, "Revolver")
        val beatlesDiscography = ImmutablePlaylist<AudioItem>(13, true, "The Beatles' discography", emptyList(), setOf(revolver))

        audioPlaylistRepository.addOrReplaceAll(setOf(ninaSimoneDiscography, beatlesDiscography))

        assertThat(audioPlaylistRepository).hasSize(5)
        assertThat(audioPlaylistRepository.findByName(beatlesDiscography.name)?.playlists).isNotEmpty()
    }
}