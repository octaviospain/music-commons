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
import java.time.Duration
import java.util.*
import kotlin.time.Duration.Companion.seconds

internal class AudioPlaylistJsonRepositoryTest : StringSpec({

    lateinit var audioPlaylistRepository: AudioPlaylistRepository<AudioItem, AudioPlaylist<AudioItem>>

    beforeEach {
        audioPlaylistRepository = AudioPlaylistJsonRepository()
    }

    "Repository serializes itself to file when playlists are created" {
        val jsonFile = tempfile("playlistRepository-serialization-test", ".json").also { it.deleteOnExit() }
        audioPlaylistRepository = AudioPlaylistJsonRepository.initialize(jsonFile)

        val rockAudioItem = arbitraryAudioItem(title = "50s Rock hit 1").next()
        val rockAudioItems = listOf(rockAudioItem)
        val rock = audioPlaylistRepository.createPlaylistDirectory("Rock", rockAudioItems)

        val rockFavAudioItem = arbitraryAudioItem(title = "Rock fav").next()
        val rockFavoritesAudioItems = listOf(rockFavAudioItem)
        val rockFavorites = audioPlaylistRepository.createPlaylist("Rock favorites", rockFavoritesAudioItems)

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
        val jsonFile = tempfile("playlistRepository-deserialization-test", ".json").also {
            it.writeText("""
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
            it.deleteOnExit()
        }

        val audioItem = arbitraryAudioItem(id = 453374921).next()
        val audioItemRepository = mock<AudioItemRepository<AudioItem>> {
            whenever(it.findById(eq(453374921))).thenReturn(Optional.of(audioItem))
        }

        audioPlaylistRepository = AudioPlaylistJsonRepository.loadFromFile(jsonFile, audioItemRepository)

        audioPlaylistRepository.shouldContainExactly(
            ImmutablePlaylist(1, true, "Rock", listOf(audioItem))
        )
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
        var rock = audioPlaylistRepository.createPlaylist("Rock", rockAudioItems)

        audioPlaylistRepository.findByName(rock.name) shouldBe rock
        val playlistsThatContainsAllAudioItemsWith50sInTitle =
            audioPlaylistRepository.search { it.audioItemsAllMatch { audioItem -> audioItem.title.contains("50s") } }
        playlistsThatContainsAllAudioItemsWith50sInTitle.shouldContainExactly(rock)

        val pop = audioPlaylistRepository.createPlaylist("Pop")
        audioPlaylistRepository.size() shouldBe 2
        audioPlaylistRepository.numberOfPlaylists() shouldBe 2

        var fifties = audioPlaylistRepository.createPlaylistDirectory("50s")
        audioPlaylistRepository.addPlaylistsToDirectory(setOf(pop, rock), fifties)
        audioPlaylistRepository.findByName(fifties.name)?.playlists.shouldContainExactly(pop, rock)
        audioPlaylistRepository.numberOfPlaylistDirectories() shouldBe 1

        var sixties = audioPlaylistRepository.createPlaylistDirectory("60s")
        sixties.playlists.isEmpty() shouldBe true
        audioPlaylistRepository.numberOfPlaylistDirectories() shouldBe 2
        audioPlaylistRepository.findByUniqueId(sixties.id.toString() + "-D-" + sixties.name) shouldBePresent { it shouldBe sixties }

        var bestHits = audioPlaylistRepository.createPlaylistDirectory("Best hits")
        audioPlaylistRepository.addPlaylistsToDirectory(setOf(fifties, sixties), bestHits)
        bestHits.playlists.isEmpty() shouldBe true  // Fails because bestHits is an old immutable copy
        audioPlaylistRepository.findByName(bestHits.name)?.playlists.shouldContainExactly(fifties, sixties)

        val thisWeeksFavorites = audioPlaylistRepository.createPlaylist("This weeks' favorites songs")
        audioPlaylistRepository.search { it.name.contains("favorites") }.shouldContainExactly(thisWeeksFavorites)
        audioPlaylistRepository.size() shouldBe 6
        audioPlaylistRepository.addOrReplaceAll(setOf(bestHits, thisWeeksFavorites))
        audioPlaylistRepository.size() shouldBe 6
        audioPlaylistRepository.search { it.isDirectory.not() }.shouldContainExactly(rock, pop, thisWeeksFavorites)

        fifties = audioPlaylistRepository.findByName(fifties.name)!!
        sixties = audioPlaylistRepository.findByName(sixties.name)!!
        bestHits = audioPlaylistRepository.findByName(bestHits.name)!!

        audioPlaylistRepository.search { it.isDirectory}.shouldContainExactly(fifties, sixties, bestHits)

        rock = audioPlaylistRepository.findById(rock.id).get()
        fifties = audioPlaylistRepository.findByName(fifties.name)!!
        val fiftiesItems = listOf(
            arbitraryAudioItem(title = "50s hit", duration = Duration.ofSeconds(30)).next(),
            arbitraryAudioItem(title = "50s favorite song", duration = Duration.ofSeconds(120)).next()
        )

        audioPlaylistRepository.addAudioItemsToPlaylist(fiftiesItems, fifties)
        val playlistsThatContainsAnyAudioItemsWithHitInTitle =
            audioPlaylistRepository.search { it.audioItemsAnyMatch { audioItem -> audioItem.title.contains("hit") } }
        fifties = audioPlaylistRepository.findByName(fifties.name)!!
        rock = audioPlaylistRepository.findByName(rock.name)!!
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
        val rock = audioPlaylistRepository.createPlaylist("Rock")
        audioPlaylistRepository.findByName(rock.name) shouldBe rock
        val pop = audioPlaylistRepository.createPlaylist("Pop")
        var fifties = audioPlaylistRepository.createPlaylistDirectory("50s")
        audioPlaylistRepository.addPlaylistsToDirectory(setOf(rock, pop), fifties)
        val bestHits = audioPlaylistRepository.createPlaylistDirectory("Best hits")
        audioPlaylistRepository.addPlaylistsToDirectory(setOf(fifties), bestHits)
        var selection = audioPlaylistRepository.createPlaylistDirectory("Selection of playlists")
        audioPlaylistRepository.size() shouldBe 5

        audioPlaylistRepository.movePlaylist(rock, selection)

        // ├──Best hits
        // │  └──50s
        // │     └──Pop
        // └──Selection of playlists
        //    └──Rock

        audioPlaylistRepository.size() shouldBe 5
        audioPlaylistRepository.findByName(selection.name)?.playlists.shouldContainExactly(rock)
        audioPlaylistRepository.findById(fifties.id) shouldBePresent { it.playlists.shouldNotContain(rock) }

        // --

        fifties = audioPlaylistRepository.findByName(fifties.name)!!
        selection = audioPlaylistRepository.findByName(selection.name)!!

        audioPlaylistRepository.movePlaylist(selection, fifties)

        // └──Best hits
        //    └──50s
        //       ├──Pop
        //       └──Selection of playlists
        //          └──Rock

        audioPlaylistRepository.size() shouldBe 5
        audioPlaylistRepository.findByName(selection.name)?.playlists.shouldContainExactly(rock)
        audioPlaylistRepository.findByName(fifties.name)?.playlists.shouldContainExactly(pop, selection)
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

    "Add playlists not created with the repository" {
        audioPlaylistRepository.createPlaylist("Best hits")
        audioPlaylistRepository.createPlaylistDirectory("Nina Simone discography")
        audioPlaylistRepository.size() shouldBe 2

        val bestHits = MutablePlaylist(9, false,"Best hits")
        val rock = MutablePlaylist(10, false, "Best hits - Rock")

        audioPlaylistRepository.addOrReplaceAll(setOf(bestHits, rock))
        audioPlaylistRepository.size() shouldBe 3

        val ninaSimoneDiscography = ImmutablePlaylist(11, true, "Nina Simone discography")
        val revolver = MutablePlaylist(12, false, "Revolver")
        val beatlesDiscography = ImmutablePlaylist(13, true, "The Beatles' discography", emptyList(), setOf(revolver))

        audioPlaylistRepository.addOrReplaceAll(setOf(ninaSimoneDiscography, beatlesDiscography))

        audioPlaylistRepository.size() shouldBe 5
        audioPlaylistRepository.findByName(beatlesDiscography.name)?.playlists?.isNotEmpty() shouldBe true
    }
})