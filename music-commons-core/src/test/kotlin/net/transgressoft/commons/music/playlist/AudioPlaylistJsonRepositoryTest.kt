package net.transgressoft.commons.music.playlist

import io.kotest.assertions.throwables.shouldThrowMessage
import io.kotest.assertions.timing.eventually
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.AudioItemRepository
import net.transgressoft.commons.music.audio.AudioItemTestUtil.arbitraryAudioItem
import org.mockito.ArgumentMatchers.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.time.Duration
import java.util.*
import kotlin.time.Duration.Companion.seconds

internal class AudioPlaylistJsonRepositoryTest : StringSpec({

    lateinit var jsonFile: File
    lateinit var audioPlaylistRepository: AudioPlaylistRepository<AudioItem, MutableAudioPlaylist<AudioItem>>

    beforeEach {
        jsonFile = tempfile("playlistRepository-test", ".json").also { it.deleteOnExit() }
        audioPlaylistRepository = AudioPlaylistJsonRepository(jsonFile)
    }

    "Repository serializes itself to file when playlists are added" {
        val rockAudioItem = arbitraryAudioItem(title = "50s Rock hit 1").next()
        val rockAudioItems = listOf(rockAudioItem)
        val rock = audioPlaylistRepository.addPlaylist(ImmutablePlaylistDirectory("Rock", rockAudioItems))

        val rockFavAudioItem = arbitraryAudioItem(title = "Rock fav").next()
        val rockFavoritesAudioItems = listOf(rockFavAudioItem)
        val rockFavorites = audioPlaylistRepository.addPlaylist(ImmutablePlaylist("Rock favorites", rockFavoritesAudioItems))

        audioPlaylistRepository.movePlaylist(rockFavorites, rock)

        audioPlaylistRepository.findById(rock.id) shouldBePresent {updatedRock ->
            updatedRock.playlists.shouldContainOnly(rockFavorites)
            updatedRock.id shouldBe rock.id
            updatedRock.name shouldBe "Rock"
            updatedRock.audioItems.shouldContainExactly(rockAudioItems)
            updatedRock.playlists.shouldContainOnly(rockFavorites)
        }

        eventually(2.seconds) {
            jsonFile.readText() shouldBe """
            [
                {
                    "id": ${rock.id},
                    "isDirectory": ${rock.isDirectory},
                    "name": "${rock.name}",
                    "audioItemIds": [
                        ${rockAudioItem.id}
                    ],
                    "playlistIds": [
                        ${rockFavorites.id}
                    ]
                },
                {
                    "id": ${rockFavorites.id},
                    "isDirectory": ${rockFavorites.isDirectory},
                    "name": "${rockFavorites.name}",
                    "audioItemIds": [
                        ${rockFavAudioItem.id}
                    ],
                    "playlistIds": [
                    ]
                }
            ]
        """.trimIndent()
        }
    }

    "Existing repository loads from file" {
        jsonFile.writeText("""
            [
                {
                    "id": 1,
                    "isDirectory": true,
                    "name": "Rock",
                    "audioItemIds": [
                        453374921
                    ],
                    "playlistIds": [
                    ]
                }
            ]
        """.trimIndent())

        val audioItem = arbitraryAudioItem(id = 453374921).next()
        val audioItemRepository = mock<AudioItemRepository<AudioItem>> {
            whenever(it.findById(eq(453374921))).thenReturn(Optional.of(audioItem))
        }

        audioPlaylistRepository = AudioPlaylistJsonRepository(jsonFile, audioItemRepository)

        audioPlaylistRepository.size() shouldBe 1
        audioPlaylistRepository.contains { it.id == 1 && it.isDirectory && it.name == "Rock" && it.audioItems == listOf(audioItem) && it.playlists.isEmpty()} shouldBe true
    }

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
    "Mixed playlists hierarchy structure and audio items search" {
        val rockAudioItems = listOf(
            arbitraryAudioItem(title = "50s Rock hit 1", duration = Duration.ofSeconds(60)).next(),
            arbitraryAudioItem(title = "50s Rock hit 2 my fav", duration = Duration.ofSeconds(230)).next()
        )
        var rock = audioPlaylistRepository.addPlaylist(ImmutablePlaylist("Rock", rockAudioItems))

        audioPlaylistRepository.findByName(rock.name) shouldBe rock
        val playlistsThatContainsAllAudioItemsWith50sInTitle =
            audioPlaylistRepository.search { it.audioItemsAllMatch { audioItem -> audioItem.title.contains("50s") } }
        playlistsThatContainsAllAudioItemsWith50sInTitle.shouldContainExactly(rock)

        val pop = audioPlaylistRepository.addPlaylist(ImmutablePlaylist("Pop"))
        audioPlaylistRepository.size() shouldBe 2
        audioPlaylistRepository.numberOfPlaylists() shouldBe 2

        var fifties = audioPlaylistRepository.addPlaylist(ImmutablePlaylist("50s"))
        audioPlaylistRepository.addPlaylistsToDirectory(setOf(pop, rock), fifties)
        audioPlaylistRepository.findByName(fifties.name) shouldBePresent { it.playlists.shouldContainExactly(pop, rock) }
        audioPlaylistRepository.numberOfPlaylistDirectories() shouldBe 1

        var sixties = audioPlaylistRepository.addPlaylist(ImmutablePlaylistDirectory("60s"))
        sixties.playlists.isEmpty() shouldBe true
        audioPlaylistRepository.numberOfPlaylistDirectories() shouldBe 2
        audioPlaylistRepository.findByUniqueId(sixties.id.toString() + "-D-" + sixties.name) shouldBePresent { it shouldBe sixties }

        var bestHits = audioPlaylistRepository.addPlaylist(ImmutablePlaylistDirectory("Best hits"))
        audioPlaylistRepository.addPlaylistsToDirectory(setOf(fifties, sixties), bestHits)
        bestHits.playlists.isEmpty() shouldBe true  // Fails because bestHits is an old immutable copy
        audioPlaylistRepository.findByName(bestHits.name) shouldBePresent { it.playlists.shouldContainExactly(fifties, sixties) }

        val thisWeeksFavorites = audioPlaylistRepository.addPlaylist(ImmutablePlaylist("This weeks' favorites songs"))
        audioPlaylistRepository.search { it.name.contains("favorites") }.shouldContainExactly(thisWeeksFavorites)
        audioPlaylistRepository.size() shouldBe 6
        audioPlaylistRepository.addOrReplaceAll(setOf(bestHits, thisWeeksFavorites))
        audioPlaylistRepository.size() shouldBe 6
        audioPlaylistRepository.search { it.isDirectory.not() }.shouldContainExactly(rock, pop, thisWeeksFavorites)

        fifties = audioPlaylistRepository.findByName(fifties.name).get()
        sixties = audioPlaylistRepository.findByName(sixties.name).get()
        bestHits = audioPlaylistRepository.findByName(bestHits.name).get()

        audioPlaylistRepository.search { it.isDirectory}.shouldContainExactly(fifties, sixties, bestHits)

        rock = audioPlaylistRepository.findById(rock.id).get()
        fifties = audioPlaylistRepository.findByName(fifties.name).get()
        val fiftiesItems = listOf(
            arbitraryAudioItem(title = "50s hit", duration = Duration.ofSeconds(30)).next(),
            arbitraryAudioItem(title = "50s favorite song", duration = Duration.ofSeconds(120)).next()
        )

        audioPlaylistRepository.addAudioItemsToPlaylist(fiftiesItems, fifties)
        val playlistsThatContainsAnyAudioItemsWithHitInTitle =
            audioPlaylistRepository.search { it.audioItemsAnyMatch { audioItem -> audioItem.title.contains("hit") } }
        fifties = audioPlaylistRepository.findByName(fifties.name).get()
        rock = audioPlaylistRepository.findByName(rock.name).get()
        playlistsThatContainsAnyAudioItemsWithHitInTitle.shouldContainExactly(rock, fifties)
        val playlistsThatContainsAudioItemsWithDurationBelow60 = audioPlaylistRepository.search {
            it.audioItemsAnyMatch { audioItem: AudioItem -> audioItem.duration <= Duration.ofSeconds(60) }
        }

        playlistsThatContainsAudioItemsWithDurationBelow60.shouldContainExactly(rock, fifties)

        audioPlaylistRepository.removeAudioItemsFromPlaylist(fiftiesItems, fifties)

        audioPlaylistRepository.findById(fifties.id) shouldBePresent { it.audioItems.isEmpty() shouldBe true }
        audioPlaylistRepository.removeAudioItems(rockAudioItems)
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
        val rock = audioPlaylistRepository.addPlaylist(ImmutablePlaylist("Rock"))
        audioPlaylistRepository.findByName(rock.name) shouldBe rock
        val pop = audioPlaylistRepository.addPlaylist(ImmutablePlaylist("Pop"))
        var fifties = audioPlaylistRepository.addPlaylist(ImmutablePlaylistDirectory("50s"))
        audioPlaylistRepository.addPlaylistsToDirectory(setOf(rock, pop), fifties)
        val bestHits = audioPlaylistRepository.addPlaylist(ImmutablePlaylistDirectory("Best hits"))
        audioPlaylistRepository.addPlaylistsToDirectory(setOf(fifties), bestHits)
        var selection = audioPlaylistRepository.addPlaylist(ImmutablePlaylistDirectory("Selection of playlists"))
        audioPlaylistRepository.size() shouldBe 5

        audioPlaylistRepository.movePlaylist(rock, selection)

        // ├──Best hits
        // │  └──50s
        // │     └──Pop
        // └──Selection of playlists
        //    └──Rock

        audioPlaylistRepository.size() shouldBe 5
        audioPlaylistRepository.findByName(selection.name) shouldBePresent { it.playlists.shouldContainExactly(rock) }
        audioPlaylistRepository.findById(fifties.id) shouldBePresent { it.playlists.shouldNotContain(rock) }

        // --

        fifties = audioPlaylistRepository.findByName(fifties.name).get()
        selection = audioPlaylistRepository.findByName(selection.name).get()

        audioPlaylistRepository.movePlaylist(selection, fifties)

        // └──Best hits
        //    └──50s
        //       ├──Pop
        //       └──Selection of playlists
        //          └──Rock

        audioPlaylistRepository.size() shouldBe 5
        audioPlaylistRepository.findByName(selection.name) shouldBePresent { it.playlists.shouldContainExactly(rock) }
        audioPlaylistRepository.findByName(fifties.name) shouldBePresent { it.playlists.shouldContainExactly(pop, selection) }
    }

    "Create playlists with existing name" {
        val newPlaylistDirectory = audioPlaylistRepository.addPlaylist(ImmutablePlaylistDirectory("New playlist"))

        shouldThrowMessage("Playlist with name 'New playlist' already exists") { audioPlaylistRepository.addPlaylist(ImmutablePlaylistDirectory("New playlist")) }
        audioPlaylistRepository.size() shouldBe 1

        shouldThrowMessage("Playlist with name 'New playlist' already exists") { audioPlaylistRepository.addPlaylist(ImmutablePlaylist("New playlist")) }
        audioPlaylistRepository.size() shouldBe 1

        audioPlaylistRepository.remove(newPlaylistDirectory) shouldBe true
        audioPlaylistRepository.isEmpty shouldBe true
    }
})