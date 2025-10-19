package net.transgressoft.commons.fx.music.playlist

import net.transgressoft.commons.event.ReactiveScope
import net.transgressoft.commons.fx.music.audio.ObservableAudioItem
import net.transgressoft.commons.fx.music.audio.ObservableAudioLibrary
import net.transgressoft.commons.fx.music.fxAudioItem
import net.transgressoft.commons.music.playlist.asJsonKeyValues
import net.transgressoft.commons.persistence.json.JsonFileRepository
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrowMessage
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import io.mockk.every
import io.mockk.mockk
import org.testfx.api.FxToolkit
import org.testfx.util.WaitForAsyncUtils
import java.time.Duration
import java.util.*
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@ExperimentalCoroutinesApi
class ObservablePlaylistHierarchyTest : StringSpec({

    val testDispatcher = UnconfinedTestDispatcher()
    val testScope = CoroutineScope(testDispatcher)

    beforeSpec {
        ReactiveScope.flowScope = testScope
        ReactiveScope.ioScope = testScope
        FxToolkit.registerPrimaryStage()
    }

    afterSpec {
        ReactiveScope.resetDefaultFlowScope()
        ReactiveScope.resetDefaultIoScope()
    }

    "Reflects changes on a JsonFileRepository" {
        val jsonFile = tempfile("observablePlaylistHierarchy-test", ".json").apply { deleteOnExit() }
        val jsonFileRepository = JsonFileRepository(jsonFile, ObservablePlaylistMapSerializer)
        val playlistHierarchy = ObservablePlaylistHierarchy(jsonFileRepository)

        val rockAudioItem = Arb.fxAudioItem { title = "50s Rock hit 1" }.next()
        val rockAudioItems = listOf(rockAudioItem)
        val rock = playlistHierarchy.createPlaylist("Rock", rockAudioItems)
        testDispatcher.scheduler.advanceUntilIdle()

        playlistHierarchy.playlistsProperty.shouldContainOnly(rock)

        val rockFavAudioItem = Arb.fxAudioItem { title = "Rock fav" }.next()
        val rockFavoritesAudioItems = listOf(rockFavAudioItem)
        val rockFavorites = playlistHierarchy.createPlaylist("Rock favorites", rockFavoritesAudioItems)

        playlistHierarchy.movePlaylist(rockFavorites.name, rock.name)

        playlistHierarchy.playlistsProperty shouldContainExactly setOf(rock, rockFavorites)

        playlistHierarchy.findById(rock.id) shouldBePresent { updatedRock ->
            updatedRock.playlists.shouldContainOnly(rockFavorites)
            updatedRock.id shouldBe rock.id
            updatedRock.isDirectory shouldBe false
            updatedRock.name shouldBe "Rock"
            updatedRock.audioItems.shouldContainExactly(rockAudioItems)
            updatedRock.playlists.shouldContainExactly(rockFavorites)
        }

        testDispatcher.scheduler.advanceUntilIdle()

        jsonFile.readText().shouldEqualJson(listOf(rock, rockFavorites).asJsonKeyValues())

        rock.isDirectory = true
        rock.name = "Rock directory"

        eventually(100.milliseconds) {
            playlistHierarchy.findByUniqueId(rock.uniqueId) shouldBePresent {
                it.isDirectory shouldBe true
                it.name shouldBe "Rock directory"
            }
        }

        testDispatcher.scheduler.advanceUntilIdle()

        jsonFile.readText().shouldEqualJson(listOf(rock, rockFavorites).asJsonKeyValues())

        jsonFileRepository.close()
    }

    "Throws Exception on creation from JsonFileRepository without AudioLibrary" {
        val audioItem = Arb.fxAudioItem {}.next()
        val playlist =
            mockk<ObservablePlaylist> {
                every { id } returns 1
                every { isDirectory } returns true
                every { name } returns "Rock"
                every { audioItems } returns listOf(audioItem)
                every { playlists } returns emptySet()
                every { asJsonKeyValue() } answers { callOriginal() }
            }
        val jsonFile =
            tempfile("observablePlaylistHierarchy-test", ".json").apply {
                writeText(listOf(playlist).asJsonKeyValues())
                deleteOnExit()
            }
        val jsonFileRepository = JsonFileRepository(jsonFile, ObservablePlaylistMapSerializer)

        shouldThrowMessage("AudioLibrary is required when loading a non empty playlistHierarchy") {
            ObservablePlaylistHierarchy(jsonFileRepository)
        }

        jsonFileRepository.close()
    }

    "Initializes from a non empty JsonFileRepository and AudioLibrary" {
        val audioItem = Arb.fxAudioItem {}.next()
        val playlist =
            mockk<ObservablePlaylist> {
                every { id } returns 1
                every { isDirectory } returns true
                every { name } returns "Rock"
                every { audioItems } returns listOf(audioItem)
                every { playlists } returns emptySet()
                every { asJsonKeyValue() } answers { callOriginal() }
            }
        val jsonFile =
            tempfile("observablePlaylistHierarchy-test", ".json").apply {
                writeText(listOf(playlist).asJsonKeyValues())
                deleteOnExit()
            }
        val jsonFileRepository = JsonFileRepository(jsonFile, ObservablePlaylistMapSerializer)

        val audioLibrary =
            mockk<ObservableAudioLibrary> {
                every { findById(any()) } answers { Optional.of(audioItem) }
            }

        val playlistHierarchy = ObservablePlaylistHierarchy(jsonFileRepository, audioLibrary)

        playlistHierarchy.size() shouldBe 1
        playlistHierarchy.contains {
            it.id == 1 && it.isDirectory && it.name == "Rock" && it.audioItems == listOf(audioItem) && it.playlists.isEmpty()
        } shouldBe true

        playlistHierarchy.playlistsProperty should {
            it.size shouldBe 1
            it.first().id shouldBe 1
            it.first().isDirectory shouldBe true
            it.first().name shouldBe "Rock"
            it.first().audioItems shouldContainExactly listOf(audioItem)
            it.first().playlists.isEmpty() shouldBe true
            it.first().shouldBeInstanceOf<ObservablePlaylist>()
        }
    }

    /** The following playlist hierarchy is used for the test:

     ├──Best hits
     │  ├──50s
     │  │  ├──Rock
     │  │  │  ├──:50s Rock hit 1
     │  │  │  └──:50s Rock hit 2 my fav
     │  │  ├──Pop
     │  │  ├──:50s hit 1
     │  │  └──:50s favorite song
     │  └──60s
     └──This weeks' favorites songs
     */
    "Creates and finds playlists and audio items" {
        val playlistHierarchy = ObservablePlaylistHierarchy()

        val rockAudioItems =
            listOf(
                Arb.fxAudioItem {
                    title = "50s Rock hit 1"
                    duration = Duration.ofSeconds(60)
                }.next(),
                Arb.fxAudioItem {
                    title = "50s Rock hit 2 my fav"
                    duration = Duration.ofSeconds(230)
                }.next()
            )
        val rock = playlistHierarchy.createPlaylist("Rock", rockAudioItems)

        playlistHierarchy.findByName(rock.name) shouldBePresent { it shouldBe rock }
        val playlistsThatContainsAllAudioItemsWith50sInTitle =
            playlistHierarchy.search { it.audioItemsAllMatch { audioItem -> audioItem.title.contains("50s") } }
        playlistsThatContainsAllAudioItemsWith50sInTitle.shouldContainOnly(rock)

        testDispatcher.scheduler.advanceUntilIdle()

        playlistHierarchy.playlistsProperty.shouldContainOnly(rock)

        val pop = playlistHierarchy.createPlaylist("Pop")
        playlistHierarchy.size() shouldBe 2
        playlistHierarchy.numberOfPlaylists() shouldBe 2

        testDispatcher.scheduler.advanceUntilIdle()

        playlistHierarchy.playlistsProperty shouldContainExactly setOf(rock, pop)

        val fifties = playlistHierarchy.createPlaylistDirectory("50s")
        playlistHierarchy.addPlaylistToDirectory(rock, fifties.name)
        playlistHierarchy.addPlaylistToDirectory(pop.name, fifties.name)
        playlistHierarchy.findByName(fifties.name) shouldBePresent { it.playlists shouldContainExactly setOf(pop, rock) }
        playlistHierarchy.numberOfPlaylistDirectories() shouldBe 1

        testDispatcher.scheduler.advanceUntilIdle()

        playlistHierarchy.playlistsProperty shouldContainExactly setOf(rock, pop, fifties)

        val sixties = playlistHierarchy.createPlaylistDirectory("60s")
        sixties.playlists.isEmpty() shouldBe true
        playlistHierarchy.numberOfPlaylistDirectories() shouldBe 2
        playlistHierarchy.findByUniqueId("D-" + sixties.name) shouldBePresent { it shouldBe sixties }

        testDispatcher.scheduler.advanceUntilIdle()

        playlistHierarchy.playlistsProperty shouldContainExactly setOf(rock, pop, fifties, sixties)

        val bestHits = playlistHierarchy.createPlaylistDirectory("Best hits")
        playlistHierarchy.addPlaylistsToDirectory(setOf(fifties, sixties), bestHits.name)
        bestHits.playlists.isEmpty() shouldBe false
        playlistHierarchy.findByName(bestHits.name) shouldBePresent { it.playlists shouldContainExactly setOf(fifties, sixties) }

        testDispatcher.scheduler.advanceUntilIdle()

        playlistHierarchy.playlistsProperty shouldContainExactly setOf(rock, pop, fifties, sixties, bestHits)

        val thisWeeksFavorites = playlistHierarchy.createPlaylist("This weeks' favorites songs")
        playlistHierarchy.search { "favorites" in it.name }.shouldContainExactly(thisWeeksFavorites)
        playlistHierarchy.size() shouldBe 6
        playlistHierarchy.addOrReplaceAll(setOf(bestHits, thisWeeksFavorites))
        playlistHierarchy.size() shouldBe 6
        playlistHierarchy.search { it.isDirectory.not() } shouldContainExactly setOf(rock, pop, thisWeeksFavorites)

        testDispatcher.scheduler.advanceUntilIdle()

        playlistHierarchy.playlistsProperty shouldContainExactly setOf(rock, pop, fifties, sixties, bestHits, thisWeeksFavorites)

        playlistHierarchy.search { it.isDirectory } shouldContainExactly setOf(fifties, sixties, bestHits)

        val fiftiesItems =
            listOf(
                Arb.fxAudioItem {
                    title = "50s hit"
                    duration = Duration.ofSeconds(30)
                }.next(),
                Arb.fxAudioItem {
                    title = "50s favorite song"
                    duration = Duration.ofSeconds(120)
                }.next()
            )

        playlistHierarchy.addAudioItemToPlaylist(fiftiesItems[0], fifties.name)
        playlistHierarchy.addAudioItemsToPlaylist(fiftiesItems, fifties.name)
        val playlistsThatContainsAnyAudioItemsWithHitInTitle =
            playlistHierarchy.search { it.audioItemsAnyMatch { audioItem -> "hit" in audioItem.title } }
        playlistsThatContainsAnyAudioItemsWithHitInTitle shouldContainExactly setOf(rock, fifties)
        val playlistsThatContainsAudioItemsWithDurationBelow60 =
            playlistHierarchy.search {
                it.audioItemsAnyMatch { audioItem: ObservableAudioItem -> audioItem.duration <= Duration.ofSeconds(60) }
            }

        playlistsThatContainsAudioItemsWithDurationBelow60 shouldContainExactly setOf(rock, fifties)

        playlistHierarchy.removeAudioItemFromPlaylist(fiftiesItems[0], fifties.name)

        // this FXAudioItem instance is a mock
        every { fiftiesItems[1].title } returns "new title"

        fifties.audioItemsAllMatch { it.title == "new title" } shouldBe true
        fifties.audioItems.find { it.title == "title" }?.shouldBeEqual(fiftiesItems[1])

        fifties.clearAudioItems()

        playlistHierarchy.findById(fifties.id) shouldBePresent { it.audioItems.isEmpty() shouldBe true }
        playlistHierarchy.runForAll { it.removeAudioItems(rockAudioItems) }
        playlistHierarchy.findById(rock.id) shouldBePresent { it.audioItems.isEmpty() shouldBe true }

        playlistHierarchy.clear()
        playlistHierarchy.isEmpty shouldBe true

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        playlistHierarchy.playlistsProperty.isEmpty() shouldBe true
    }

    /** The following playlist hierarchy is used for the test:

     ├──Best hits
     │  └──50s
     │     ├──Rock
     │     └──Pop
     └──Selection of playlists
     */
    "Moves playlists from/to playlist directories" {
        val playlistHierarchy = ObservablePlaylistHierarchy()

        val rock = playlistHierarchy.createPlaylist("Rock")
        playlistHierarchy.findByName(rock.name) shouldBePresent { it shouldBe rock }
        val pop = playlistHierarchy.createPlaylist("Pop")
        val fifties = playlistHierarchy.createPlaylistDirectory("50s")
        playlistHierarchy.addPlaylistsToDirectory(setOf(rock.name, pop.name), fifties.name)
        val bestHits = playlistHierarchy.createPlaylistDirectory("Best hits")
        playlistHierarchy.addPlaylistsToDirectory(setOf(fifties.name), bestHits.name)
        val selection = playlistHierarchy.createPlaylistDirectory("Selection of playlists")
        playlistHierarchy.size() shouldBe 5

        testDispatcher.scheduler.advanceUntilIdle()

        playlistHierarchy.playlistsProperty shouldContainExactly setOf(rock, pop, fifties, bestHits, selection)

        selection.addPlaylist(rock)
        // same result as doing
        // playlistHierarchy.movePlaylist(rock.name, selection.name)

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

        playlistHierarchy.playlistsProperty shouldContainExactly setOf(rock, pop, fifties, bestHits, selection)

        playlistHierarchy.removeAll(setOf(bestHits)) shouldBe true

        playlistHierarchy.size() shouldBe 0
        playlistHierarchy.isEmpty shouldBe true
        bestHits.playlists.shouldContainOnly(fifties)
        fifties.playlists shouldContainExactly setOf(pop, selection)
        selection.playlists.shouldContainOnly(rock)
        rock.playlists.isEmpty() shouldBe true
        pop.playlists.isEmpty() shouldBe true

        testDispatcher.scheduler.advanceUntilIdle()

        playlistHierarchy.playlistsProperty.isEmpty() shouldBe true
    }

    "Removing playlist directory from it is recursive and changes reflects on playlists" {
        val playlistHierarchy = ObservablePlaylistHierarchy()

        val pop = playlistHierarchy.createPlaylist("Pop")
        val fifties = playlistHierarchy.createPlaylistDirectory("50s").also { it.addPlaylist(pop) }
        val bestHits = playlistHierarchy.createPlaylistDirectory("Best hits").also { it.addPlaylist(fifties) }
        val rock = playlistHierarchy.createPlaylist("Rock")
        val selection = playlistHierarchy.createPlaylistDirectory("Selection of playlists").also { it.addPlaylist(rock) }

        testDispatcher.scheduler.advanceUntilIdle()

        playlistHierarchy.playlistsProperty shouldContainExactly setOf(pop, fifties, bestHits, rock, selection)

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
        playlistHierarchy.findByName(pop.name).isEmpty shouldBe true
        playlistHierarchy.findByUniqueId(fifties.uniqueId).isEmpty shouldBe true
        bestHits.playlists.isEmpty() shouldBe true
        fifties.playlists.shouldContainOnly(pop)

        playlistHierarchy.removePlaylistFromDirectory(rock, selection.name) shouldBe true

        // ├──Best hits
        // └──Selection of playlists

        playlistHierarchy.size() shouldBe 2
        selection.playlists.isEmpty() shouldBe true

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        playlistHierarchy.playlistsProperty shouldContainExactly setOf(bestHits, selection)
    }

    "Throws Exception when creating playlists with an existing name" {
        val playlistHierarchy = ObservablePlaylistHierarchy()

        val newPlaylistDirectory = playlistHierarchy.createPlaylistDirectory("New playlist")

        shouldThrowMessage("Playlist with name 'New playlist' already exists") { playlistHierarchy.createPlaylistDirectory("New playlist") }
        playlistHierarchy.size() shouldBe 1

        shouldThrowMessage("Playlist with name 'New playlist' already exists") { playlistHierarchy.createPlaylist("New playlist") }
        playlistHierarchy.size() shouldBe 1

        playlistHierarchy.remove(newPlaylistDirectory) shouldBe true
        playlistHierarchy.isEmpty shouldBe true
    }

    "Removing playlists from a directory, does not remove them from the playlistHierarchy" {
        val playlistHierarchy = ObservablePlaylistHierarchy()

        val fifties = playlistHierarchy.createPlaylistDirectory("50s")
        val rock = playlistHierarchy.createPlaylistDirectory("Rock")
        val rockFavorites = playlistHierarchy.createPlaylist("Rock Favorites")
        playlistHierarchy.addPlaylistToDirectory(rockFavorites, rock.name)
        playlistHierarchy.addPlaylistToDirectory(rock, fifties.name)

        // └──50s
        //    └──Rock
        //        └──Rock Favorites

        playlistHierarchy.size() shouldBe 3
        fifties.playlists.size shouldBe 1
        rock.playlists.size shouldBe 1

        rock.clearPlaylists()

        // └──50s
        //    └──Rock
        //
        // └ Rock Favorites (playlist remains in the playlistHierarchy)

        playlistHierarchy.size() shouldBe 3
        rock.playlists.isEmpty() shouldBe true
        fifties.playlists.size shouldBe 1

        fifties.removePlaylist(rock.id)

        // └──50s
        //
        // └ Rock (playlist remains in the playlistHierarchy)
        // └ Rock Favorites

        playlistHierarchy.size() shouldBe 3
        fifties.playlists.isEmpty() shouldBe true
    }

    "Deleting a playlist removes it from the playlistHierarchy and its parent directory" {
        val playlistHierarchy = ObservablePlaylistHierarchy()

        val rock = playlistHierarchy.createPlaylist("Rock")
        val fifties = playlistHierarchy.createPlaylistDirectory("50s")
        playlistHierarchy.addPlaylistToDirectory(rock, fifties.name)

        // └──50s
        //    └──Rock

        playlistHierarchy.size() shouldBe 2
        fifties.playlists.size shouldBe 1

        playlistHierarchy.remove(rock)

        // └──50s

        playlistHierarchy.size() shouldBe 1
        fifties.playlists.isEmpty() shouldBe true
    }
})