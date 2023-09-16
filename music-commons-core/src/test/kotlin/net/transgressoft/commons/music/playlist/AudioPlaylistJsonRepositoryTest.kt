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
        val rock = audioPlaylistRepository.createPlaylist("Rock", rockAudioItems)

        val rockFavAudioItem = arbitraryAudioItem(title = "Rock fav").next()
        val rockFavoritesAudioItems = listOf(rockFavAudioItem)
        val rockFavorites = audioPlaylistRepository.createPlaylist("Rock favorites", rockFavoritesAudioItems)

        audioPlaylistRepository.movePlaylist(rockFavorites.name, rock.name)

        audioPlaylistRepository.findById(rock.id) shouldBePresent { updatedRock ->
            updatedRock.playlists.shouldContainOnly(rockFavorites)
            updatedRock.id shouldBe rock.id
            updatedRock.name shouldBe "Rock"
            updatedRock.audioItems.shouldContainExactly(rockAudioItems)
            updatedRock.playlists.shouldContainOnly(rockFavorites)
        }

        eventually(2.seconds) {
            jsonFile.readText() shouldBe """
            {
                "${rock.id}": {
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
                "${rockFavorites.id}": {
                    "id": ${rockFavorites.id},
                    "isDirectory": ${rockFavorites.isDirectory},
                    "name": "${rockFavorites.name}",
                    "audioItemIds": [
                        ${rockFavAudioItem.id}
                    ],
                    "playlistIds": [
                    ]
                }
            }
            """.trimIndent()
        }
    }

    "Existing repository loads from file" {
        jsonFile.writeText(
            """
        {
            "1": {
                "id": 1,
                "isDirectory": true,
                "name": "Rock",
                "audioItemIds": [
                    453374921
                ],
                "playlistIds": [
                ]
            }
        }
        """.trimIndent()
        )

        val audioItem = arbitraryAudioItem(id = 453374921).next()
        val audioItemRepository = mock<AudioItemRepository<AudioItem>> {
            whenever(it.findById(eq(453374921))).thenReturn(Optional.of(audioItem))
        }

        audioPlaylistRepository = AudioPlaylistJsonRepository(jsonFile, audioItemRepository)

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
            arbitraryAudioItem(title = "50s Rock hit 1", duration = Duration.ofSeconds(60)).next(),
            arbitraryAudioItem(title = "50s Rock hit 2 my fav", duration = Duration.ofSeconds(230)).next()
        )
        val rock = audioPlaylistRepository.createPlaylist("Rock", rockAudioItems)

        audioPlaylistRepository.findByName(rock.name) shouldBePresent { it shouldBe rock }
        val playlistsThatContainsAllAudioItemsWith50sInTitle =
            audioPlaylistRepository.search { it.audioItemsAllMatch { audioItem -> audioItem.title.contains("50s") } }
        playlistsThatContainsAllAudioItemsWith50sInTitle.shouldContainExactly(rock)

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
            arbitraryAudioItem(title = "50s hit", duration = Duration.ofSeconds(30)).next(),
            arbitraryAudioItem(title = "50s favorite song", duration = Duration.ofSeconds(120)).next()
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
        audioPlaylistRepository.runMatching({ true }) { it.removeAudioItems(rockAudioItems) }
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
        audioPlaylistRepository.findByName(rock.name) shouldBePresent { it shouldBe  rock }
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
        audioPlaylistRepository.findByName(selection.name) shouldBePresent { it.playlists.shouldContainExactly(rock) }
        audioPlaylistRepository.findById(fifties.id) shouldBePresent { it.playlists.shouldNotContain(rock) }
        audioPlaylistRepository.findByName(bestHits.name) shouldBePresent { it.playlists.shouldContainExactly(fifties) }

        eventually(2.seconds) {
            jsonFile.readText() shouldBe """
            {
                "${rock.id}": {
                    "id": ${rock.id},
                    "isDirectory": ${rock.isDirectory},
                    "name": "${rock.name}",
                    "audioItemIds": [
                    ],
                    "playlistIds": [
                    ]
                },
                "${pop.id}": {
                    "id": ${pop.id},
                    "isDirectory": ${pop.isDirectory},
                    "name": "${pop.name}",
                    "audioItemIds": [
                    ],
                    "playlistIds": [
                    ]
                },
                "${fifties.id}": {
                    "id": ${fifties.id},
                    "isDirectory": ${fifties.isDirectory},
                    "name": "${fifties.name}",
                    "audioItemIds": [
                    ],
                    "playlistIds": [
                        ${pop.id}
                    ]
                },
                "${bestHits.id}": {
                    "id": ${bestHits.id},
                    "isDirectory": ${bestHits.isDirectory},
                    "name": "${bestHits.name}",
                    "audioItemIds": [
                    ],
                    "playlistIds": [
                        ${fifties.id}
                    ]
                },
                "${selection.id}": {
                    "id": ${selection.id},
                    "isDirectory": ${selection.isDirectory},
                    "name": "${selection.name}",
                    "audioItemIds": [
                    ],
                    "playlistIds": [
                        ${rock.id}
                    ]
                }
            }
            """.trimIndent()
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
        audioPlaylistRepository.findByName(selection.name) shouldBePresent { it.playlists.shouldContainExactly(rock) }
        audioPlaylistRepository.findByName(fifties.name) shouldBePresent { it.playlists shouldContainExactly setOf(pop, selection) }

        eventually(2.seconds) {
            jsonFile.readText() shouldBe """
            {
                "${rock.id}": {
                    "id": ${rock.id},
                    "isDirectory": ${rock.isDirectory},
                    "name": "${rock.name}",
                    "audioItemIds": [
                    ],
                    "playlistIds": [
                    ]
                },
                "${pop.id}": {
                    "id": ${pop.id},
                    "isDirectory": ${pop.isDirectory},
                    "name": "${pop.name}",
                    "audioItemIds": [
                    ],
                    "playlistIds": [
                    ]
                },
                "${fifties.id}": {
                    "id": ${fifties.id},
                    "isDirectory": ${fifties.isDirectory},
                    "name": "${fifties.name}",
                    "audioItemIds": [
                    ],
                    "playlistIds": [
                        ${pop.id},
                        ${selection.id}
                    ]
                },
                "${bestHits.id}": {
                    "id": ${bestHits.id},
                    "isDirectory": ${bestHits.isDirectory},
                    "name": "${bestHits.name}",
                    "audioItemIds": [
                    ],
                    "playlistIds": [
                        ${fifties.id}
                    ]
                },
                "${selection.id}": {
                    "id": ${selection.id},
                    "isDirectory": ${selection.isDirectory},
                    "name": "${selection.name}",
                    "audioItemIds": [
                    ],
                    "playlistIds": [
                        ${rock.id}
                    ]
                }
            }
            """.trimIndent()
        }

        audioPlaylistRepository.removeAll(setOf(bestHits))

        audioPlaylistRepository.size() shouldBe 0
        audioPlaylistRepository.isEmpty shouldBe true
        bestHits.playlists.shouldContainExactly(fifties)
        fifties.playlists shouldContainExactly setOf(pop, selection)
        selection.playlists.shouldContainExactly(rock)
        rock.playlists.isEmpty() shouldBe true
        pop.playlists.isEmpty() shouldBe true

        eventually(2.seconds) {
            jsonFile.readText() shouldBe "{\n}"
        }

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
        audioPlaylistRepository.findByName(selection.name) shouldBePresent { it.playlists.shouldContainExactly(rock) }
        audioPlaylistRepository.findById(fifties.id) shouldBePresent { it.playlists.shouldNotContain(rock) }
        audioPlaylistRepository.findByName(bestHits.name) shouldBePresent { it.playlists.shouldContainExactly(fifties) }

        audioPlaylistRepository.removePlaylistFromDirectory(fifties.name, bestHits.name)

        // ├──Best hits
        // └──Selection of playlists
        //    └──Rock

        audioPlaylistRepository.size() shouldBe 3
        audioPlaylistRepository.findByName(pop.name).isEmpty shouldBe true
        audioPlaylistRepository.findByUniqueId(fifties.uniqueId).isEmpty shouldBe true
        bestHits.playlists.isEmpty() shouldBe true
        fifties.playlists.shouldContainExactly(pop)

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