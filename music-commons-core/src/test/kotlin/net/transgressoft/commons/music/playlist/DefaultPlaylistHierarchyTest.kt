package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.event.ReactiveScope
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.AudioLibrary
import net.transgressoft.commons.music.audio.audioItem
import net.transgressoft.commons.persistence.json.JsonFileRepository
import net.transgressoft.commons.persistence.json.JsonRepository
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.throwables.shouldThrowMessage
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.time.Duration
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@ExperimentalCoroutinesApi
internal class DefaultPlaylistHierarchyTest : StringSpec({

    val testDispatcher = UnconfinedTestDispatcher()
    val testScope = CoroutineScope(testDispatcher)
    lateinit var jsonFile: File
    lateinit var jsonFileRepository: JsonRepository<Int, MutableAudioPlaylist>
    lateinit var playlistHierarchy: PlaylistHierarchy<AudioItem, MutableAudioPlaylist>

    beforeSpec {
        ReactiveScope.flowScope = testScope
        ReactiveScope.ioScope = testScope
    }

    beforeEach {
        jsonFile = tempfile("playlistRepository-test", ".json").also { it.deleteOnExit() }
        jsonFileRepository = JsonFileRepository(jsonFile, AudioPlaylistMapSerializer)
        playlistHierarchy = DefaultPlaylistHierarchy(jsonFileRepository)
    }

    afterEach {
        jsonFileRepository.close()
    }

    afterSpec {
        ReactiveScope.resetDefaultFlowScope()
        ReactiveScope.resetDefaultIoScope()
    }

    "Repository serializes itself to file when playlists are added" {
        val rockAudioItem = Arb.audioItem { title = "50s Rock hit 1" }.next()
        val rockAudioItems = listOf(rockAudioItem)
        val rock = playlistHierarchy.createPlaylist("Rock", rockAudioItems)

        val rockFavAudioItem = Arb.audioItem { title = "Rock fav" }.next()
        val rockFavoritesAudioItems = listOf(rockFavAudioItem)
        val rockFavorites = playlistHierarchy.createPlaylist("Rock favorites", rockFavoritesAudioItems)

        playlistHierarchy.movePlaylist(rockFavorites.name, rock.name)

        playlistHierarchy.findById(rock.id) shouldBePresent { updatedRock ->
            updatedRock.playlists.shouldContainOnly(rockFavorites)
            updatedRock.id shouldBe rock.id
            updatedRock.name shouldBe "Rock"
            updatedRock.audioItems.shouldContainExactly(rockAudioItems)
            updatedRock.playlists.shouldContainExactly(rockFavorites)
        }
        testDispatcher.scheduler.advanceUntilIdle()

        val json = listOf(rock, rockFavorites).asJsonKeyValues()
        jsonFile.readText() shouldEqualJson (json)
    }

    "Existing repository loads from file" {
        val audioItem = Arb.audioItem { id = 453374921 }.next()
        jsonFile.writeText(
            listOf(ImmutablePlaylist(1, true, "Rock", listOf(audioItem)))
                .asJsonKeyValues()
        )
        jsonFileRepository = JsonFileRepository(jsonFile, AudioPlaylistMapSerializer)

        val audioLibrary =
            mockk<AudioLibrary<AudioItem>> {
                every { findById(eq(453374921)) } returns Optional.of(audioItem)
            }

        playlistHierarchy = DefaultPlaylistHierarchy(jsonFileRepository, audioLibrary)

        testDispatcher.scheduler.advanceUntilIdle()

        playlistHierarchy.size() shouldBe 1
        playlistHierarchy.contains {
            it.id == 1 && it.isDirectory && it.name == "Rock" && it.audioItems == listOf(audioItem) && it.playlists.isEmpty()
        } shouldBe true
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
        val rockAudioItems =
            listOf(
                Arb.audioItem {
                    title = "50s Rock hit 1"
                    duration = Duration.ofSeconds(60)
                }.next(),
                Arb.audioItem {
                    title = "50s Rock hit 2 my fav"
                    duration = Duration.ofSeconds(230)
                }.next()
            )
        val rock = playlistHierarchy.createPlaylist("Rock", rockAudioItems)

        playlistHierarchy.findByName(rock.name) shouldBePresent { it shouldBe rock }
        val playlistsThatContainsAllAudioItemsWith50sInTitle =
            playlistHierarchy.search { it.audioItemsAllMatch { audioItem -> "50s" in audioItem.title } }
        playlistsThatContainsAllAudioItemsWith50sInTitle.shouldContainOnly(rock)

        val pop = playlistHierarchy.createPlaylist("Pop")
        playlistHierarchy.size() shouldBe 2
        playlistHierarchy.numberOfPlaylists() shouldBe 2

        val fifties = playlistHierarchy.createPlaylistDirectory("50s")
        playlistHierarchy.addPlaylistToDirectory(rock, fifties.name)
        playlistHierarchy.addPlaylistToDirectory(pop.name, fifties.name)
        playlistHierarchy.findByName(fifties.name) shouldBePresent { it.playlists shouldContainExactly setOf(pop, rock) }
        playlistHierarchy.numberOfPlaylistDirectories() shouldBe 1

        val sixties = playlistHierarchy.createPlaylistDirectory("60s")
        sixties.playlists.isEmpty() shouldBe true
        playlistHierarchy.numberOfPlaylistDirectories() shouldBe 2
        playlistHierarchy.findByUniqueId("D-" + sixties.name) shouldBePresent { it shouldBe sixties }

        val bestHits = playlistHierarchy.createPlaylistDirectory("Best hits")
        playlistHierarchy.addPlaylistsToDirectory(setOf(fifties, sixties), bestHits.name)
        bestHits.playlists.isEmpty() shouldBe false
        playlistHierarchy.findByName(bestHits.name) shouldBePresent { it.playlists shouldContainExactly setOf(fifties, sixties) }

        val thisWeeksFavorites = playlistHierarchy.createPlaylist("This weeks' favorites songs")
        playlistHierarchy.search { "favorites" in it.name }.shouldContainExactly(thisWeeksFavorites)
        playlistHierarchy.size() shouldBe 6
        playlistHierarchy.addOrReplaceAll(setOf(bestHits, thisWeeksFavorites))
        playlistHierarchy.size() shouldBe 6
        playlistHierarchy.search { it.isDirectory.not() } shouldContainExactly setOf(rock, pop, thisWeeksFavorites)

        playlistHierarchy.search { it.isDirectory } shouldContainExactly setOf(fifties, sixties, bestHits)

        val fiftiesItems =
            listOf(
                Arb.audioItem {
                    title = "50s hit"
                    duration = Duration.ofSeconds(30)
                }.next(),
                Arb.audioItem {
                    title = "50s favorite song"
                    duration = Duration.ofSeconds(120)
                }.next()
            )

        playlistHierarchy.addAudioItemToPlaylist(fiftiesItems[0], fifties.name)
        playlistHierarchy.addAudioItemsToPlaylist(fiftiesItems, fifties.name)
        val playlistsThatContainsAnyAudioItemsWithHitInTitle =
            playlistHierarchy.search { it.audioItemsAnyMatch { audioItem -> audioItem.title.contains("hit") } }
        playlistsThatContainsAnyAudioItemsWithHitInTitle shouldContainExactly setOf(rock, fifties)
        val playlistsThatContainsAudioItemsWithDurationBelow60 =
            playlistHierarchy.search {
                it.audioItemsAnyMatch { audioItem: AudioItem -> audioItem.duration <= Duration.ofSeconds(60) }
            }

        playlistsThatContainsAudioItemsWithDurationBelow60 shouldContainExactly setOf(rock, fifties)

        playlistHierarchy.removeAudioItemFromPlaylist(fiftiesItems[0], fifties.name)
        every { fiftiesItems[1].title } returns "new title"

        fifties.audioItemsAllMatch { it.title == "new title" } shouldBe true
        fifties.audioItems.find { it.title == "title" }?.shouldBeEqual(fiftiesItems[1])

        fifties.clearAudioItems()

        playlistHierarchy.findById(fifties.id) shouldBePresent { it.audioItems.isEmpty() shouldBe true }
        playlistHierarchy.runForAll { it.removeAudioItems(rockAudioItems) }
        playlistHierarchy.findById(rock.id) shouldBePresent { it.audioItems.isEmpty() shouldBe true }

        playlistHierarchy.clear()
        playlistHierarchy.isEmpty shouldBe true
    }

    // ├──Best hits
    // │  └──50s
    // │     ├──Rock
    // │     └──Pop
    // └──Selection of playlists
    "Move playlists in the hierarchy" {
        val rock = playlistHierarchy.createPlaylist("Rock")
        playlistHierarchy.findByName(rock.name) shouldBePresent { it shouldBe rock }
        val pop = playlistHierarchy.createPlaylist("Pop")
        val fifties = playlistHierarchy.createPlaylistDirectory("50s")
        playlistHierarchy.addPlaylistsToDirectory(setOf(rock.name, pop.name), fifties.name)
        val bestHits = playlistHierarchy.createPlaylistDirectory("Best hits")
        playlistHierarchy.addPlaylistsToDirectory(setOf(fifties.name), bestHits.name)
        val selection = playlistHierarchy.createPlaylistDirectory("Selection of playlists")
        playlistHierarchy.size() shouldBe 5

        selection.addPlaylist(rock)
        // same result as doing
        // audioPlaylistRepository.movePlaylist(rock.name, selection.name)

        // ├──Best hits
        // │  └──50s
        // │     └──Pop
        // └──Selection of playlists
        //    └──Rock

        playlistHierarchy.size() shouldBe 5
        playlistHierarchy.findByName(selection.name) shouldBePresent { it.playlists.shouldContainOnly(rock) }
        playlistHierarchy.findById(fifties.id) shouldBePresent { it.playlists.shouldNotContain(rock) }
        playlistHierarchy.findByName(bestHits.name) shouldBePresent { it.playlists.shouldContainOnly(fifties) }

        testDispatcher.scheduler.advanceUntilIdle()

        jsonFile.readText().shouldEqualJson(listOf(rock, pop, fifties, bestHits, selection).asJsonKeyValues())

        // --

        playlistHierarchy.movePlaylist(selection.name, fifties.name)
        // same result as doing
        // fifties.addPlaylist(selection)

        // └──Best hits
        //    └──50s
        //       ├──Pop
        //       └──Selection of playlists
        //          └──Rock

        playlistHierarchy.size() shouldBe 5
        playlistHierarchy.findByName(selection.name) shouldBePresent { it.playlists.shouldContainOnly(rock) }
        playlistHierarchy.findByName(fifties.name) shouldBePresent { it.playlists shouldContainExactly setOf(pop, selection) }

        testDispatcher.scheduler.advanceUntilIdle()

        jsonFile.readText().shouldEqualJson(listOf(rock, pop, fifties, bestHits, selection).asJsonKeyValues())

        playlistHierarchy.removeAll(setOf(bestHits)) shouldBe true

        playlistHierarchy.size() shouldBe 0
        playlistHierarchy.isEmpty shouldBe true
        bestHits.playlists.shouldContainOnly(fifties)
        fifties.playlists shouldContainExactly setOf(pop, selection)
        selection.playlists.shouldContainOnly(rock)
        rock.playlists.isEmpty() shouldBe true
        pop.playlists.isEmpty() shouldBe true

        testDispatcher.scheduler.advanceUntilIdle()

        jsonFile.readText() shouldBe "{}"
    }

    "Removing playlist directory from repository is recursive and changes reflected on playlists" {
        val pop = playlistHierarchy.createPlaylist("Pop")
        val fifties = playlistHierarchy.createPlaylistDirectory("50s").also { it.addPlaylist(pop) }
        val bestHits = playlistHierarchy.createPlaylistDirectory("Best hits").also { it.addPlaylist(fifties) }
        val rock = playlistHierarchy.createPlaylist("Rock")
        val selection = playlistHierarchy.createPlaylistDirectory("Selection of playlists").also { it.addPlaylist(rock) }

        // ├──Best hits
        // │  └──50s
        // │     └──Pop
        // └──Selection of playlists
        //    └──Rock

        playlistHierarchy.size() shouldBe 5
        playlistHierarchy.findByName(selection.name) shouldBePresent { it.playlists.shouldContainOnly(rock) }
        playlistHierarchy.findById(fifties.id) shouldBePresent { it.playlists.shouldNotContain(rock) }
        playlistHierarchy.findByName(bestHits.name) shouldBePresent { it.playlists.shouldContainOnly(fifties) }

        playlistHierarchy.removePlaylistFromDirectory(fifties.name, bestHits.name) shouldBe true

        // ├──Best hits
        // └──Selection of playlists
        //    └──Rock

        playlistHierarchy.size() shouldBe 3
        playlistHierarchy.findByName(fifties.name).isEmpty shouldBe true
        playlistHierarchy.findByUniqueId(fifties.uniqueId).isEmpty shouldBe true
        bestHits.playlists.isEmpty() shouldBe true

        playlistHierarchy.removePlaylistFromDirectory(rock, selection.name) shouldBe true

        // ├──Best hits
        // └──Selection of playlists

        playlistHierarchy.size() shouldBe 2
        selection.playlists.isEmpty() shouldBe true
    }

    "Create playlists with existing name" {
        playlistHierarchy.createPlaylistDirectory("New playlist")
        playlistHierarchy.size() shouldBe 1

        shouldThrowMessage("Playlist with name 'New playlist' already exists") { playlistHierarchy.createPlaylistDirectory("New playlist") }
        playlistHierarchy.size() shouldBe 1

        shouldThrowMessage("Playlist with name 'New playlist' already exists") { playlistHierarchy.createPlaylist("New playlist") }
        playlistHierarchy.size() shouldBe 1
    }

    "Removing child playlist directly from one, does not remove them from the repository" {
        val rock = playlistHierarchy.createPlaylist("Rock")
        val rockFavorites = playlistHierarchy.createPlaylist("Rock Favorites")
        val fifties = playlistHierarchy.createPlaylistDirectory("50s")
        playlistHierarchy.addPlaylistToDirectory(rockFavorites, rock.name)
        playlistHierarchy.addPlaylistToDirectory(rock, fifties.name)

        playlistHierarchy.size() shouldBe 3
        fifties.playlists.size shouldBe 1
        rock.playlists.size shouldBe 1

        rock.clearPlaylists()

        playlistHierarchy.size() shouldBe 3
        rock.playlists.isEmpty() shouldBe true
        fifties.playlists.size shouldBe 1

        fifties.removePlaylist(rock.id)

        playlistHierarchy.size() shouldBe 3
        fifties.playlists.isEmpty() shouldBe true
    }

    "Deleting playlist from the repository removes it from any parent one" {
        val rock = playlistHierarchy.createPlaylist("Rock")
        val fifties = playlistHierarchy.createPlaylistDirectory("50s")
        playlistHierarchy.addPlaylistToDirectory(rock, fifties.name)

        playlistHierarchy.size() shouldBe 2
        fifties.playlists.size shouldBe 1

        playlistHierarchy.remove(rock)

        playlistHierarchy.size() shouldBe 1
        fifties.playlists.isEmpty() shouldBe true
    }
})