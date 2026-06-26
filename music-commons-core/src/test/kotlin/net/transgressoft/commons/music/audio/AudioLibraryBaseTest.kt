package net.transgressoft.commons.music.audio

import net.transgressoft.commons.music.audio.virtualFiles
import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.lirp.event.CrudEvent.Type.CREATE
import net.transgressoft.lirp.event.CrudEvent.Type.DELETE
import net.transgressoft.lirp.event.CrudEvent.Type.UPDATE
import net.transgressoft.lirp.persistence.VolatileRepository
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.optional.shouldBeEmpty
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
internal class AudioLibraryBaseTest : StringSpec({

    val reactive = reactiveScope()
    val files = virtualFiles()
    lateinit var repository: VolatileRepository<Int, AudioItem>
    lateinit var audioLibrary: TestAudioLibrary

    beforeEach {
        repository = VolatileRepository("AudioLibraryBaseTest")
        audioLibrary = TestAudioLibrary(repository, files.metadataIO)
    }

    afterEach {
        audioLibrary.close()
    }

    "AudioLibraryBase adds audio item and syncs artist catalog" {
        // Use a deterministic artist (no country code) so the catalog key matches the name-derived Artist
        val artist = Artist.of("The Cure")
        val album = AlbumDetails("Disintegration", artist)
        val audioItem =
            audioLibrary.createFromFile(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                    title = "Lovesong"
                }.next()
            )

        reactive.advance()

        // Catalog is keyed on Artist derived from name (country-code-free); query by name
        audioLibrary.getArtistCatalog(Artist.of("The Cure")) shouldBePresent { catalog ->
            catalog.artist.name shouldBe "The Cure"
        }
        audioLibrary.findAlbumAudioItems(Artist.of("The Cure"), album.name).shouldContainOnly(audioItem)
    }

    "AudioLibraryBase removes audio item and cleans up artist catalog" {
        val artist = Artist.of("The Cure")
        val album = AlbumDetails("Disintegration", artist)
        val audioItem =
            audioLibrary.createFromFile(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                    title = "Lovesong"
                }.next()
            )
        reactive.advance()

        audioLibrary.remove(audioItem) shouldBe true
        reactive.advance()

        audioLibrary.getArtistCatalog(Artist.of("The Cure")).shouldBeEmpty()
        audioLibrary.findAlbumAudioItems(Artist.of("The Cure"), album.name).isEmpty() shouldBe true
    }

    "AudioLibraryBase updates artist catalog on item mutation" {
        val audioItem = audioLibrary.createFromFile(files.virtualAudioFile().next())
        reactive.advance()

        audioItem.title = "New Title"
        reactive.advance()

        audioLibrary.contains { it.title == "New Title" } shouldBe true
        audioLibrary.size() shouldBe 1
    }

    "AudioLibraryBase close() stops event delivery" {
        val artist = Artist.of("The Cure")
        val album = AlbumDetails("Disintegration", artist)
        val audioItem =
            audioLibrary.createFromFile(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                    title = "Lovesong"
                }.next()
            )
        reactive.advance()

        audioLibrary.close()

        // Add another item directly to repository after close — catalog should not pick it up.
        // Fixed artist distinct from "The Cure" so the assertions below cannot collide by chance.
        val newArtist = Artist.of("Aphex Twin")
        val virtualPath =
            files.virtualAudioFile {
                this.artist = newArtist
                this.album = AlbumDetails("Selected Ambient Works", newArtist)
            }.next()
        val newItem = MutableAudioItem(virtualPath, Int.MAX_VALUE - 1, files.metadataIO.readMetadata(virtualPath))
        repository.add(newItem)
        reactive.advance()

        // The catalog for the new item's artist should not exist because subscription was cancelled
        audioLibrary.getArtistCatalog(Artist.of(newItem.artist.name)).shouldBeEmpty()
        // The original item's catalog is still present from before close
        audioLibrary.getArtistCatalog(Artist.of("The Cure")) shouldBePresent { it.artist.name shouldBe "The Cure" }
    }

    "AudioLibraryBase findAlbumAudioItems returns items by artist and album" {
        val theBeatles = Artist.of("The Beatles")
        val abbeyRoad = AlbumDetails("Abbey Road", theBeatles)

        val file1 =
            files.virtualAudioFile {
                artist = theBeatles
                album = abbeyRoad
                title = "Come Together"
            }.next()
        val item1 = audioLibrary.createFromFile(file1)

        val file2 =
            files.virtualAudioFile {
                artist = theBeatles
                album = abbeyRoad
                title = "Something"
            }.next()
        val item2 = audioLibrary.createFromFile(file2)

        reactive.advance()

        // Catalog key is Artist("The Beatles", UNDEFINED)
        val result = audioLibrary.findAlbumAudioItems(Artist.of("The Beatles"), abbeyRoad.name)
        result.size shouldBe 2
        result.any { it.id == item1.id } shouldBe true
        result.any { it.id == item2.id } shouldBe true
    }

    "AudioLibraryBase moves item to new artist catalog when artist changes" {
        // Use Artists without country codes so the catalog key (derived from name) matches exactly
        val oldArtist = Artist.of("David Bowie")
        val newArtist = Artist.of("Lou Reed")
        // albumArtist matches track artist; change both together so oldArtist leaves artistsInvolved
        val oldAlbum = AlbumDetails("Heroes", oldArtist)
        val audioItem =
            audioLibrary.createFromFile(
                files.virtualAudioFile {
                    artist = oldArtist
                    album = oldAlbum
                    title = "Heroes"
                }.next()
            )
        reactive.advance()

        audioItem.artist = newArtist
        // also update album so albumArtist no longer references oldArtist
        audioItem.album = AlbumDetails("Transformer", newArtist)

        reactive.advance()

        // Item must appear in new artist's catalog
        audioLibrary.getArtistCatalog(newArtist) shouldBePresent { catalog ->
            catalog.artist.name shouldBe newArtist.name
        }
        // Old artist catalog must be gone because oldArtist is no longer in artistsInvolved
        audioLibrary.getArtistCatalog(oldArtist).shouldBeEmpty()
    }

    "AudioLibraryBase re-sorts item in catalog when track number changes" {
        // Deterministic artist and title prevent random titles from injecting extra artist keys
        val artist = Artist.of("Boards Of Canada")
        val album = AlbumDetails("Music Has The Right To Children", artist)
        val item1 =
            audioLibrary.createFromFile(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                    title = "Roygbiv"
                    trackNumber = 1
                    discNumber = 1
                }.next()
            )
        val item2 =
            audioLibrary.createFromFile(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                    title = "Pete Standing Alone"
                    trackNumber = 2
                    discNumber = 1
                }.next()
            )
        reactive.advance()

        // Move item1 to last position by increasing its track number past item2
        item1.trackNumber = 10

        reactive.advance()

        // Both items still in the same catalog
        audioLibrary.findAlbumAudioItems(Artist.of("Boards Of Canada"), album.name).shouldContainOnly(item1, item2)
    }

    "AudioLibraryBase removes catalog and emits Delete CrudEvent when last item is removed" {
        val artist = Artist.of("Nick Cave")
        val album = AlbumDetails("Murder Ballads", artist)
        val audioItem =
            audioLibrary.createFromFile(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                    title = "Where The Wild Roses Grow"
                }.next()
            )
        reactive.advance()

        val receivedDeleteEvents = mutableListOf<net.transgressoft.lirp.event.CrudEvent<Artist, ArtistCatalog<AudioItem>>>()
        audioLibrary.artistCatalogPublisher.subscribe(DELETE) { receivedDeleteEvents.add(it) }

        audioLibrary.remove(audioItem)

        reactive.advance()

        audioLibrary.getArtistCatalog(artist).shouldBeEmpty()
        eventually(2.seconds) {
            receivedDeleteEvents.isNotEmpty() shouldBe true
        }
    }

    "AudioLibraryBase artistCatalogPublisher emits Create on first item and Update on second" {
        // Deterministic artist and title to prevent random titles from inflating the catalog count
        val artist = Artist.of("Massive Attack")
        val album = AlbumDetails("Mezzanine", artist)

        val createEvents = mutableListOf<net.transgressoft.lirp.event.CrudEvent<Artist, ArtistCatalog<AudioItem>>>()
        val updateEvents = mutableListOf<net.transgressoft.lirp.event.CrudEvent<Artist, ArtistCatalog<AudioItem>>>()
        audioLibrary.artistCatalogPublisher.subscribe(CREATE) { createEvents.add(it) }
        audioLibrary.artistCatalogPublisher.subscribe(UPDATE) { updateEvents.add(it) }

        audioLibrary.createFromFile(
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
                title = "Teardrop"
                trackNumber = 1
            }.next()
        )
        reactive.advance()

        eventually(2.seconds) {
            createEvents.any { event -> event.entities.values.any { it.artist.name == "Massive Attack" } } shouldBe true
        }

        audioLibrary.createFromFile(
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
                title = "Angel"
                trackNumber = 2
            }.next()
        )
        reactive.advance()

        eventually(2.seconds) {
            updateEvents.any { event -> event.entities.values.any { it.artist.name == "Massive Attack" } } shouldBe true
        }
    }

    "getRandomAudioItemsFromArtist returns track where artist is only an involved (featured) artist" {
        // Multi-membership: primary artist A, featured artist B in the title.
        // getRandomAudioItemsFromArtist(B) must find the track even though B is not the primary artist.
        val primaryArtist = Artist.of("Massive Attack")
        val featuredArtist = Artist.of("Elizabeth Fraser")
        val album = AlbumDetails("Mezzanine", primaryArtist)
        val audioItem =
            audioLibrary.createFromFile(
                files.virtualAudioFile {
                    artist = primaryArtist
                    this.album = album
                    // feat without dot matches the getNamesInTitle hasFeat regex pattern
                    title = "Teardrop feat Elizabeth Fraser"
                }.next()
            )
        reactive.advance()

        eventually(2.seconds) {
            val results = audioLibrary.getRandomAudioItemsFromArtist(featuredArtist, 10)
            results.shouldContainOnly(audioItem)
        }
    }
})