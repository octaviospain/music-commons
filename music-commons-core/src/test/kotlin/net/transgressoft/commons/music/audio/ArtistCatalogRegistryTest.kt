package net.transgressoft.commons.music.audio

import net.transgressoft.commons.event.CrudEvent
import net.transgressoft.commons.event.CrudEvent.Type.CREATE
import net.transgressoft.commons.event.CrudEvent.Type.DELETE
import net.transgressoft.commons.event.CrudEvent.Type.UPDATE
import net.transgressoft.commons.music.audio.ArbitraryAudioFile.realAudioFile
import com.neovisionaries.i18n.CountryCode
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import kotlin.time.Duration.Companion.milliseconds

internal class ArtistCatalogRegistryTest : BehaviorSpec({

    lateinit var registry: ArtistCatalogRegistry<AudioItem>

    given("An artist catalog registry") {
        registry = ArtistCatalogRegistry()

        When("an audio item that has the same album artist and artist is added") {
            val expectedAlbum = ImmutableAlbum("Play", ImmutableArtist.of("Moby", CountryCode.US))
            val audioFilePath =
                Arb.realAudioFile {
                    artist = ImmutableArtist.of("Moby", CountryCode.US)
                    album = expectedAlbum
                    trackNumber = 1
                    discNumber = 1
                }.next()
            var audioItem =
                MutableAudioItem(audioFilePath).also {
                    registry.addAudioItem(it)
                }

            then("the artist catalog should contain the artist and album with the audio item") {
                registry.size() shouldBe 1
                registry.contains { it.artist == audioItem.artist } shouldBe true
                registry.findByUniqueId(audioItem.artist.id()).isPresent shouldBe true
                registry.findFirst("Moby") shouldBePresent { artistCatalog ->
                    artistCatalog.artist should { it.name shouldBe "Moby" }
                    artistCatalog should {
                        it.artist shouldBe ImmutableArtist.of("Moby", CountryCode.US)
                        it.size shouldBe 1
                        it.albumAudioItems(expectedAlbum).shouldContainOnly(audioItem)
                    }
                }
            }

            and("the audio item updates fields that do not affect the artist catalog") {
                val audioItemBeforeChange = audioItem.clone()
                audioItem.apply {
                    title = "Natural Blues"
                    genre = Genre.ROCK
                    comments = "Comments"
                    bpm = 120f
                }

                registry.updateCatalog(audioItem, audioItemBeforeChange)

                then("the artist catalog remains the same") {
                    registry.findFirst(audioItem.artist.name) shouldBePresent { artistCatalog ->
                        artistCatalog.artist should { it.name shouldBe audioItem.artist.name }
                        artistCatalog should {
                            it.artist shouldBe audioItem.artist
                            it.size shouldBe 1
                            it.albumAudioItems(expectedAlbum).shouldContainOnly(audioItem)
                        }
                    }
                }
            }

            and("the audio item updates its artist and album artist without writing the metadata") {
                val audioItemBeforeChange = audioItem.clone()
                audioItem.apply {
                    artist = ImmutableArtist.of("Bjork", CountryCode.IS)
                    album = ImmutableAlbum(audioItem.album.name, artist!!)
                }

                registry.updateCatalog(audioItem, audioItemBeforeChange)

                then("the artist catalog should be updated") {
                    registry.findFirst("Bjork") shouldBePresent { artistCatalog ->
                        artistCatalog.artist should { it.name shouldBe "Bjork" }
                        artistCatalog should {
                            it.artist shouldBe ImmutableArtist.of("Bjork", CountryCode.IS)
                            it.size shouldBe 1
                            it.albumAudioItems(expectedAlbum).shouldContainOnly(audioItem)
                        }
                    }
                    registry.findFirst("Moby").isEmpty shouldBe true
                    registry.findAlbumAudioItems(ImmutableArtist.of("Moby", CountryCode.US), "Play").isEmpty() shouldBe true
                }
            }

            and("the audio item updates its track number and disc number") {
                val audioItemBeforeChange = audioItem.clone()
                audioItem.apply {
                    trackNumber = trackNumber!!.plus(1).toShort()
                    discNumber = discNumber!!.plus(1).toShort()
                }

                registry.updateCatalog(audioItem, audioItemBeforeChange)

                then("the album in the artist catalog should be updated") {
                    eventually(100.milliseconds) {
                        registry.findById(audioItem.artist) shouldBePresent { artistCatalog ->
                            artistCatalog.artist shouldBe audioItem.artist
                            artistCatalog should {
                                it.artist shouldBe audioItem.artist
                                it.size shouldBe 1
                                it.albumAudioItems(audioItem.album).shouldContainOnly(audioItem)
                            }
                        }
                    }
                }
            }

            and("the audio item is deleted") {
                registry.removeAudioItem(audioItem)

                then("the artist catalog should not contain the artist and album with the audio item") {
                    registry.isEmpty shouldBe true
                    registry.findById(audioItem.artist).isEmpty shouldBe true
                    registry.findAlbumAudioItems(audioItem.artist, audioItem.album.name).isEmpty() shouldBe true
                }
            }
        }

        When("album audio items are added") {
            val expectedArtist = ImmutableArtist.of("Pixies", CountryCode.UK)
            val expectedAlbum = ImmutableAlbum("Doolittle", ImmutableArtist.of("Pixies"))
            val pixiesAudioItems = Arb.albumAudioItems(expectedArtist, expectedAlbum).next()

            registry.addAudioItems(pixiesAudioItems)

            then("the artist catalog should contain only the artist and album with the audio items") {
                registry.size() shouldBe 1
                registry.contains { it.artist == expectedArtist } shouldBe true
                registry.findByUniqueId(expectedArtist.id()).isPresent shouldBe true
                registry.findAlbumAudioItems(expectedArtist, expectedAlbum.name).shouldContainOnly(pixiesAudioItems)
                registry.findFirst("Pixies") shouldBePresent { artistCatalog ->
                    artistCatalog.artist should { it.name shouldBe "Pixies" }
                    artistCatalog should {
                        it.artist shouldBe expectedArtist
                        it.size shouldBe pixiesAudioItems.size
                        it.albumAudioItems(expectedAlbum.name).shouldContainOnly(pixiesAudioItems)
                    }
                }
            }
        }

        When("getting an artist album set") {
            val expectedArtist = ImmutableArtist.of("Radiohead", CountryCode.UK)
            val expectedAlbum = ImmutableAlbum("OK Computer", ImmutableArtist.of("Radiohead", CountryCode.UK))
            val audioItems = Arb.albumAudioItems(expectedArtist, expectedAlbum).next()

            registry.addAudioItems(audioItems)

            then("the album set should contain the artist and album views with audio items") {
                registry.findById(expectedArtist) shouldBePresent { artistCatalog ->
                    artistCatalog.artist shouldBe expectedArtist
                    artistCatalog.albums.size shouldBe 1

                    artistCatalog.albums.forEach { albumSet ->
                        albumSet.albumName shouldBe expectedAlbum.name
                        albumSet shouldContainExactly audioItems
                    }
                }
            }
        }

        When("subscribing to CREATE events") {
            registry = ArtistCatalogRegistry()
            val receivedEvents = mutableListOf<CrudEvent<Artist, ArtistCatalog<AudioItem>>>()

            registry.subscribe(CREATE) { receivedEvents.add(it) }

            val expectedArtist = ImmutableArtist.of("Moby", CountryCode.US)
            val expectedAlbum = ImmutableAlbum("Play", expectedArtist)
            val audioFilePath =
                Arb.realAudioFile {
                    artist = expectedArtist
                    album = expectedAlbum
                }.next()
            val audioItem = MutableAudioItem(audioFilePath)

            registry.addAudioItem(audioItem)

            then("CREATE event should be emitted with the new catalog") {
                eventually(100.milliseconds) {
                    receivedEvents.size shouldBe 1
                    receivedEvents[0].entities.size shouldBe 1
                    receivedEvents[0].entities.values.first() should { catalog ->
                        catalog.artist shouldBe expectedArtist
                        catalog.size shouldBe 1
                        catalog.albumAudioItems(expectedAlbum.name).shouldContainOnly(audioItem)
                    }
                }
            }
        }

        When("subscribing to UPDATE events") {
            registry = ArtistCatalogRegistry()
            val receivedEvents = mutableListOf<CrudEvent<Artist, ArtistCatalog<AudioItem>>>()

            val expectedArtist = ImmutableArtist.of("Radiohead", CountryCode.UK)
            val expectedAlbum = ImmutableAlbum("OK Computer", expectedArtist)
            val audioFilePath =
                Arb.realAudioFile {
                    artist = expectedArtist
                    album = expectedAlbum
                    trackNumber = 1
                    discNumber = 1
                    genre = Genre.UNDEFINED
                }.next()
            val audioItem = MutableAudioItem(audioFilePath)

            registry.addAudioItem(audioItem)

            registry.subscribe(UPDATE) { receivedEvents.add(it) }

            and("an audio item is added to an existing artist catalog") {
                receivedEvents.clear()

                val secondAudioFilePath =
                    Arb.realAudioFile {
                        artist = expectedArtist
                        album = expectedAlbum
                        trackNumber = 2
                        discNumber = 1
                    }.next()
                val secondAudioItem = MutableAudioItem(secondAudioFilePath)

                registry.addAudioItem(secondAudioItem)

                then("UPDATE event should be emitted with the updated catalog") {
                    eventually(100.milliseconds) {
                        receivedEvents.size shouldBe 1
                        receivedEvents[0].entities.size shouldBe 1
                        receivedEvents[0].entities.values.first() should { catalog ->
                            catalog.artist shouldBe expectedArtist
                            catalog.size shouldBe 2
                            catalog.albumAudioItems(expectedAlbum.name).size shouldBe 2
                        }
                    }
                }
            }

            and("an audio item field is modified but it does not affect its artist catalog") {
                receivedEvents.clear()

                val audioItemBeforeChange = audioItem.clone()
                audioItem.genre = Genre.ROCK

                registry.updateCatalog(audioItem, audioItemBeforeChange)

                then("no UPDATE event should be emitted but the audio item should be updated") {
                    receivedEvents.isEmpty() shouldBe true
                    registry.findFirst {
                        it.artist == expectedArtist && it.albumAudioItems(expectedAlbum.name).contains(audioItem)
                    }
                }
            }

            and("an audio item's track number is updated") {
                receivedEvents.clear()

                val audioItemBeforeChange = audioItem.clone()
                audioItem.trackNumber = 5

                registry.updateCatalog(audioItem, audioItemBeforeChange)

                then("UPDATE event should be emitted with the re-sorted catalog") {
                    eventually(100.milliseconds) {
                        receivedEvents.size shouldBe 1
                        receivedEvents[0].entities.size shouldBe 1
                        receivedEvents[0].entities.values.first() should { catalog ->
                            catalog.artist shouldBe expectedArtist
                            catalog.size shouldBe 2
                            // After changing trackNumber from 1 to 5, the item should be reordered to the last position
                            catalog.albumAudioItems(expectedAlbum.name).last() shouldBe audioItem
                        }
                    }
                }
            }
        }

        When("subscribing to DELETE events") {
            registry = ArtistCatalogRegistry()
            val receivedEvents = mutableListOf<CrudEvent<Artist, ArtistCatalog<AudioItem>>>()

            val expectedArtist = ImmutableArtist.of("Bjork", CountryCode.IS)
            val expectedAlbum = ImmutableAlbum("Homogenic", expectedArtist)
            val audioFilePath =
                Arb.realAudioFile {
                    artist = expectedArtist
                    album = expectedAlbum
                }.next()
            val audioItem = MutableAudioItem(audioFilePath)

            registry.addAudioItem(audioItem)

            registry.subscribe(DELETE) { receivedEvents.add(it) }

            and("the last audio item of an artist is removed") {
                registry.removeAudioItem(audioItem)

                then("DELETE event should be emitted with the removed catalog") {
                    eventually(100.milliseconds) {
                        receivedEvents.size shouldBe 1
                        receivedEvents[0].entities.size shouldBe 1
                        receivedEvents[0].entities.values.first() should { catalog ->
                            catalog.artist shouldBe expectedArtist
                            catalog.isEmpty shouldBe true
                        }
                    }
                }
            }
        }

        When("multiple audio items from different artists are added") {
            registry = ArtistCatalogRegistry()
            val receivedEvents = mutableListOf<CrudEvent<Artist, ArtistCatalog<AudioItem>>>()

            registry.subscribe(CREATE) { receivedEvents.add(it) }

            val artist1 = ImmutableArtist.of("Pink Floyd", CountryCode.UK)
            val artist2 = ImmutableArtist.of("Led Zeppelin", CountryCode.UK)
            val album1 = ImmutableAlbum("The Wall", artist1)
            val album2 = ImmutableAlbum("IV", artist2)

            val audioItem1 =
                MutableAudioItem(
                    Arb.realAudioFile {
                        artist = artist1
                        album = album1
                    }.next()
                )
            val audioItem2 =
                MutableAudioItem(
                    Arb.realAudioFile {
                        artist = artist2
                        album = album2
                    }.next()
                )

            registry.addAudioItems(listOf(audioItem1, audioItem2))

            then("CREATE events should be emitted for each new artist catalog") {
                eventually(100.milliseconds) {
                    receivedEvents.size shouldBe 1
                    receivedEvents[0].entities.size shouldBe 2

                    val catalogs = receivedEvents[0].entities.values.toList()
                    catalogs.map { it.artist }.shouldContainOnly(artist1, artist2)
                }
            }
        }
    }
})