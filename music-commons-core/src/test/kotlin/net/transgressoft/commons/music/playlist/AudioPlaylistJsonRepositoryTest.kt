package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.AudioItemRepository
import net.transgressoft.commons.music.audio.AudioItemTestUtil.arbitraryAudioItem
import net.transgressoft.commons.music.playlist.AudioPlaylistTestUtil.asJsonKeyValues
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrowMessage
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import org.mockito.ArgumentMatchers.*
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.time.Duration
import java.util.*
import kotlin.time.Duration.Companion.milliseconds

internal class AudioPlaylistJsonRepositoryTest : StringSpec({

    lateinit var jsonFile: File
    lateinit var audioPlaylistRepository: AudioPlaylistRepository<AudioItem, ReactiveAudioPlaylist>

    beforeEach {
        jsonFile = tempfile("playlistRepository-test", ".json").also { it.deleteOnExit() }
        audioPlaylistRepository = AudioPlaylistJsonRepository("Playlists", jsonFile)
    }

    "Repository serializes itself to file when playlists are added" {
        val rockAudioItem = arbitraryAudioItem { title = "50s Rock hit 1" }.next()
        val rockAudioItems = listOf(rockAudioItem)
        val rock = audioPlaylistRepository.createPlaylist("Rock", rockAudioItems)

        val rockFavAudioItem = arbitraryAudioItem { title = "Rock fav" }.next()
        val rockFavoritesAudioItems = listOf(rockFavAudioItem)
        val rockFavorites = audioPlaylistRepository.createPlaylist("Rock favorites", rockFavoritesAudioItems)

        audioPlaylistRepository.movePlaylist(rockFavorites.name, rock.name)

        audioPlaylistRepository.findById(rock.id) shouldBePresent { updatedRock ->
            updatedRock.playlists.shouldContainOnly(rockFavorites)
            updatedRock.id shouldBe rock.id
            updatedRock.name shouldBe "Rock"
            updatedRock.audioItems.shouldContainExactly(rockAudioItems)
            updatedRock.playlists.shouldContainExactly(rockFavorites)
        }

        val json = listOf(rock, rockFavorites).asJsonKeyValues()

        eventually(100.milliseconds) { jsonFile.readText().shouldEqualJson(json) }
    }

    "Existing repository loads from file" {
        val audioItem = arbitraryAudioItem { id = 453374921 }.next()
        jsonFile.writeText(
            listOf(ImmutablePlaylist(1, true, "Rock", listOf(audioItem)))
                .asJsonKeyValues()
        )

        val audioItemRepository = mock<AudioItemRepository<AudioItem>> {
            whenever(it.findById(eq(453374921))).thenReturn(Optional.of(audioItem))
        }

        audioPlaylistRepository = AudioPlaylistJsonRepository("Playlists", jsonFile, audioItemRepository)

        audioPlaylistRepository.size() shouldBe 1
        audioPlaylistRepository.contains { it.id == 1 && it.isDirectory && it.name == "Rock" && it.audioItems == listOf(audioItem) && it.playlists.isEmpty() } shouldBe true
    }

    // ├──Best hits
    // │  ├──50s
    // │  │  ├──Rock
    // │  │  │  ├──:50s Rock hit 1
    // │  │  │  └──:50s Rock hit 2 my fav
    // │  │  ├──Pop
    // │  │  ├──:50s hit 1
    // │  │  └──:50s favorite song
    // │  └──60s
    // └──This weeks' favorites songs
    "Mixed playlists hierarchy structure and audio items search" {
        val rockAudioItems = listOf(
            arbitraryAudioItem { title = "50s Rock hit 1"; duration = Duration.ofSeconds(60) }.next(),
            arbitraryAudioItem { title = "50s Rock hit 2 my fav"; duration = Duration.ofSeconds(230) }.next()
        )
        val rock = audioPlaylistRepository.createPlaylist("Rock", rockAudioItems)

        audioPlaylistRepository.findByName(rock.name) shouldBePresent { it shouldBe rock }
        val playlistsThatContainsAllAudioItemsWith50sInTitle =
            audioPlaylistRepository.search { it.audioItemsAllMatch { audioItem -> audioItem.title.contains("50s") } }
        playlistsThatContainsAllAudioItemsWith50sInTitle.shouldContainOnly(rock)

        val pop = audioPlaylistRepository.createPlaylist("Pop")
        audioPlaylistRepository.size() shouldBe 2
        audioPlaylistRepository.numberOfPlaylists() shouldBe 2

        val fifties = audioPlaylistRepository.createPlaylistDirectory("50s")
        audioPlaylistRepository.addPlaylistToDirectory(rock, fifties.name)
        audioPlaylistRepository.addPlaylistToDirectory(pop.name, fifties.name)
        audioPlaylistRepository.findByName(fifties.name) shouldBePresent { it.playlists shouldContainExactly setOf(pop, rock) }
        audioPlaylistRepository.numberOfPlaylistDirectories() shouldBe 1

        val sixties = audioPlaylistRepository.createPlaylistDirectory("60s")
        sixties.playlists.isEmpty() shouldBe true
        audioPlaylistRepository.numberOfPlaylistDirectories() shouldBe 2
        audioPlaylistRepository.findByUniqueId("D-" + sixties.name) shouldBePresent { it shouldBe sixties }

        val bestHits = audioPlaylistRepository.createPlaylistDirectory("Best hits")
        audioPlaylistRepository.addPlaylistsToDirectory(setOf(fifties, sixties), bestHits.name)
        bestHits.playlists.isEmpty() shouldBe false
        audioPlaylistRepository.findByName(bestHits.name) shouldBePresent { it.playlists shouldContainExactly setOf(fifties, sixties) }

        val thisWeeksFavorites = audioPlaylistRepository.createPlaylist("This weeks' favorites songs")
        audioPlaylistRepository.search { it.name.contains("favorites") }.shouldContainExactly(thisWeeksFavorites)
        audioPlaylistRepository.size() shouldBe 6
        audioPlaylistRepository.addOrReplaceAll(setOf(bestHits, thisWeeksFavorites))
        audioPlaylistRepository.size() shouldBe 6
        audioPlaylistRepository.search { it.isDirectory.not() } shouldContainExactly setOf(rock, pop, thisWeeksFavorites)

        audioPlaylistRepository.search { it.isDirectory } shouldContainExactly setOf(fifties, sixties, bestHits)

        val fiftiesItems = listOf(
            arbitraryAudioItem { title = "50s hit"; duration = Duration.ofSeconds(30) }.next(),
            arbitraryAudioItem { title = "50s favorite song"; duration = Duration.ofSeconds(120) }.next()
        )

        audioPlaylistRepository.addAudioItemToPlaylist(fiftiesItems[0], fifties.name)
        audioPlaylistRepository.addAudioItemsToPlaylist(fiftiesItems, fifties.name)
        val playlistsThatContainsAnyAudioItemsWithHitInTitle =
            audioPlaylistRepository.search { it.audioItemsAnyMatch { audioItem -> audioItem.title.contains("hit") } }
        playlistsThatContainsAnyAudioItemsWithHitInTitle shouldContainExactly setOf(rock, fifties)
        val playlistsThatContainsAudioItemsWithDurationBelow60 = audioPlaylistRepository.search {
            it.audioItemsAnyMatch { audioItem: AudioItem -> audioItem.duration <= Duration.ofSeconds(60) }
        }

        playlistsThatContainsAudioItemsWithDurationBelow60 shouldContainExactly setOf(rock, fifties)

        audioPlaylistRepository.removeAudioItemFromPlaylist(fiftiesItems[0], fifties.name)
        audioPlaylistRepository.removeAudioItemFromPlaylist(fiftiesItems[1].id, fifties.name)

        audioPlaylistRepository.findById(fifties.id) shouldBePresent { it.audioItems.isEmpty() shouldBe true }
        audioPlaylistRepository.runForAll { it.removeAudioItems(rockAudioItems) }
        audioPlaylistRepository.findById(rock.id) shouldBePresent { it.audioItems.isEmpty() shouldBe true }

        audioPlaylistRepository.clear()
        audioPlaylistRepository.isEmpty shouldBe true
    }

    // ├──Best hits
    // │  └──50s
    // │     ├──Rock
    // │     └──Pop
    // └──Selection of playlists
    "Move playlists in the hierarchy" {
        val rock = audioPlaylistRepository.createPlaylist("Rock")
        audioPlaylistRepository.findByName(rock.name) shouldBePresent { it shouldBe rock }
        val pop = audioPlaylistRepository.createPlaylist("Pop")
        val fifties = audioPlaylistRepository.createPlaylistDirectory("50s")
        audioPlaylistRepository.addPlaylistsToDirectory(setOf(rock.name, pop.name), fifties.name)
        val bestHits = audioPlaylistRepository.createPlaylistDirectory("Best hits")
        audioPlaylistRepository.addPlaylistsToDirectory(setOf(fifties.name), bestHits.name)
        val selection = audioPlaylistRepository.createPlaylistDirectory("Selection of playlists")
        audioPlaylistRepository.size() shouldBe 5

        selection.addPlaylist(rock)
        // same result as doing
        // audioPlaylistRepository.movePlaylist(rock.name, selection.name)

        // ├──Best hits
        // │  └──50s
        // │     └──Pop
        // └──Selection of playlists
        //    └──Rock

        audioPlaylistRepository.size() shouldBe 5
        audioPlaylistRepository.findByName(selection.name) shouldBePresent { it.playlists.shouldContainOnly(rock) }
        audioPlaylistRepository.findById(fifties.id) shouldBePresent { it.playlists.shouldNotContain(rock) }
        audioPlaylistRepository.findByName(bestHits.name) shouldBePresent { it.playlists.shouldContainOnly(fifties) }

        eventually(100.milliseconds) {
            jsonFile.readText().shouldEqualJson(listOf(rock, pop, fifties, bestHits, selection).asJsonKeyValues())
        }

        // --

        audioPlaylistRepository.movePlaylist(selection.name, fifties.name)
        // same result as doing
        // fifties.addPlaylist(selection)

        // └──Best hits
        //    └──50s
        //       ├──Pop
        //       └──Selection of playlists
        //          └──Rock

        audioPlaylistRepository.size() shouldBe 5
        audioPlaylistRepository.findByName(selection.name) shouldBePresent { it.playlists.shouldContainOnly(rock) }
        audioPlaylistRepository.findByName(fifties.name) shouldBePresent { it.playlists shouldContainExactly setOf(pop, selection) }

        eventually(100.milliseconds) { jsonFile.readText().shouldEqualJson(listOf(rock, pop, fifties, bestHits, selection).asJsonKeyValues()) }

        audioPlaylistRepository.removeAll(setOf(bestHits))

        audioPlaylistRepository.size() shouldBe 0
        audioPlaylistRepository.isEmpty shouldBe true
        bestHits.playlists.shouldContainOnly(fifties)
        fifties.playlists shouldContainExactly setOf(pop, selection)
        selection.playlists.shouldContainOnly(rock)
        rock.playlists.isEmpty() shouldBe true
        pop.playlists.isEmpty() shouldBe true

        eventually(100.milliseconds) { jsonFile.readText() shouldBe "{}"}
    }

    "Removing playlist directory from repository is recursive and changes reflected on playlists" {
        val pop = audioPlaylistRepository.createPlaylist("Pop")
        val fifties = audioPlaylistRepository.createPlaylistDirectory("50s").also { it.addPlaylist(pop) }
        val bestHits = audioPlaylistRepository.createPlaylistDirectory("Best hits").also { it.addPlaylist(fifties) }
        val rock = audioPlaylistRepository.createPlaylist("Rock")
        val selection = audioPlaylistRepository.createPlaylistDirectory("Selection of playlists").also { it.addPlaylist(rock) }

        // ├──Best hits
        // │  └──50s
        // │     └──Pop
        // └──Selection of playlists
        //    └──Rock

        audioPlaylistRepository.size() shouldBe 5
        audioPlaylistRepository.findByName(selection.name) shouldBePresent { it.playlists.shouldContainOnly(rock) }
        audioPlaylistRepository.findById(fifties.id) shouldBePresent { it.playlists.shouldNotContain(rock) }
        audioPlaylistRepository.findByName(bestHits.name) shouldBePresent { it.playlists.shouldContainOnly(fifties) }

        audioPlaylistRepository.removePlaylistFromDirectory(fifties.name, bestHits.name)

        // ├──Best hits
        // └──Selection of playlists
        //    └──Rock

        audioPlaylistRepository.size() shouldBe 3
        audioPlaylistRepository.findByName(pop.name).isEmpty shouldBe true
        audioPlaylistRepository.findByUniqueId(fifties.uniqueId).isEmpty shouldBe true
        bestHits.playlists.isEmpty() shouldBe true
        fifties.playlists.shouldContainOnly(pop)

        audioPlaylistRepository.removePlaylistFromDirectory(rock, selection.name)

        // ├──Best hits
        // └──Selection of playlists

        audioPlaylistRepository.size() shouldBe 2
        selection.playlists.isEmpty() shouldBe true
    }

    "Create playlists with existing name" {
        val newPlaylistDirectory = audioPlaylistRepository.createPlaylistDirectory("New playlist")

        shouldThrowMessage("Playlist with name 'New playlist' already exists") { audioPlaylistRepository.createPlaylistDirectory("New playlist") }
        audioPlaylistRepository.size() shouldBe 1

        shouldThrowMessage("Playlist with name 'New playlist' already exists") { audioPlaylistRepository.createPlaylist("New playlist") }
        audioPlaylistRepository.size() shouldBe 1

        audioPlaylistRepository.remove(newPlaylistDirectory) shouldBe true
        audioPlaylistRepository.isEmpty shouldBe true
    }
})