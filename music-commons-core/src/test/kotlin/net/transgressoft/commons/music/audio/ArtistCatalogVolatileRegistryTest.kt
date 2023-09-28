package net.transgressoft.commons.music.audio

import net.transgressoft.commons.music.audio.AudioItemTestUtil.arbitraryAudioItem
import com.neovisionaries.i18n.CountryCode
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import kotlin.time.Duration.Companion.seconds

internal class ArtistCatalogVolatileRegistryTest : BehaviorSpec({

    lateinit var registry: ArtistCatalogVolatileRegistry

    fun testAudioItem(artistName: String, albumName: String, albumArtistName: String = artistName): MutableAudioItem =
        arbitraryAudioItem {
            artist = ImmutableArtist.of(artistName, CountryCode.US)
            album = ImmutableAlbum(albumName, ImmutableArtist.of(albumArtistName, CountryCode.US))
            trackNumber = 1
            discNumber = 1
        }.next()

    given("An artist catalog registry") {
        registry = ArtistCatalogVolatileRegistry()

        When("an audio item that has the same album artist and artist is added") {
            val audioItem = testAudioItem("Moby", "Play")
            registry.addAudioItems(listOf(audioItem))

            then("the artist catalog should contain the artist and album with the audio item") {
                eventually(1.seconds) {
                    val album = audioItem.album

                    registry.size() shouldBe 1
                    registry.contains { it.artist == audioItem.artist } shouldBe true
                    registry.findByUniqueId(audioItem.artist.id()).isPresent shouldBe true
                    registry.findFirst("Moby") shouldBePresent { artistCatalog ->
                        artistCatalog.artist should { it.name shouldBe "Moby" }
                        artistCatalog should {
                            it.artist shouldBe ImmutableArtist.of("Moby", CountryCode.US)
                            it.size shouldBe 1
                            it.findAlbum(album) shouldBePresent { extendedAlbum ->
                                extendedAlbum.name shouldBe "Play"
                                extendedAlbum.albumArtist shouldBe ImmutableArtist.of("Moby", CountryCode.US)
                                extendedAlbum.isCompilation shouldBe album.isCompilation
                                extendedAlbum.year shouldBe album.year
                                extendedAlbum.label shouldBe album.label
                                extendedAlbum.audioItems.shouldContainOnly(audioItem)
                            }
                        }
                    }
                }
            }

            When("the audio item updates its title, path, genre, comments and bpm") {
                audioItem.title = "Natural Blues"
                registry.addAudioItems(listOf(audioItem))

                then("the artist catalog remains the same") {
                    eventually(1.seconds) {
                        val album = audioItem.album

                        registry.findFirst(audioItem.artist.name) shouldBePresent { artistCatalog ->
                            artistCatalog.artist should { it.name shouldBe audioItem.artist.name }
                            artistCatalog should {
                                it.artist shouldBe audioItem.artist
                                it.size shouldBe 1
                                it.findAlbum(album) shouldBePresent { extendedAlbum ->
                                    extendedAlbum.audioItems.shouldContainOnly(audioItem)
                                }
                            }
                        }
                    }
                }
            }

            When("the audio item updates its artist and album artist") {
                val audioItemBeforeChange = InternalMutableAudioItem(audioItem)
                audioItem.artist = ImmutableArtist.of("Bjork", CountryCode.IS)
                audioItem.album = ImmutableAlbum(audioItem.album.name, audioItem.artist)

                registry.updateCatalog(audioItem, audioItemBeforeChange)

                then("the artist catalog should be updated") {
                    eventually(1.seconds) {
                        val album = audioItem.album

                        registry.findFirst("Bjork") shouldBePresent { artistCatalog ->
                            artistCatalog.artist should { it.name shouldBe "Bjork" }
                            artistCatalog should {
                                it.artist shouldBe ImmutableArtist.of("Bjork", CountryCode.IS)
                                it.size shouldBe 1
                                it.findAlbum(album) shouldBePresent { extendedAlbum ->
                                    extendedAlbum.name shouldBe audioItem.album.name
                                    extendedAlbum.albumArtist shouldBe ImmutableArtist.of("Bjork", CountryCode.IS)
                                    extendedAlbum.isCompilation shouldBe album.isCompilation
                                    extendedAlbum.year shouldBe album.year
                                    extendedAlbum.label shouldBe album.label
                                    extendedAlbum.audioItems.shouldContainOnly(audioItem)
                                }
                            }
                        }
                    }
                }
            }

            When("the audio item updates its track number and disc number") {
                val audioItemBeforeChange = InternalMutableAudioItem(audioItem)
                audioItem.trackNumber = audioItem.trackNumber!!.plus(1).toShort()
                audioItem.discNumber = audioItem.discNumber!!.plus(1).toShort()
                registry.updateCatalog(audioItem, audioItemBeforeChange)

                xthen("the album in the artist catalog should be updated") {
                    eventually(1.seconds) {
                        registry.findFirst(audioItem.artist) shouldBePresent { artistCatalog ->
                            artistCatalog.artist shouldBe audioItem.artist
                            artistCatalog should {
                                it.artist shouldBe audioItem.artist
                                it.size shouldBe 1
                                it.findAlbum(audioItem.album) shouldBePresent { extendedAlbum ->
                                    extendedAlbum.audioItems.shouldContainExactly(audioItem)
                                }
                            }
                        }
                    }
                }
            }

            When("the audio item is deleted") {
                registry.removeAudioItems(listOf(audioItem))

                then("the artist catalog should not contain the artist and album with the audio item") {
                    eventually(1.seconds) {
                        registry.isEmpty shouldBe true
                        registry.findFirst(audioItem.artist).isEmpty shouldBe true
                        registry.findAlbum(audioItem.album.name, audioItem.artist).isEmpty shouldBe true
                    }
                }
            }
        }
    }
})
