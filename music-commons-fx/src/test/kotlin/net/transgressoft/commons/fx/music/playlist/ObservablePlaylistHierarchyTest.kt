package net.transgressoft.commons.fx.music.playlist

import net.transgressoft.commons.fx.music.audio.FXAudioLibrary
import net.transgressoft.commons.fx.music.audio.ObservableAudioItem
import net.transgressoft.commons.fx.music.fxAudioItem
import net.transgressoft.commons.music.playlist.asJsonKeyValues
import net.transgressoft.lirp.event.ReactiveScope
import net.transgressoft.lirp.persistence.VolatileRepository
import net.transgressoft.lirp.persistence.json.JsonFileRepository
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.nondeterministic.eventually
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
import javafx.application.Platform
import org.testfx.api.FxToolkit
import org.testfx.util.WaitForAsyncUtils
import java.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@ExperimentalCoroutinesApi
internal class ObservablePlaylistHierarchyTest : StringSpec({

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

    suspend fun eventuallyOnFxThread(
        duration: kotlin.time.Duration = 500.milliseconds,
        assertion: () -> Unit
    ) {
        eventually(duration) {
            Platform.runLater {
                assertion()
            }
        }
    }

    "Reflects changes on a JsonFileRepository" {
        val jsonFile = tempfile("observablePlaylistHierarchy-test", ".json").apply { deleteOnExit() }
        val jsonFileRepository = JsonFileRepository(jsonFile, ObservablePlaylistMapSerializer)
        val playlistHierarchy = FXPlaylistHierarchy(jsonFileRepository)

        val rockAudioItem = Arb.fxAudioItem { title = "50s Rock hit 1" }.next()
        val rockAudioItems = listOf(rockAudioItem)
        val rock = playlistHierarchy.createPlaylist("Rock", rockAudioItems)

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            playlistHierarchy.playlistsProperty.shouldContainOnly(rock)
        }

        val rockFavAudioItem = Arb.fxAudioItem { title = "Rock fav" }.next()
        val rockFavoritesAudioItems = listOf(rockFavAudioItem)
        val rockFavorites = playlistHierarchy.createPlaylist("Rock favorites", rockFavoritesAudioItems)

        playlistHierarchy.movePlaylist(rockFavorites.name, rock.name)

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            playlistHierarchy.playlistsProperty shouldContainExactly setOf(rock, rockFavorites)
            playlistHierarchy.findById(rock.id) shouldBePresent { updatedRock ->
                updatedRock.playlists.shouldContainOnly(rockFavorites)
                updatedRock.id shouldBe rock.id
                updatedRock.isDirectory shouldBe false
                updatedRock.name shouldBe "Rock"
                updatedRock.audioItems.shouldContainExactly(rockAudioItems)
                updatedRock.playlists.shouldContainExactly(rockFavorites)
            }
        }

        testDispatcher.scheduler.advanceUntilIdle()

        jsonFile.readText().shouldEqualJson(listOf(rock, rockFavorites).asJsonKeyValues())

        rock.isDirectory = true
        rock.name = "Rock directory"

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            playlistHierarchy.findByUniqueId(rock.uniqueId) shouldBePresent {
                it.isDirectory shouldBe true
                it.name shouldBe "Rock directory"
            }
        }

        testDispatcher.scheduler.advanceUntilIdle()

        eventually(2.seconds) {
            jsonFile.readText().shouldEqualJson(listOf(rock, rockFavorites).asJsonKeyValues())
        }

        playlistHierarchy.close()
        jsonFileRepository.close()
    }

    "Initializes from a non empty JsonFileRepository without AudioLibrary resolves to empty audio items" {
        // Create a hierarchy to write a playlist with a reference audio item id to a JSON file
        val audioItem = Arb.fxAudioItem {}.next()
        val jsonFile =
            tempfile("observablePlaylistHierarchy-test", ".json").apply { deleteOnExit() }
        val writeHierarchy = FXPlaylistHierarchy(JsonFileRepository(jsonFile, ObservablePlaylistMapSerializer))
        val writtenPlaylist = writeHierarchy.createPlaylistDirectory("Rock")
        writtenPlaylist.addAudioItem(audioItem)
        testDispatcher.scheduler.advanceUntilIdle()
        writeHierarchy.close()

        // No audio library registered — audio item IDs are preserved but entities cannot be resolved
        val playlistHierarchy = FXPlaylistHierarchy(JsonFileRepository(jsonFile, ObservablePlaylistMapSerializer))

        playlistHierarchy.size() shouldBe 1
        playlistHierarchy.contains {
            it.id == writtenPlaylist.id && it.isDirectory && it.name == "Rock" && it.playlists.isEmpty()
        } shouldBe true

        playlistHierarchy.close()
    }

    "Initializes from a non empty JsonFileRepository and AudioLibrary" {
        val audioItem = Arb.fxAudioItem {}.next()
        // Create a hierarchy to write a playlist with a reference audio item id to a JSON file
        val jsonFile =
            tempfile("observablePlaylistHierarchy-test", ".json").apply { deleteOnExit() }
        val writeHierarchy = FXPlaylistHierarchy(JsonFileRepository(jsonFile, ObservablePlaylistMapSerializer))
        val writtenPlaylist = writeHierarchy.createPlaylistDirectory("Rock")
        writtenPlaylist.addAudioItem(audioItem)
        testDispatcher.scheduler.advanceUntilIdle()
        writeHierarchy.close()

        val audioItemRepository = VolatileRepository<Int, ObservableAudioItem>()
        audioItemRepository.add(audioItem)
        val audioLibrary = FXAudioLibrary(audioItemRepository)

        val playlistHierarchy = FXPlaylistHierarchy(JsonFileRepository(jsonFile, ObservablePlaylistMapSerializer))

        playlistHierarchy.size() shouldBe 1
        playlistHierarchy.contains {
            it.id == writtenPlaylist.id && it.isDirectory && it.name == "Rock" && it.audioItems == listOf(audioItem) && it.playlists.isEmpty()
        } shouldBe true

        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            playlistHierarchy.playlistsProperty should {
                it.size shouldBe 1
                it.first().id shouldBe writtenPlaylist.id
                it.first().isDirectory shouldBe true
                it.first().name shouldBe "Rock"
                it.first().audioItems shouldContainExactly listOf(audioItem)
                it.first().playlists.isEmpty() shouldBe true
                it.first().shouldBeInstanceOf<ObservablePlaylist>()
            }
        }

        playlistHierarchy.close()
        audioLibrary.close()
        audioItemRepository.close()
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
        val playlistHierarchy = FXPlaylistHierarchy()

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
        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            playlistHierarchy.playlistsProperty.shouldContainOnly(rock)
        }

        val pop = playlistHierarchy.createPlaylist("Pop")
        playlistHierarchy.size() shouldBe 2
        playlistHierarchy.numberOfPlaylists() shouldBe 2

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            playlistHierarchy.playlistsProperty shouldContainExactly setOf(rock, pop)
        }

        val fifties = playlistHierarchy.createPlaylistDirectory("50s")
        playlistHierarchy.addPlaylistToDirectory(rock, fifties.name)
        playlistHierarchy.addPlaylistToDirectory(pop.name, fifties.name)

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            playlistHierarchy.findByName(fifties.name) shouldBePresent {
                it.playlists shouldContainExactly setOf(pop, rock)
            }
            playlistHierarchy.numberOfPlaylistDirectories() shouldBe 1
            playlistHierarchy.playlistsProperty shouldContainExactly setOf(rock, pop, fifties)
        }

        val sixties = playlistHierarchy.createPlaylistDirectory("60s")

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            sixties.playlists.isEmpty() shouldBe true
            playlistHierarchy.numberOfPlaylistDirectories() shouldBe 2
            playlistHierarchy.findByUniqueId("D-" + sixties.name) shouldBePresent { it shouldBe sixties }
            playlistHierarchy.playlistsProperty shouldContainExactly setOf(rock, pop, fifties, sixties)
        }

        val bestHits = playlistHierarchy.createPlaylistDirectory("Best hits")
        playlistHierarchy.addPlaylistsToDirectory(setOf(fifties, sixties), bestHits.name)

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            bestHits.playlists.isEmpty() shouldBe false
            playlistHierarchy.findByName(bestHits.name) shouldBePresent {
                it.playlists shouldContainExactly setOf(fifties, sixties)
            }
            playlistHierarchy.playlistsProperty shouldContainExactly setOf(rock, pop, fifties, sixties, bestHits)
        }

        val thisWeeksFavorites = playlistHierarchy.createPlaylist("This weeks' favorites songs")
        playlistHierarchy.search { "favorites" in it.name }.shouldContainExactly(thisWeeksFavorites)
        playlistHierarchy.size() shouldBe 6
        playlistHierarchy.add(bestHits)
        playlistHierarchy.add(thisWeeksFavorites)
        playlistHierarchy.size() shouldBe 6
        playlistHierarchy.search { it.isDirectory.not() } shouldContainExactly setOf(rock, pop, thisWeeksFavorites)

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            playlistHierarchy.playlistsProperty shouldContainExactly setOf(rock, pop, fifties, sixties, bestHits, thisWeeksFavorites)
        }

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

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            val playlistsThatContainsAnyAudioItemsWithHitInTitle = playlistHierarchy.search { it.audioItemsAnyMatch { audioItem -> "hit" in audioItem.title } }
            playlistsThatContainsAnyAudioItemsWithHitInTitle shouldContainExactly setOf(rock, fifties)

            val playlistsThatContainsAudioItemsWithDurationBelow60 =
                playlistHierarchy.search {
                    it.audioItemsAnyMatch { audioItem: ObservableAudioItem -> audioItem.duration <= Duration.ofSeconds(60) }
                }
            playlistsThatContainsAudioItemsWithDurationBelow60 shouldContainExactly setOf(rock, fifties)
        }

        playlistHierarchy.removeAudioItemFromPlaylist(fiftiesItems[0], fifties.name)

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            // this FXAudioItem instance is a mock
            every { fiftiesItems[1].title } returns "new title"

            fifties.audioItemsAllMatch { it.title == "new title" } shouldBe true
            fifties.audioItems.find { it.title == "title" }?.shouldBeEqual(fiftiesItems[1])
        }

        fifties.clearAudioItems()

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            playlistHierarchy.findById(fifties.id) shouldBePresent {
                it.audioItems.isEmpty() shouldBe true
            }
        }

        playlistHierarchy.forEach { it.removeAudioItems(rockAudioItems) }

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            playlistHierarchy.findById(rock.id) shouldBePresent {
                it.audioItems.isEmpty() shouldBe true
            }
        }

        playlistHierarchy.clear()
        playlistHierarchy.isEmpty shouldBe true

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            playlistHierarchy.playlistsProperty.isEmpty() shouldBe true
        }

        playlistHierarchy.close()
    }

    /** The following playlist hierarchy is used for the test:

     ├──Best hits
     │  └──50s
     │     ├──Rock
     │     └──Pop
     └──Selection of playlists
     */
    "Moves playlists from/to playlist directories" {
        val playlistHierarchy = FXPlaylistHierarchy()

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
        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            playlistHierarchy.playlistsProperty shouldContainExactly setOf(rock, pop, fifties, bestHits, selection)
        }

        selection.addPlaylist(rock)
        // same result as doing
        // playlistHierarchy.movePlaylist(rock.name, selection.name)

        // ├──Best hits
        // │  └──50s
        // │     └──Pop
        // └──Selection of playlists
        //    └──Rock

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            playlistHierarchy.size() shouldBe 5
            playlistHierarchy.findByName(selection.name) shouldBePresent {
                it.playlists.shouldContainOnly(rock)
            }
            playlistHierarchy.findById(fifties.id) shouldBePresent {
                it.playlists.shouldNotContain(rock)
            }
            playlistHierarchy.findByName(bestHits.name) shouldBePresent {
                it.playlists.shouldContainOnly(fifties)
            }
        }

        // --

        playlistHierarchy.movePlaylist(selection.name, fifties.name)
        // same result as doing
        // fifties.addPlaylist(selection)

        // └──Best hits
        //    └──50s
        //       ├──Pop
        //       └──Selection of playlists
        //          └──Rock

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            playlistHierarchy.size() shouldBe 5
            playlistHierarchy.findByName(selection.name) shouldBePresent {
                it.playlists.shouldContainOnly(rock)
            }
            playlistHierarchy.findByName(fifties.name) shouldBePresent {
                it.playlists shouldContainExactly setOf(pop, selection)
            }
            playlistHierarchy.playlistsProperty shouldContainExactly setOf(rock, pop, fifties, bestHits, selection)
        }

        playlistHierarchy.removeAll(setOf(bestHits)) shouldBe true

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            playlistHierarchy.size() shouldBe 0
            playlistHierarchy.isEmpty shouldBe true
            bestHits.playlists.shouldContainOnly(fifties)
            fifties.playlists shouldContainExactly setOf(pop, selection)
            selection.playlists.shouldContainOnly(rock)
            rock.playlists.isEmpty() shouldBe true
            pop.playlists.isEmpty() shouldBe true
            playlistHierarchy.playlistsProperty.isEmpty() shouldBe true
        }

        playlistHierarchy.close()
    }

    "Removing playlist directory from it is recursive and changes reflects on playlists" {
        val playlistHierarchy = FXPlaylistHierarchy()

        val pop = playlistHierarchy.createPlaylist("Pop")
        val fifties = playlistHierarchy.createPlaylistDirectory("50s").also { it.addPlaylist(pop) }
        val bestHits = playlistHierarchy.createPlaylistDirectory("Best hits").also { it.addPlaylist(fifties) }
        val rock = playlistHierarchy.createPlaylist("Rock")
        val selection = playlistHierarchy.createPlaylistDirectory("Selection of playlists").also { it.addPlaylist(rock) }

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            playlistHierarchy.playlistsProperty shouldContainExactly setOf(pop, fifties, bestHits, rock, selection)
        }

        // ├──Best hits
        // │  └──50s
        // │     └──Pop
        // └──Selection of playlists
        //    └──Rock

        eventuallyOnFxThread {
            playlistHierarchy.size() shouldBe 5
            playlistHierarchy.findByName(selection.name) shouldBePresent {
                it.playlists.shouldContainOnly(rock)
            }
            playlistHierarchy.findById(fifties.id) shouldBePresent {
                it.playlists.shouldNotContain(rock)
            }
            playlistHierarchy.findByName(bestHits.name) shouldBePresent {
                it.playlists.shouldContainOnly(fifties)
            }
        }

        var removed = playlistHierarchy.removePlaylistFromDirectory(fifties.name, bestHits.name)

        // ├──Best hits
        // └──Selection of playlists
        //    └──Rock

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            removed shouldBe true
            playlistHierarchy.size() shouldBe 3
            playlistHierarchy.findByName(pop.name).isEmpty shouldBe true
            playlistHierarchy.findByUniqueId(fifties.uniqueId).isEmpty shouldBe true
            bestHits.playlists.isEmpty() shouldBe true
            fifties.playlists.shouldContainOnly(pop)
        }

        removed = playlistHierarchy.removePlaylistFromDirectory(rock, selection.name)

        // ├──Best hits
        // └──Selection of playlists

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            removed shouldBe true
            playlistHierarchy.size() shouldBe 2
            selection.playlists.isEmpty() shouldBe true
            playlistHierarchy.playlistsProperty shouldContainExactly setOf(bestHits, selection)
        }

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            playlistHierarchy.playlistsProperty shouldContainExactly setOf(bestHits, selection)
        }

        playlistHierarchy.close()
    }

    "Throws Exception when creating playlists with an existing name" {
        val playlistHierarchy = FXPlaylistHierarchy()

        val newPlaylistDirectory = playlistHierarchy.createPlaylistDirectory("New playlist")

        io.kotest.assertions.throwables.shouldThrowMessage("Playlist with name 'New playlist' already exists") {
            playlistHierarchy.createPlaylistDirectory("New playlist")
        }
        playlistHierarchy.size() shouldBe 1

        io.kotest.assertions.throwables.shouldThrowMessage("Playlist with name 'New playlist' already exists") {
            playlistHierarchy.createPlaylist("New playlist")
        }
        playlistHierarchy.size() shouldBe 1

        playlistHierarchy.remove(newPlaylistDirectory) shouldBe true
        playlistHierarchy.isEmpty shouldBe true

        playlistHierarchy.close()
    }

    "Removing playlists from a directory, does not remove them from the playlistHierarchy" {
        val playlistHierarchy = FXPlaylistHierarchy()

        val fifties = playlistHierarchy.createPlaylistDirectory("50s")
        val rock = playlistHierarchy.createPlaylistDirectory("Rock")
        val rockFavorites = playlistHierarchy.createPlaylist("Rock Favorites")
        playlistHierarchy.addPlaylistToDirectory(rockFavorites, rock.name)
        playlistHierarchy.addPlaylistToDirectory(rock, fifties.name)

        // └──50s
        //    └──Rock
        //        └──Rock Favorites

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            playlistHierarchy.size() shouldBe 3
            fifties.playlists.size shouldBe 1
            rock.playlists.size shouldBe 1
        }

        rock.clearPlaylists()

        // └──50s
        //    └──Rock
        //
        // Rock Favorites (playlist remains in the playlistHierarchy)

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            playlistHierarchy.size() shouldBe 3
            rock.playlists.isEmpty() shouldBe true
            fifties.playlists.size shouldBe 1
        }

        fifties.removePlaylist(rock.id)

        // └──50s
        //
        // Rock (playlist remains in the playlistHierarchy)
        // Rock Favorites

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            playlistHierarchy.size() shouldBe 3
            fifties.playlists.isEmpty() shouldBe true
        }

        playlistHierarchy.close()
    }

    "Deleting a playlist removes it from the playlistHierarchy and its parent directory" {
        val playlistHierarchy = FXPlaylistHierarchy()

        val rock = playlistHierarchy.createPlaylist("Rock")
        val fifties = playlistHierarchy.createPlaylistDirectory("50s")
        playlistHierarchy.addPlaylistToDirectory(rock, fifties.name)

        // └──50s
        //    └──Rock

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            playlistHierarchy.size() shouldBe 2
            fifties.playlists.size shouldBe 1
        }

        playlistHierarchy.remove(rock)

        // └──50s

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            playlistHierarchy.size() shouldBe 1
            fifties.playlists.isEmpty() shouldBe true
        }

        playlistHierarchy.close()
    }

    "Single playlist property updates are eventually consistent" {
        val hierarchy = FXPlaylistHierarchy()
        val audioItem = Arb.fxAudioItem().next()

        val playlist = hierarchy.createPlaylist("Test Playlist", listOf(audioItem))

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            hierarchy.playlistsProperty.size shouldBe 1
            playlist.audioItemsProperty.size shouldBe 1
        }

        hierarchy.close()
    }

    "FXPlaylist allows adding duplicate audio items" {
        val hierarchy = FXPlaylistHierarchy()
        val item = Arb.fxAudioItem { title = "Duplicate Me" }.next()
        val playlist = hierarchy.createPlaylist("Dupes", listOf(item))

        playlist.addAudioItem(item)

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            playlist.audioItemsProperty.size shouldBe 2
        }

        hierarchy.close()
    }

    "FXPlaylist addAudioItems emits MutationEvent" {
        val hierarchy = FXPlaylistHierarchy()
        val playlist = hierarchy.createPlaylist("Events Test")
        val item1 = Arb.fxAudioItem { title = "Event Item 1" }.next()
        val item2 = Arb.fxAudioItem { title = "Event Item 2" }.next()

        // Start with one item so the playlist already has audioItems
        playlist.addAudioItem(item1)

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            playlist.audioItemsProperty.size shouldBe 1
        }

        // addAudioItems must wrap update in mutateAndPublish so the observable property updates on FX thread
        playlist.addAudioItem(item2)

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            playlist.audioItemsProperty.size shouldBe 2
        }

        hierarchy.close()
    }

    "Rapid playlist modifications are eventually consistent" {
        val hierarchy = FXPlaylistHierarchy()
        val audioItems = List(10) { Arb.fxAudioItem { title = "Item-$it" }.next() }

        val playlist = hierarchy.createPlaylist("Test")

        // Rapid additions
        audioItems.forEach { playlist.addAudioItem(it) }

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            playlist.audioItemsProperty.size shouldBe 10
        }

        // Rapid removals
        audioItems.take(5).forEach { playlist.removeAudioItem(it) }

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            playlist.audioItemsProperty.size shouldBe 5
        }

        hierarchy.close()
    }

    "Nested playlist additions maintain hierarchy consistency" {
        val hierarchy = FXPlaylistHierarchy()

        val child = hierarchy.createPlaylist("Child")
        val parent = hierarchy.createPlaylistDirectory("Parent")

        parent.addPlaylist(child)

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            hierarchy.playlistsProperty.size shouldBe 2
            parent.playlistsProperty.size shouldBe 1
            hierarchy.findParentPlaylist(child).isPresent shouldBe true
        }

        hierarchy.close()
    }

    "Moving playlist between directories is eventually consistent" {
        val hierarchy = FXPlaylistHierarchy()

        val playlist = hierarchy.createPlaylist("Movable")
        val dir1 = hierarchy.createPlaylistDirectory("Directory 1")
        val dir2 = hierarchy.createPlaylistDirectory("Directory 2")

        // Add to dir1
        dir1.addPlaylist(playlist)
        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            dir1.playlistsProperty.size shouldBe 1
            dir2.playlistsProperty.size shouldBe 0
        }

        // Move to dir2
        dir2.addPlaylist(playlist)
        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            dir1.playlistsProperty.size shouldBe 0
            dir2.playlistsProperty.size shouldBe 1
            hierarchy.findParentPlaylist(playlist).get() shouldBe dir2
        }

        hierarchy.close()
    }

    "Clearing playlist propagates to observable properties" {
        val hierarchy = FXPlaylistHierarchy()
        val audioItems = List(5) { Arb.fxAudioItem().next() }

        val playlist = hierarchy.createPlaylist("To Clear", audioItems)

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            playlist.audioItemsProperty.size shouldBe 5
        }

        playlist.clearAudioItems()

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            playlist.audioItemsProperty.size shouldBe 0
        }

        hierarchy.close()
    }

    "Recursive audio items property updates correctly" {
        val hierarchy = FXPlaylistHierarchy()

        val item1 = Arb.fxAudioItem { title = "Item1" }.next()
        val item2 = Arb.fxAudioItem { title = "Item2" }.next()
        val item3 = Arb.fxAudioItem { title = "Item3" }.next()

        val child: ObservablePlaylist = hierarchy.createPlaylist("Child", listOf(item1, item2))
        val parent: ObservablePlaylist = hierarchy.createPlaylistDirectory("Parent", listOf(item3))

        parent.addPlaylist(child)

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            parent.audioItemsRecursiveProperty.size shouldBe 3
            parent.audioItemsRecursiveProperty.map { it: ObservableAudioItem -> it.title } shouldContainExactly listOf("Item3", "Item1", "Item2")
        }

        hierarchy.close()
    }

    "Recursive audio items property propagates when items are added to a nested child playlist" {
        val hierarchy = FXPlaylistHierarchy()

        val leaf: ObservablePlaylist = hierarchy.createPlaylist("Leaf")
        val folder: ObservablePlaylist = hierarchy.createPlaylistDirectory("Folder")

        folder.addPlaylist(leaf)

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        folder.audioItemsRecursiveProperty.size shouldBe 0

        val late1 = Arb.fxAudioItem { title = "Late1" }.next()
        val late2 = Arb.fxAudioItem { title = "Late2" }.next()
        leaf.addAudioItems(listOf(late1, late2))

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        leaf.audioItemsRecursiveProperty.size shouldBe 2
        folder.audioItemsRecursiveProperty.size shouldBe 2
        folder.audioItemsRecursiveProperty.map { it: ObservableAudioItem -> it.title } shouldContainExactly listOf("Late1", "Late2")

        leaf.removeAudioItems(listOf(late1))

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        folder.audioItemsRecursiveProperty.size shouldBe 1
        folder.audioItemsRecursiveProperty.map { it: ObservableAudioItem -> it.title } shouldContainExactly listOf("Late2")

        hierarchy.close()
    }

    "Recursive audio items property propagates through grandchild updates" {
        val hierarchy = FXPlaylistHierarchy()

        val grandchild: ObservablePlaylist = hierarchy.createPlaylist("Grandchild")
        val middle: ObservablePlaylist = hierarchy.createPlaylistDirectory("Middle")
        val top: ObservablePlaylist = hierarchy.createPlaylistDirectory("Top")

        middle.addPlaylist(grandchild)
        top.addPlaylist(middle)

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        top.audioItemsRecursiveProperty.size shouldBe 0

        val deep = Arb.fxAudioItem { title = "Deep" }.next()
        grandchild.addAudioItems(listOf(deep))

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        middle.audioItemsRecursiveProperty.size shouldBe 1
        top.audioItemsRecursiveProperty.size shouldBe 1
        top.audioItemsRecursiveProperty.map { it: ObservableAudioItem -> it.title } shouldContainExactly listOf("Deep")

        hierarchy.close()
    }

    "Recursive audio items property reflects nested descendants after JSON round-trip" {
        val jsonFile = tempfile("observablePlaylistHierarchy-recursive-roundtrip", ".json").apply { deleteOnExit() }

        val seed = Arb.fxAudioItem { title = "RoundtripSeed" }.next()
        val seed2 = Arb.fxAudioItem { title = "RoundtripSeed2" }.next()

        // Author phase: build folder + nested leaf with audio items, then persist.
        val writeHierarchy = FXPlaylistHierarchy(JsonFileRepository(jsonFile, ObservablePlaylistMapSerializer))
        val leaf = writeHierarchy.createPlaylist("Leaf")
        val folder = writeHierarchy.createPlaylistDirectory("Folder")
        folder.addPlaylist(leaf)
        leaf.addAudioItems(listOf(seed, seed2))

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        folder.audioItemsRecursiveProperty.size shouldBe 2

        writeHierarchy.close()

        // Reload phase: simulate app restart — the audio library must be available so the
        // playlist hierarchy can resolve the persisted audio item ids back to entities.
        val audioItemRepository = VolatileRepository<Int, ObservableAudioItem>()
        audioItemRepository.add(seed)
        audioItemRepository.add(seed2)
        FXAudioLibrary(audioItemRepository)

        val readHierarchy = FXPlaylistHierarchy(JsonFileRepository(jsonFile, ObservablePlaylistMapSerializer))

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        val reloadedFolder = readHierarchy.findByName("Folder").get()
        val reloadedLeaf = readHierarchy.findByName("Leaf").get()

        reloadedLeaf.audioItemsRecursiveProperty.size shouldBe 2
        reloadedFolder.audioItemsRecursiveProperty.size shouldBe 2
        reloadedFolder.audioItemsRecursiveProperty.map { it: ObservableAudioItem -> it.title } shouldContainExactly listOf("RoundtripSeed", "RoundtripSeed2")

        // Subsequent runtime mutations on the reloaded leaf must propagate to the reloaded folder.
        val late = Arb.fxAudioItem { title = "RoundtripLate" }.next()
        audioItemRepository.add(late)
        reloadedLeaf.addAudioItems(listOf(late))

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        reloadedFolder.audioItemsRecursiveProperty.size shouldBe 3

        readHierarchy.close()
    }

    "Recursive audio items property stops propagating after a child is removed" {
        val hierarchy = FXPlaylistHierarchy()

        // Build the same shape as the surrounding tests use: create the leaf empty,
        // nest it, then add items afterwards. This avoids the racy "create-with-items
        // then nest" setup where the audio item aggregate binds asynchronously.
        val leaf: ObservablePlaylist = hierarchy.createPlaylist("Leaf")
        val folder: ObservablePlaylist = hierarchy.createPlaylistDirectory("Folder")

        folder.addPlaylist(leaf)

        val seed = Arb.fxAudioItem { title = "Seed" }.next()
        leaf.addAudioItems(listOf(seed))

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        folder.audioItemsRecursiveProperty.size shouldBe 1

        folder.removePlaylist(leaf)

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        folder.audioItemsRecursiveProperty.size shouldBe 0

        // Items added to a former child after removal must not appear in the former parent.
        val late = Arb.fxAudioItem { title = "Late" }.next()
        leaf.addAudioItems(listOf(late))

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        leaf.audioItemsRecursiveProperty.size shouldBe 2
        folder.audioItemsRecursiveProperty.size shouldBe 0

        hierarchy.close()
    }

    "Repository subscription updates observable properties" {
        val hierarchy = FXPlaylistHierarchy()

        val playlist = hierarchy.createPlaylist("Test")

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            hierarchy.playlistsProperty.contains(playlist) shouldBe true
        }

        hierarchy.remove(playlist)

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            hierarchy.playlistsProperty.contains(playlist) shouldBe false
        }

        hierarchy.close()
    }

    "Multiple concurrent modifications converge to consistent state" {
        val hierarchy = FXPlaylistHierarchy()
        val audioItems = List(20) { Arb.fxAudioItem { title = "Item-$it" }.next() }

        val playlist = hierarchy.createPlaylist("Concurrent Test")

        // Simulate concurrent-like rapid operations
        audioItems.take(10).forEach { playlist.addAudioItem(it) }
        audioItems.drop(10).forEach { playlist.addAudioItem(it) }
        audioItems.take(5).forEach { playlist.removeAudioItem(it) }

        testDispatcher.scheduler.advanceUntilIdle()
        WaitForAsyncUtils.waitForFxEvents()

        eventuallyOnFxThread {
            // Should have 15 items: added 20, removed 5
            playlist.audioItemsProperty.size shouldBe 15
        }

        hierarchy.close()
    }
})