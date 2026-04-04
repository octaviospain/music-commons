package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.DefaultAudioLibrary
import net.transgressoft.commons.music.audio.audioItem
import net.transgressoft.lirp.event.ReactiveScope
import net.transgressoft.lirp.persistence.RegistryBase
import net.transgressoft.lirp.persistence.VolatileRepository
import net.transgressoft.lirp.persistence.json.JsonFileRepository
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
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@ExperimentalCoroutinesApi
internal class DefaultPlaylistHierarchyTest : StringSpec({

    val testDispatcher = UnconfinedTestDispatcher()
    val testScope = CoroutineScope(testDispatcher)

    beforeSpec {
        ReactiveScope.flowScope = testScope
        ReactiveScope.ioScope = testScope
    }

    afterSpec {
        ReactiveScope.resetDefaultFlowScope()
        ReactiveScope.resetDefaultIoScope()
    }

    "Reflects changes on a JsonFileRepository" {
        val audioItemRepository = VolatileRepository<Int, AudioItem>()
        RegistryBase.deregisterRepository(AudioItem::class.java)
        RegistryBase.registerRepository(AudioItem::class.java, audioItemRepository)

        val jsonFile = tempfile("playlistRepository-test", ".json").apply { deleteOnExit() }
        val playlistHierarchy = DefaultPlaylistHierarchy(JsonFileRepository(jsonFile, AudioPlaylistMapSerializer))

        val rockAudioItem = Arb.audioItem { title = "50s Rock hit 1" }.next().also { audioItemRepository.add(it) }
        val rockAudioItems = listOf(rockAudioItem)
        val rock = playlistHierarchy.createPlaylist("Rock", rockAudioItems)

        val rockFavAudioItem = Arb.audioItem { title = "Rock fav" }.next().also { audioItemRepository.add(it) }
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

        playlistHierarchy.close()
        RegistryBase.deregisterRepository(AudioItem::class.java)
        audioItemRepository.close()
    }

    "Initializes from a non empty JsonFileRepository and AudioLibrary" {
        val audioItem = Arb.audioItem { id = 453374921 }.next()
        val jsonFile =
            tempfile("playlistRepository-test", ".json").apply {
                writeText(
                    """{"1":{"id":1,"isDirectory":true,"name":"Rock","audioItems":[453374921],"playlists":[]}}"""
                )
                deleteOnExit()
            }

        val audioLibraryRepository = VolatileRepository<Int, AudioItem>()
        val audioLibrary = DefaultAudioLibrary(audioLibraryRepository)
        audioLibrary.add(audioItem)

        val playlistHierarchy = DefaultPlaylistHierarchy(JsonFileRepository(jsonFile, AudioPlaylistMapSerializer))

        testDispatcher.scheduler.advanceUntilIdle()

        playlistHierarchy.size() shouldBe 1
        playlistHierarchy.contains {
            it.id == 1 && it.isDirectory && it.name == "Rock" && it.audioItems == listOf(audioItem) && it.playlists.isEmpty()
        } shouldBe true

        playlistHierarchy.close()
        audioLibrary.close()
        audioLibraryRepository.close()
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
        val audioItemRepository = VolatileRepository<Int, AudioItem>()
        RegistryBase.deregisterRepository(AudioItem::class.java)
        RegistryBase.registerRepository(AudioItem::class.java, audioItemRepository)

        val playlistHierarchy = DefaultPlaylistHierarchy()
        val rockAudioItems =
            listOf(
                Arb.audioItem {
                    title = "50s Rock hit 1"
                    duration = Duration.ofSeconds(60)
                }.next().also { audioItemRepository.add(it) },
                Arb.audioItem {
                    title = "50s Rock hit 2 my fav"
                    duration = Duration.ofSeconds(230)
                }.next().also { audioItemRepository.add(it) }
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
        playlistHierarchy.add(bestHits)
        playlistHierarchy.add(thisWeeksFavorites)
        playlistHierarchy.size() shouldBe 6
        playlistHierarchy.search { it.isDirectory.not() } shouldContainExactly setOf(rock, pop, thisWeeksFavorites)

        playlistHierarchy.search { it.isDirectory } shouldContainExactly setOf(fifties, sixties, bestHits)

        val fiftiesItems =
            listOf(
                Arb.audioItem {
                    title = "50s hit"
                    duration = Duration.ofSeconds(30)
                }.next().also { audioItemRepository.add(it) },
                Arb.audioItem {
                    title = "50s favorite song"
                    duration = Duration.ofSeconds(120)
                }.next().also { audioItemRepository.add(it) }
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
        playlistHierarchy.forEach { it.removeAudioItems(rockAudioItems) }
        playlistHierarchy.findById(rock.id) shouldBePresent { it.audioItems.isEmpty() shouldBe true }

        playlistHierarchy.clear()
        playlistHierarchy.isEmpty shouldBe true

        playlistHierarchy.close()
        RegistryBase.deregisterRepository(AudioItem::class.java)
        audioItemRepository.close()
    }

    /** The following playlist hierarchy is used for the test:

     ├──Best hits
     │  └──50s
     │     ├──Rock
     │     └──Pop
     └──Selection of playlists
     */
    "Moves playlists from/to playlist directories" {
        val playlistHierarchy = DefaultPlaylistHierarchy()

        val rock = playlistHierarchy.createPlaylist("Rock")
        playlistHierarchy.findByName(rock.name) shouldBePresent { it shouldBe rock }
        val pop = playlistHierarchy.createPlaylist("Pop")
        val fifties = playlistHierarchy.createPlaylistDirectory("50s")
        playlistHierarchy.addPlaylistsToDirectory(setOf(rock.name, pop.name), fifties.name)
        testDispatcher.scheduler.advanceUntilIdle()
        val bestHits = playlistHierarchy.createPlaylistDirectory("Best hits")
        playlistHierarchy.addPlaylistsToDirectory(setOf(fifties.name), bestHits.name)
        testDispatcher.scheduler.advanceUntilIdle()
        val selection = playlistHierarchy.createPlaylistDirectory("Selection of playlists")
        playlistHierarchy.size() shouldBe 5

        playlistHierarchy.movePlaylist(rock.name, selection.name)
        testDispatcher.scheduler.advanceUntilIdle()

        // ├──Best hits
        // │  └──50s
        // │     └──Pop
        // └──Selection of playlists
        //    └──Rock

        playlistHierarchy.size() shouldBe 5
        playlistHierarchy.findByName(selection.name) shouldBePresent { it.playlists.shouldContainOnly(rock) }
        playlistHierarchy.findById(fifties.id) shouldBePresent { it.playlists.shouldNotContain(rock) }
        playlistHierarchy.findByName(bestHits.name) shouldBePresent { it.playlists.shouldContainOnly(fifties) }

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

        playlistHierarchy.removeAll(setOf(bestHits)) shouldBe true

        playlistHierarchy.size() shouldBe 0
        playlistHierarchy.isEmpty shouldBe true

        // Recursive removal clears nested playlist delegates
        bestHits.playlists.isEmpty() shouldBe true
        fifties.playlists.isEmpty() shouldBe true
        selection.playlists.isEmpty() shouldBe true
        rock.playlists.isEmpty() shouldBe true
        pop.playlists.isEmpty() shouldBe true

        playlistHierarchy.close()
    }

    "Removing playlist directory from it is recursive and changes reflects on playlists" {
        val playlistHierarchy = DefaultPlaylistHierarchy()

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

        playlistHierarchy.close()
    }

    "Throws Exception when creating playlists with an existing name" {
        val playlistHierarchy = DefaultPlaylistHierarchy()

        playlistHierarchy.createPlaylistDirectory("New playlist")
        playlistHierarchy.size() shouldBe 1

        shouldThrowMessage("Playlist with name 'New playlist' already exists") { playlistHierarchy.createPlaylistDirectory("New playlist") }
        playlistHierarchy.size() shouldBe 1

        shouldThrowMessage("Playlist with name 'New playlist' already exists") { playlistHierarchy.createPlaylist("New playlist") }
        playlistHierarchy.size() shouldBe 1

        playlistHierarchy.close()
    }

    "Removing child playlist directly from one, does not remove them from the repository" {
        val playlistHierarchy = DefaultPlaylistHierarchy()

        val fifties = playlistHierarchy.createPlaylistDirectory("50s")
        val rock = playlistHierarchy.createPlaylistDirectory("Rock")
        val rockFavorites = playlistHierarchy.createPlaylist("Rock Favorites")
        playlistHierarchy.addPlaylistToDirectory(rockFavorites, rock.name)
        playlistHierarchy.addPlaylistToDirectory(rock, fifties.name)

        playlistHierarchy.size() shouldBe 3
        fifties.playlists.size shouldBe 1
        rock.playlists.size shouldBe 1

        // └──50s
        //    └──Rock
        //        └──Rock Favorites

        rock.clearPlaylists()

        playlistHierarchy.size() shouldBe 3
        rock.playlists.isEmpty() shouldBe true
        fifties.playlists.size shouldBe 1

        // └──50s
        //    └──Rock
        //
        // └ Rock Favorites (playlist remains in the playlistHierarchy)

        fifties.removePlaylist(rock.id)

        // └──50s
        //
        // └ Rock (playlist remains in the playlistHierarchy)
        // └ Rock Favorites

        playlistHierarchy.size() shouldBe 3
        fifties.playlists.isEmpty() shouldBe true

        playlistHierarchy.close()
    }

    "Deleting playlist from the repository removes it from any parent one" {
        val playlistHierarchy = DefaultPlaylistHierarchy()

        val rock = playlistHierarchy.createPlaylist("Rock")
        val fifties = playlistHierarchy.createPlaylistDirectory("50s")
        playlistHierarchy.addPlaylistToDirectory(rock, fifties.name)

        playlistHierarchy.size() shouldBe 2
        fifties.playlists.size shouldBe 1

        playlistHierarchy.remove(rock)

        playlistHierarchy.size() shouldBe 1
        fifties.playlists.isEmpty() shouldBe true

        playlistHierarchy.close()
    }
})