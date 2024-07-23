package net.transgressoft.commons.fx.music.playlist

import net.transgressoft.commons.fx.music.audio.FXAudioItemTestUtil.arbitraryAudioItem
import net.transgressoft.commons.fx.music.audio.ObservableAudioItem
import net.transgressoft.commons.fx.music.audio.ObservableAudioItemJsonRepository
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
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.time.Duration
import java.util.*
import kotlin.time.Duration.Companion.milliseconds

class ObservablePlaylistJsonRepositoryTest : StringSpec({

    lateinit var jsonFile: File
    lateinit var observableAudioPlaylistRepository: ObservablePlaylistJsonRepository

    beforeEach {
        jsonFile = tempfile("observablePlaylistRepository-test", ".json").also { it.deleteOnExit() }
        observableAudioPlaylistRepository = ObservablePlaylistJsonRepository("ObservablePlaylists", jsonFile)
    }

    "Repository serializes itself to file when playlists are added" {
        val rockAudioItem = arbitraryAudioItem { title = "50s Rock hit 1" }.next()
        val rockAudioItems = listOf(rockAudioItem)
        val rock = observableAudioPlaylistRepository.createPlaylist("Rock", rockAudioItems)

        val rockFavAudioItem = arbitraryAudioItem { title = "Rock fav" }.next()
        val rockFavoritesAudioItems = listOf(rockFavAudioItem)
        val rockFavorites = observableAudioPlaylistRepository.createPlaylist("Rock favorites", rockFavoritesAudioItems)

        observableAudioPlaylistRepository.movePlaylist(rockFavorites.name, rock.name)

        observableAudioPlaylistRepository.findById(rock.id) shouldBePresent { updatedRock ->
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
        val playlist = mockk<ObservablePlaylist> {
            every { id } returns 1
            every { isDirectory } returns true
            every { name } returns "Rock"
            every { audioItems } returns listOf(audioItem)
            every { playlists } returns emptySet()
        }
        jsonFile.writeText(listOf(playlist).asJsonKeyValues())

        val audioItemRepository = mockk<ObservableAudioItemJsonRepository> {
            every { findById(eq(453374921)) } returns Optional.of(audioItem)
        }

        observableAudioPlaylistRepository = ObservablePlaylistJsonRepository("Playlists", jsonFile, audioItemRepository)

        observableAudioPlaylistRepository.size() shouldBe 1
        observableAudioPlaylistRepository.contains { it.id == 1 && it.isDirectory && it.name == "Rock" && it.audioItems == listOf(audioItem) && it.playlists.isEmpty() } shouldBe true
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
        val rock = observableAudioPlaylistRepository.createPlaylist("Rock", rockAudioItems)

        observableAudioPlaylistRepository.findByName(rock.name) shouldBePresent { it shouldBe rock }
        val playlistsThatContainsAllAudioItemsWith50sInTitle =
            observableAudioPlaylistRepository.search { it.audioItemsAllMatch { audioItem -> audioItem.title.contains("50s") } }
        playlistsThatContainsAllAudioItemsWith50sInTitle.shouldContainOnly(rock)

        val pop = observableAudioPlaylistRepository.createPlaylist("Pop")
        observableAudioPlaylistRepository.size() shouldBe 2
        observableAudioPlaylistRepository.numberOfPlaylists() shouldBe 2

        val fifties = observableAudioPlaylistRepository.createPlaylistDirectory("50s")
        observableAudioPlaylistRepository.addPlaylistToDirectory(rock, fifties.name)
        observableAudioPlaylistRepository.addPlaylistToDirectory(pop.name, fifties.name)
        observableAudioPlaylistRepository.findByName(fifties.name) shouldBePresent { it.playlists shouldContainExactly setOf(pop, rock) }
        observableAudioPlaylistRepository.numberOfPlaylistDirectories() shouldBe 1

        val sixties = observableAudioPlaylistRepository.createPlaylistDirectory("60s")
        sixties.playlists.isEmpty() shouldBe true
        observableAudioPlaylistRepository.numberOfPlaylistDirectories() shouldBe 2
        observableAudioPlaylistRepository.findByUniqueId("D-" + sixties.name) shouldBePresent { it shouldBe sixties }

        val bestHits = observableAudioPlaylistRepository.createPlaylistDirectory("Best hits")
        observableAudioPlaylistRepository.addPlaylistsToDirectory(setOf(fifties, sixties), bestHits.name)
        bestHits.playlists.isEmpty() shouldBe false
        observableAudioPlaylistRepository.findByName(bestHits.name) shouldBePresent { it.playlists shouldContainExactly setOf(fifties, sixties) }

        val thisWeeksFavorites = observableAudioPlaylistRepository.createPlaylist("This weeks' favorites songs")
        observableAudioPlaylistRepository.search { it.name.contains("favorites") }.shouldContainExactly(thisWeeksFavorites)
        observableAudioPlaylistRepository.size() shouldBe 6
        observableAudioPlaylistRepository.addOrReplaceAll(setOf(bestHits, thisWeeksFavorites))
        observableAudioPlaylistRepository.size() shouldBe 6
        observableAudioPlaylistRepository.search { it.isDirectory.not() } shouldContainExactly setOf(rock, pop, thisWeeksFavorites)

        observableAudioPlaylistRepository.search { it.isDirectory } shouldContainExactly setOf(fifties, sixties, bestHits)

        val fiftiesItems = listOf(
            arbitraryAudioItem { title = "50s hit"; duration = Duration.ofSeconds(30) }.next(),
            arbitraryAudioItem { title = "50s favorite song"; duration = Duration.ofSeconds(120) }.next()
        )

        observableAudioPlaylistRepository.addAudioItemToPlaylist(fiftiesItems[0], fifties.name)
        observableAudioPlaylistRepository.addAudioItemsToPlaylist(fiftiesItems, fifties.name)
        val playlistsThatContainsAnyAudioItemsWithHitInTitle =
            observableAudioPlaylistRepository.search { it.audioItemsAnyMatch { audioItem -> audioItem.title.contains("hit") } }
        playlistsThatContainsAnyAudioItemsWithHitInTitle shouldContainExactly setOf(rock, fifties)
        val playlistsThatContainsAudioItemsWithDurationBelow60 = observableAudioPlaylistRepository.search {
            it.audioItemsAnyMatch { audioItem: ObservableAudioItem -> audioItem.duration <= Duration.ofSeconds(60) }
        }

        playlistsThatContainsAudioItemsWithDurationBelow60 shouldContainExactly setOf(rock, fifties)

        observableAudioPlaylistRepository.removeAudioItemFromPlaylist(fiftiesItems[0], fifties.name)
        observableAudioPlaylistRepository.removeAudioItemFromPlaylist(fiftiesItems[1].id, fifties.name)

        observableAudioPlaylistRepository.findById(fifties.id) shouldBePresent { it.audioItems.isEmpty() shouldBe true }
        observableAudioPlaylistRepository.runForAll { it.removeAudioItems(rockAudioItems) }
        observableAudioPlaylistRepository.findById(rock.id) shouldBePresent { it.audioItems.isEmpty() shouldBe true }

        observableAudioPlaylistRepository.clear()
        observableAudioPlaylistRepository.isEmpty shouldBe true
    }

    // ├──Best hits
    // │  └──50s
    // │     ├──Rock
    // │     └──Pop
    // └──Selection of playlists
    "Move playlists in the hierarchy" {
        val rock = observableAudioPlaylistRepository.createPlaylist("Rock")
        observableAudioPlaylistRepository.findByName(rock.name) shouldBePresent { it shouldBe rock }
        val pop = observableAudioPlaylistRepository.createPlaylist("Pop")
        val fifties = observableAudioPlaylistRepository.createPlaylistDirectory("50s")
        observableAudioPlaylistRepository.addPlaylistsToDirectory(setOf(rock.name, pop.name), fifties.name)
        val bestHits = observableAudioPlaylistRepository.createPlaylistDirectory("Best hits")
        observableAudioPlaylistRepository.addPlaylistsToDirectory(setOf(fifties.name), bestHits.name)
        val selection = observableAudioPlaylistRepository.createPlaylistDirectory("Selection of playlists")
        observableAudioPlaylistRepository.size() shouldBe 5

        selection.addPlaylist(rock)
        // same result as doing
        // observableAudioPlaylistRepository.movePlaylist(rock.name, selection.name)

        // ├──Best hits
        // │  └──50s
        // │     └──Pop
        // └──Selection of playlists
        //    └──Rock

        observableAudioPlaylistRepository.size() shouldBe 5
        observableAudioPlaylistRepository.findByName(selection.name) shouldBePresent { it.playlists.shouldContainOnly(rock) }
        observableAudioPlaylistRepository.findById(fifties.id) shouldBePresent { it.playlists.shouldNotContain(rock) }
        observableAudioPlaylistRepository.findByName(bestHits.name) shouldBePresent { it.playlists.shouldContainOnly(fifties) }

        eventually(100.milliseconds) {
            jsonFile.readText().shouldEqualJson(listOf(rock, pop, fifties, bestHits, selection).asJsonKeyValues())
        }

        // --

        observableAudioPlaylistRepository.movePlaylist(selection.name, fifties.name)
        // same result as doing
        // fifties.addPlaylist(selection)

        // └──Best hits
        //    └──50s
        //       ├──Pop
        //       └──Selection of playlists
        //          └──Rock

        observableAudioPlaylistRepository.size() shouldBe 5
        observableAudioPlaylistRepository.findByName(selection.name) shouldBePresent { it.playlists.shouldContainOnly(rock) }
        observableAudioPlaylistRepository.findByName(fifties.name) shouldBePresent { it.playlists shouldContainExactly setOf(pop, selection) }

        eventually(100.milliseconds) { jsonFile.readText().shouldEqualJson(listOf(rock, pop, fifties, bestHits, selection).asJsonKeyValues()) }

        observableAudioPlaylistRepository.removeAll(setOf(bestHits))

        observableAudioPlaylistRepository.size() shouldBe 0
        observableAudioPlaylistRepository.isEmpty shouldBe true
        bestHits.playlists.shouldContainOnly(fifties)
        fifties.playlists shouldContainExactly setOf(pop, selection)
        selection.playlists.shouldContainOnly(rock)
        rock.playlists.isEmpty() shouldBe true
        pop.playlists.isEmpty() shouldBe true

        eventually(100.milliseconds) { jsonFile.readText() shouldBe "{}"}
    }

    "Removing playlist directory from repository is recursive and changes reflected on playlists" {
        val pop = observableAudioPlaylistRepository.createPlaylist("Pop")
        val fifties = observableAudioPlaylistRepository.createPlaylistDirectory("50s").also { it.addPlaylist(pop) }
        val bestHits = observableAudioPlaylistRepository.createPlaylistDirectory("Best hits").also { it.addPlaylist(fifties) }
        val rock = observableAudioPlaylistRepository.createPlaylist("Rock")
        val selection = observableAudioPlaylistRepository.createPlaylistDirectory("Selection of playlists").also { it.addPlaylist(rock) }

        // ├──Best hits
        // │  └──50s
        // │     └──Pop
        // └──Selection of playlists
        //    └──Rock

        observableAudioPlaylistRepository.size() shouldBe 5
        observableAudioPlaylistRepository.findByName(selection.name) shouldBePresent { it.playlists.shouldContainOnly(rock) }
        observableAudioPlaylistRepository.findById(fifties.id) shouldBePresent { it.playlists.shouldNotContain(rock) }
        observableAudioPlaylistRepository.findByName(bestHits.name) shouldBePresent { it.playlists.shouldContainOnly(fifties) }

        observableAudioPlaylistRepository.removePlaylistFromDirectory(fifties.name, bestHits.name)

        // ├──Best hits
        // └──Selection of playlists
        //    └──Rock

        observableAudioPlaylistRepository.size() shouldBe 3
        observableAudioPlaylistRepository.findByName(pop.name).isEmpty shouldBe true
        observableAudioPlaylistRepository.findByUniqueId(fifties.uniqueId).isEmpty shouldBe true
        bestHits.playlists.isEmpty() shouldBe true
        fifties.playlists.shouldContainOnly(pop)

        observableAudioPlaylistRepository.removePlaylistFromDirectory(rock, selection.name)

        // ├──Best hits
        // └──Selection of playlists

        observableAudioPlaylistRepository.size() shouldBe 2
        selection.playlists.isEmpty() shouldBe true
    }

    "Create playlists with existing name" {
        val newPlaylistDirectory = observableAudioPlaylistRepository.createPlaylistDirectory("New playlist")

        shouldThrowMessage("Playlist with name 'New playlist' already exists") { observableAudioPlaylistRepository.createPlaylistDirectory("New playlist") }
        observableAudioPlaylistRepository.size() shouldBe 1

        shouldThrowMessage("Playlist with name 'New playlist' already exists") { observableAudioPlaylistRepository.createPlaylist("New playlist") }
        observableAudioPlaylistRepository.size() shouldBe 1

        observableAudioPlaylistRepository.remove(newPlaylistDirectory) shouldBe true
        observableAudioPlaylistRepository.isEmpty shouldBe true
    }
})

fun ObservablePlaylist.asJsonKeyValue(): String {
    val audioItemsString = buildString {
        append("[")
        audioItems.forEachIndexed { index, it ->
            append(it.id)
            if (index < audioItems.size - 1) {
                append(",")
            }
        }
        append("],")
    }
    val playlistIds = buildString {
        append("[")
        playlists.forEachIndexed { index, it ->
            append(it.id)
            if (index < playlists.size - 1) {
                append(",")
            }
        }
        append("]")
    }
    return """
            "$id": {
                "id": $id,
                "isDirectory": $isDirectory,
                "name": "$name",
                "audioItemIds": $audioItemsString
                "playlistIds": $playlistIds
            }"""
}

fun Collection<ObservablePlaylist>.asJsonKeyValues(): String {
    return buildString {
        append("{")
        this@asJsonKeyValues.forEachIndexed { index, it ->
            append(it.asJsonKeyValue())
            if (index < this@asJsonKeyValues.size - 1) {
                append(",")
            }
        }
        append("}")
    }
}