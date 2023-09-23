package net.transgressoft.commons.music.audio

import net.transgressoft.commons.data.DataEvent
import net.transgressoft.commons.data.StandardDataEvent.Type.*
import net.transgressoft.commons.data.StandardDataEventPublisher
import net.transgressoft.commons.data.of
import net.transgressoft.commons.event.EventType
import net.transgressoft.commons.event.TransEventSubscriberBase
import net.transgressoft.commons.music.audio.AudioItemTestUtil.arbitraryAudioItem
import com.neovisionaries.i18n.CountryCode
import io.kotest.assertions.timing.eventually
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import kotlin.time.Duration.Companion.seconds


internal class ArtistCatalogVolatileRegistryTest : BehaviorSpec({

    class TestAudioItemEventPublisher(override val name: String = "Audio items publisher") : StandardDataEventPublisher<Int, MutableAudioItem>() {
        fun publishCreateEvent(artistName: String, albumName: String, albumArtistName: String = artistName): MutableAudioItem =
            arbitraryAudioItem(
                artist = ImmutableArtist.of(artistName, CountryCode.US),
                album = ImmutableAlbum(albumName, ImmutableArtist.of(albumArtistName, CountryCode.US)),
                trackNumber = 1,
                discNumber = 1,
            ).next().also { putCreateEvent(it) }

        fun publishDeleteEvent(audioItem: MutableAudioItem) = putDeleteEvent(audioItem)

        fun publishUpdateEvent(audioItem: MutableAudioItem, audioItemUpdate: (MutableAudioItem) -> MutableAudioItem): MutableAudioItem {
            val audioItemBeforeUpdate = InternalMutableAudioItem(audioItem)
            return audioItemUpdate(audioItem).also { putUpdateEvent(it, audioItemBeforeUpdate) }
        }
    }

    class TestClassSubscribedToArtistCatalog(override val name: String = "Subscriber to changes in catalog") :
        TransEventSubscriberBase<ArtistCatalog, DataEvent<String, ArtistCatalog>>() {
        val receivedEvents = mutableMapOf<EventType, MutableList<DataEvent<String, ArtistCatalog>>>()

        init {
            addOnNextEventAction(CREATE, UPDATE, DELETE) { event ->
                receivedEvents.merge(event.type, mutableListOf(event)) { _, new -> new.apply { add(event) } }
            }
        }
    }

    val subscriber = TestClassSubscribedToArtistCatalog()
    val audioItemEventPublisher = TestAudioItemEventPublisher()
    lateinit var registry: ArtistCatalogRegistry

    afterEach {
        subscriber.receivedEvents.clear()
    }

    given("An artist catalog registry subscribed to an audio item repository") {
        registry = ArtistCatalogVolatileRegistry().also {
            audioItemEventPublisher.subscribe(it)
            it.subscribe(subscriber)
        }

        When("an audio item that has the same album artist and artist is published") {
            val audioItem = audioItemEventPublisher.publishCreateEvent("Moby", "Play")

            then("the artist catalog should contain the artist and album with the audio item") {
                eventually(1.seconds) {
                    val album = audioItem.album

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

                        subscriber.receivedEvents shouldBe mutableMapOf(
                            CREATE to mutableListOf(CREATE.of(mapOf("Moby-US" to artistCatalog)))
                        )
                    }
                }
            }

            When("the audio item updates its title, path, genre, comments and bpm") {
                val updatedAudioItem = audioItemEventPublisher.publishUpdateEvent(audioItem) {
                    it.apply { title = "Natural Blues" }
                }

                then("the artist catalog remains the same") {
                    eventually(1.seconds) {
                        val album = updatedAudioItem.album

                        registry.findFirst(audioItem.artist.name) shouldBePresent { artistCatalog ->
                            artistCatalog.artist should { it.name shouldBe audioItem.artist.name }
                            artistCatalog should {
                                it.artist shouldBe audioItem.artist
                                it.size shouldBe 1
                                it.findAlbum(album) shouldBePresent { extendedAlbum ->
                                    extendedAlbum.audioItems.shouldContainOnly(updatedAudioItem)
                                }
                            }

                            subscriber.receivedEvents.isEmpty() shouldBe true
                        }
                    }
                }
            }

            When("the audio item updates its artist and album artist") {
                val artistCatalogBeforeUpdate = registry.findFirst(audioItem.artist).get()

                val updatedAudioItem = audioItemEventPublisher.publishUpdateEvent(audioItem) {
                    it.apply {
                        artist = ImmutableArtist.of("Bjork", CountryCode.IS)
                        album = ImmutableAlbum(album.name, artist)
                    }
                }

                then("the artist catalog should be updated") {
                    eventually(1.seconds) {
                        val album = updatedAudioItem.album

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
                                    extendedAlbum.audioItems.shouldContainOnly(updatedAudioItem)
                                }
                            }

                            subscriber.receivedEvents shouldBe mutableMapOf(
                                DELETE to mutableListOf(DELETE.of(mapOf("Moby-US" to artistCatalogBeforeUpdate))),
                                CREATE to mutableListOf(CREATE.of(mapOf("Bjork-IS" to artistCatalog)))
                            )
                        }
                    }
                }
            }

            When("the audio item updates its track number and disc number") {
                val artistCatalogBeforeUpdate = registry.findFirst(audioItem.artist).get()

                val updatedAudioItem = audioItemEventPublisher.publishUpdateEvent(audioItem) {
                    it.apply {
                        trackNumber = trackNumber!!.plus(1).toShort()
                        discNumber = discNumber!!.plus(1).toShort()
                    }
                }

                then("the album in the artist catalog should be updated") {
                    eventually(1.seconds) {
                        registry.findFirst(audioItem.artist) shouldBePresent { artistCatalog ->
                            artistCatalog.artist shouldBe audioItem.artist
                            artistCatalog should {
                                it.artist shouldBe audioItem.artist
                                it.size shouldBe 1
                                it.findAlbum(audioItem.album) shouldBePresent { extendedAlbum ->
                                    extendedAlbum.audioItems.shouldContainExactly(updatedAudioItem)
                                }
                            }

                            subscriber.receivedEvents shouldBe mutableMapOf(
                                UPDATE to mutableListOf(UPDATE.of(mapOf(audioItem.artist.id() to artistCatalog), mapOf(audioItem.artist.id() to artistCatalogBeforeUpdate))))
                        }
                    }
                }
            }

            When("the audio item is deleted") {
                val artistCatalogBeforeDeletion = registry.findFirst(audioItem.artist).get()

                audioItemEventPublisher.publishDeleteEvent(audioItem)

                then("the artist catalog should not contain the artist and album with the audio item") {
                    eventually(1.seconds) {
                        registry.isEmpty shouldBe true
                        registry.findFirst(audioItem.artist).isEmpty shouldBe true
                        registry.findAlbum(audioItem.album.name, audioItem.artist).isEmpty shouldBe true

                        subscriber.receivedEvents shouldBe mutableMapOf(
                            DELETE to mutableListOf(DELETE.of(mutableMapOf(audioItem.artist.id() to artistCatalogBeforeDeletion)))
                        )
                    }
                }
            }
        }
    }
})
