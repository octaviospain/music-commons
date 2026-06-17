package net.transgressoft.commons.fx.music.audio

import net.transgressoft.commons.music.audio.Album
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.id
import net.transgressoft.commons.music.audio.virtualFiles
import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.lirp.persistence.VolatileRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.arbitrary.next
import org.testfx.api.FxToolkit
import org.testfx.util.WaitForAsyncUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Tests for [FXArtistCatalog] equality, hash code, observable properties, and album grouping,
 * verifying that the build-once construction from a list snapshot produces correct state.
 */
@ExperimentalCoroutinesApi
internal class FXArtistCatalogTest : StringSpec({

    val reactive = reactiveScope()
    val files = virtualFiles()

    val artist = Artist.of("Test Artist")
    val album = Album("Test Album", artist)

    beforeSpec {
        FxToolkit.registerPrimaryStage()
    }

    "FXArtistCatalog returns false for equals when audioItemsByAlbumName differs" {
        val path =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
            }.next()
        val audioItem = FXAudioItemTestBridge.createFxAudioItem(path, files.metadataIO)

        val catalog1 = FXArtistCatalog(artist, listOf(audioItem))
        val catalog2 = FXArtistCatalog(artist, emptyList())

        catalog1 shouldNotBe catalog2
    }

    "FXArtistCatalog returns true for equals when artist and audioItemsByAlbumName match" {
        val path =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
            }.next()
        val audioItem = FXAudioItemTestBridge.createFxAudioItem(path, files.metadataIO)

        val catalog1 = FXArtistCatalog(artist, listOf(audioItem))
        val catalog2 = FXArtistCatalog(artist, listOf(audioItem))

        catalog1 shouldBe catalog2
    }

    "FXArtistCatalog produces different hashCode when audioItemsByAlbumName differs" {
        val path =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
            }.next()
        val audioItem = FXAudioItemTestBridge.createFxAudioItem(path, files.metadataIO)

        val catalog1 = FXArtistCatalog(artist, listOf(audioItem))
        val catalog2 = FXArtistCatalog(artist, emptyList())

        catalog1.hashCode() shouldNotBe catalog2.hashCode()
    }

    "FXArtistCatalog clone produces an equal but distinct instance" {
        val path =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
            }.next()
        val audioItem = FXAudioItemTestBridge.createFxAudioItem(path, files.metadataIO)
        val catalog = FXArtistCatalog(artist, listOf(audioItem))

        val cloned = catalog.clone()

        cloned shouldBe catalog
        (cloned === catalog) shouldBe false
    }

    "FXArtistCatalog stores items with the same ordering and unique id when repository ids differ" {
        val path =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
                trackNumber = 1
                discNumber = 1
            }.next()
        val firstAudioItem = FXAudioItemTestBridge.createFxAudioItem(path, 1, files.metadataIO)
        val secondAudioItem = FXAudioItemTestBridge.createFxAudioItem(path, 2, files.metadataIO)

        val catalog = FXArtistCatalog(artist, listOf(firstAudioItem, secondAudioItem))

        catalog.size shouldBe 2
        catalog.albumAudioItems(firstAudioItem.album.name) shouldBe setOf(firstAudioItem, secondAudioItem)
    }

    "FXAudioLibrary indexes same unique id items under different primary artists" {
        val firstArtist = Artist.of("First Artist")
        val secondArtist = Artist.of("Second Artist")
        val path =
            files.virtualAudioFile {
                this.artist = firstArtist
                this.album = Album("Shared Album", firstArtist)
                trackNumber = 1
                discNumber = 1
            }.next()
        val firstAudioItem = FXAudioItemTestBridge.createFxAudioItem(path, 1, files.metadataIO)
        val secondAudioItem =
            FXAudioItemTestBridge.createFxAudioItem(path, 2, files.metadataIO).apply {
                this.artist = secondArtist
                this.album = Album("Shared Album", secondArtist)
            }
        FXAudioLibrary(VolatileRepository("SameUniqueIdFxAudioLibrary")).use { audioLibrary ->
            audioLibrary.add(firstAudioItem)
            audioLibrary.add(secondAudioItem)
            reactive.advance()

            audioLibrary.getArtistCatalog(firstArtist).isPresent shouldBe true
            audioLibrary.getArtistCatalog(secondArtist).isPresent shouldBe true
            audioLibrary.getArtistCatalog(firstArtist).get().albumAudioItems("Shared Album") shouldBe setOf(firstAudioItem)
            audioLibrary.getArtistCatalog(secondArtist).get().albumAudioItems("Shared Album") shouldBe setOf(secondAudioItem)
        }
    }

    "FXArtistCatalog observable properties are populated at construction with correct values" {
        val paths =
            (1..50).map {
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                }.next()
            }
        val items = paths.map { FXAudioItemTestBridge.createFxAudioItem(it, files.metadataIO) }

        val catalog = FXArtistCatalog(artist, items)

        WaitForAsyncUtils.waitForFxEvents()

        catalog.sizeProperty.get() shouldBe 50
        catalog.albumCountProperty.get() shouldBe 1
    }

    "FXArtistCatalog returns false for equals with different types or null" {
        val catalog = FXArtistCatalog(artist, emptyList())

        (catalog.equals(null)) shouldBe false
        (catalog.equals("not a catalog")) shouldBe false
    }

    "FXArtistCatalog exposes empty, artist and uniqueId properties consistent with state" {
        val catalog = FXArtistCatalog(artist, emptyList())

        catalog.emptyProperty.get() shouldBe true
        catalog.artistProperty.get() shouldBe artist
        catalog.uniqueId shouldBe artist.id()
        catalog.compareTo(FXArtistCatalog(artist, emptyList())) shouldBe 0
    }

    "FXArtistCatalog albumAudioItems returns empty set for unknown album" {
        val catalog = FXArtistCatalog(artist, emptyList())
        catalog.albumAudioItems("Nonexistent") shouldBe emptySet()
    }
})