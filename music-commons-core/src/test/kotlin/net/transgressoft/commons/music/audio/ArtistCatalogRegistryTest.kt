package net.transgressoft.commons.music.audio

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
            var audioItem: AudioItem =
                MutableAudioItem(audioFilePath).also {
                    registry.addAudioItems(listOf(it))
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
                        it.albumAudioItems(expectedAlbum.name).shouldContainOnly(audioItem)
                    }
                }
            }

            and("the audio item updates fields that do not affect the artist catalog") {
                val audioItemBeforeChange = MutableAudioItem(audioItem)
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
                            it.albumAudioItems(expectedAlbum.name).shouldContainOnly(audioItem)
                        }
                    }
                }
            }

            and("the audio item updates its artist and album artist without writing the metadata") {
                val audioItemBeforeChange = MutableAudioItem(audioItem)
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
                            it.albumAudioItems(expectedAlbum.name).shouldContainOnly(audioItem)
                        }
                    }
                    registry.findFirst("Moby").isEmpty shouldBe true
                    registry.findAlbumAudioItems(ImmutableArtist.of("Moby", CountryCode.US), "Play").isEmpty() shouldBe true
                }
            }

            and("the audio item updates its track number and disc number") {
                val audioItemBeforeChange = MutableAudioItem(audioItem)
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
                                it.albumAudioItems(audioItem.album.name).shouldContainOnly(audioItem)
                            }
                        }
                    }
                }
            }

            and("the audio item is deleted") {
                registry.removeAudioItems(listOf(audioItem))

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
    }
})