package net.transgressoft.commons.music.audio

import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.lirp.event.CrudEvent
import net.transgressoft.lirp.event.CrudEvent.Type.UPDATE
import net.transgressoft.lirp.persistence.VolatileRepository
import com.neovisionaries.i18n.CountryCode
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.optional.shouldBeEmpty
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Verifies that the catalog re-key subscription in [AudioLibraryBase] is scoped to
 * artist/album/genres property changes (and bulk mutations), and stays silent for non-key mutations
 * such as a title-only change. This guards against the catalog churn that a blanket mutation
 * bridge would cause.
 *
 * Also covers the album and genre catalog projections exposed via [TestAudioLibrary]:
 * [AudioLibraryBase.getAlbum], [AudioLibraryBase.getGenreIndex],
 * [AudioLibraryBase.containsAudioItemWithGenre], and [AudioLibraryBase.getRandomAudioItemsFromGenre].
 */
@ExperimentalCoroutinesApi
internal class AudioLibraryBaseCatalogSyncTest : StringSpec({

    val reactive = reactiveScope()
    val files = virtualFiles()
    lateinit var repository: VolatileRepository<Int, AudioItem>
    lateinit var audioLibrary: TestAudioLibrary

    beforeEach {
        repository = VolatileRepository("AudioLibraryBaseCatalogSyncTest")
        audioLibrary = TestAudioLibrary(repository, files.metadataIO)
    }

    afterEach {
        audioLibrary.close()
    }

    "AudioLibraryBase re-keys catalog on artist change" {
        val oldArtist = Artist.of("David Bowie")
        val newArtist = Artist.of("Lou Reed")
        val audioItem =
            audioLibrary.createFromFile(
                files.virtualAudioFile {
                    artist = oldArtist
                    album = AlbumDetails("Heroes", oldArtist)
                    title = "Heroes"
                }.next()
            )
        reactive.advance()

        audioItem.artist = newArtist
        audioItem.album = AlbumDetails("Transformer", newArtist)
        reactive.advance()

        audioLibrary.getArtistCatalog(newArtist) shouldBePresent { it.artist.name shouldBe newArtist.name }
        audioLibrary.getArtistCatalog(oldArtist).shouldBeEmpty()
    }

    "AudioLibraryBase emits a catalog re-key on album change" {
        val updateEvents = mutableListOf<CrudEvent<Int, AudioItem>>()
        repository.subscribe(UPDATE) { updateEvents.add(it) }

        val artist = Artist.of("Radiohead")
        val audioItem =
            audioLibrary.createFromFile(
                files.virtualAudioFile {
                    this.artist = artist
                    album = AlbumDetails("Pablo Honey", artist)
                    title = "Creep"
                }.next()
            )
        reactive.advance()
        updateEvents.clear()

        audioItem.album = AlbumDetails("OK Computer", artist)
        reactive.advance()

        updateEvents.isEmpty() shouldBe false
    }

    "AudioLibraryBase does not re-key on title-only change" {
        val updateEvents = mutableListOf<CrudEvent<Int, AudioItem>>()
        repository.subscribe(UPDATE) { updateEvents.add(it) }

        val audioItem =
            audioLibrary.createFromFile(
                files.virtualAudioFile {
                    artist = Artist.of("Aphex Twin")
                    album = AlbumDetails("Selected Ambient Works", Artist.of("Aphex Twin"))
                    title = "Xtal"
                    trackNumber = 1
                }.next()
            )
        reactive.advance()
        updateEvents.clear()

        audioItem.title = "Tha"
        audioItem.bpm = 120f
        audioItem.trackNumber = 5
        reactive.advance()

        updateEvents.isEmpty() shouldBe true
    }

    "AudioLibraryBase re-keys catalog on bulk mutation" {
        val updateEvents = mutableListOf<CrudEvent<Int, AudioItem>>()
        repository.subscribe(UPDATE) { updateEvents.add(it) }

        val audioItem =
            audioLibrary.createFromFile(
                files.virtualAudioFile {
                    artist = Artist.of("Burial")
                    album = AlbumDetails("Untrue", Artist.of("Burial"))
                    title = "Archangel"
                }.next()
            )
        reactive.advance()
        updateEvents.clear()

        val newArtist = Artist.of("Four Tet")
        audioItem.mutate {
            artist = newArtist
            album = AlbumDetails("Rounds", newArtist)
            title = "She Moves She"
        }
        reactive.advance()

        updateEvents.isEmpty() shouldBe false
        audioLibrary.getArtistCatalog(newArtist) shouldBePresent { it.artist.name shouldBe newArtist.name }
    }

    "AudioLibraryBase emits a catalog re-key on genres change" {
        val updateEvents = mutableListOf<CrudEvent<Int, AudioItem>>()
        repository.subscribe(UPDATE) { updateEvents.add(it) }

        val artist = Artist.of("Radiohead", CountryCode.UK)
        val audioItem =
            audioLibrary.createFromFile(
                files.virtualAudioFile {
                    this.artist = artist
                    album = AlbumDetails("Kid A", artist)
                    genres = setOf(Electronic)
                    title = "Everything in Its Right Place"
                    trackNumber = 1
                    discNumber = 1
                }.next()
            )
        reactive.advance()
        updateEvents.clear()

        // Editing genres triggers a re-key because genres is a catalog-relevant property.
        // This test serves as a negative-control: if the genres gate is removed from
        // subscribeCatalogKeyChanges, no UPDATE is emitted and this test fails.
        audioItem.genres = setOf(Alternative)
        reactive.advance()

        updateEvents.isEmpty() shouldBe false
        audioLibrary.getGenreIndex(Alternative) shouldBePresent { it.genre shouldBe Alternative }
        audioLibrary.getGenreIndex(Electronic).shouldBeEmpty()
    }

    "AudioLibraryBase getAlbum and getGenreIndex return correct live values" {
        val artist = Artist.of("Portishead", CountryCode.UK)
        val album = AlbumDetails("Dummy", artist)
        val audioItem =
            audioLibrary.createFromFile(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                    genres = setOf(Electronic)
                    title = "Sour Times"
                    trackNumber = 1
                    discNumber = 1
                }.next()
            )
        reactive.advance()

        eventually(2.seconds) {
            audioLibrary.getAlbum(album) shouldBePresent { catalog ->
                catalog.album.name shouldBe "Dummy"
                catalog.tracks.shouldContainOnly(audioItem)
            }
            audioLibrary.getAlbum("Dummy") shouldBePresent { it.album.name shouldBe "Dummy" }

            audioLibrary.getGenreIndex(Electronic) shouldBePresent { catalog ->
                catalog.genre shouldBe Electronic
                catalog.tracks.shouldContainOnly(audioItem)
            }
            audioLibrary.getGenreIndex("Electronic") shouldBePresent { it.genre shouldBe Electronic }
        }
    }

    "AudioLibraryBase containsAudioItemWithGenre and getRandomAudioItemsFromGenre return correct results" {
        val artist = Artist.of("Burial", CountryCode.UK)
        val album = AlbumDetails("Untrue", artist)
        val audioItem =
            audioLibrary.createFromFile(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                    genres = setOf(Electronic)
                    title = "Archangel"
                    trackNumber = 1
                    discNumber = 1
                }.next()
            )
        reactive.advance()

        eventually(2.seconds) {
            audioLibrary.containsAudioItemWithGenre("Electronic") shouldBe true
            audioLibrary.containsAudioItemWithGenre("Jazz") shouldBe false

            val randomItems = audioLibrary.getRandomAudioItemsFromGenre(Electronic)
            randomItems.shouldContainOnly(audioItem)
        }
    }

    "AudioLibraryBase getGenreIndex and containsAudioItemWithGenre resolve blank name to the no-genre bucket" {
        val artist = Artist.of("Aphex Twin")
        val album = AlbumDetails("Selected Ambient Works", artist)

        // No items yet — blank-name queries return absent/false
        audioLibrary.containsAudioItemWithGenre("") shouldBe false

        // Add an untagged item
        val untaggedItem =
            audioLibrary.createFromFile(
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                    genres = emptySet()
                    title = "Xtal"
                    trackNumber = 1
                    discNumber = 1
                }.next()
            )
        reactive.advance()

        eventually(2.seconds) {
            audioLibrary.getGenreIndex("") shouldBePresent { it.genre shouldBe Genre.None }
            audioLibrary.containsAudioItemWithGenre("") shouldBe true
        }

        // Add a tagged item and verify that only tagged presence changes
        audioLibrary.createFromFile(
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
                genres = setOf(Electronic)
                title = "Tha"
                trackNumber = 2
                discNumber = 1
            }.next()
        )
        reactive.advance()

        eventually(2.seconds) {
            // Untagged item still exists, so blank-name query still resolves
            audioLibrary.containsAudioItemWithGenre("") shouldBe true
        }

        // Remove the untagged item — blank-name query must return false when only tagged items remain
        audioLibrary.remove(untaggedItem)
        reactive.advance()

        eventually(2.seconds) {
            audioLibrary.containsAudioItemWithGenre("") shouldBe false
        }
    }
})