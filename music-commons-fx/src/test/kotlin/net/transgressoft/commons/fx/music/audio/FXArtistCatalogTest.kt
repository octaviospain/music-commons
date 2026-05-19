package net.transgressoft.commons.fx.music.audio

import net.transgressoft.commons.music.audio.ImmutableAlbum
import net.transgressoft.commons.music.audio.ImmutableArtist
import net.transgressoft.commons.music.audio.virtualFiles
import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.lirp.persistence.VolatileRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.arbitrary.next
import org.testfx.api.FxToolkit
import org.testfx.util.WaitForAsyncUtils
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Tests for [FXArtistCatalog] equality and hash code behavior, ensuring
 * `audioItemsByAlbumName` is included in structural comparison.
 */
@ExperimentalCoroutinesApi
internal class FXArtistCatalogTest : StringSpec({

    val reactive = reactiveScope()
    val files = virtualFiles()

    val artist = ImmutableArtist.of("Test Artist")
    val album = ImmutableAlbum("Test Album", artist)

    beforeSpec {
        FxToolkit.registerPrimaryStage()
    }

    "FXArtistCatalog returns false for equals when audioItemsByAlbumName differs" {
        val catalog1 = FXArtistCatalog(artist)
        val catalog2 = FXArtistCatalog(artist)

        val path =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
            }.next()
        val audioItem = FXAudioItemTestBridge.createFxAudioItem(path, files.metadataIO)

        catalog1.addAudioItem(audioItem)

        catalog1 shouldNotBe catalog2
    }

    "FXArtistCatalog returns true for equals when artist and audioItemsByAlbumName match" {
        val catalog1 = FXArtistCatalog(artist)
        val catalog2 = FXArtistCatalog(artist)

        val path =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
            }.next()
        val audioItem = FXAudioItemTestBridge.createFxAudioItem(path, files.metadataIO)

        catalog1.addAudioItem(audioItem)
        catalog2.addAudioItem(audioItem)

        catalog1 shouldBe catalog2
    }

    "FXArtistCatalog produces different hashCode when audioItemsByAlbumName differs" {
        val catalog1 = FXArtistCatalog(artist)
        val catalog2 = FXArtistCatalog(artist)

        val path =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
            }.next()
        val audioItem = FXAudioItemTestBridge.createFxAudioItem(path, files.metadataIO)

        catalog1.addAudioItem(audioItem)

        catalog1.hashCode() shouldNotBe catalog2.hashCode()
    }

    "FXArtistCatalog detects state change after addAudioItem via clone comparison" {
        val catalog = FXArtistCatalog(artist)
        val cloneBefore = catalog.clone()

        val path =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
            }.next()
        val audioItem = FXAudioItemTestBridge.createFxAudioItem(path, files.metadataIO)
        catalog.addAudioItem(audioItem)

        cloneBefore shouldNotBe catalog
    }

    "FXArtistCatalog removes an item from the resolved album bucket when the item's album changed before removal" {
        val renamedAlbum = ImmutableAlbum("Renamed Album", artist)
        val catalog = FXArtistCatalog(artist)
        val path =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
            }.next()
        val audioItem = FXAudioItemTestBridge.createFxAudioItem(path, files.metadataIO)

        catalog.addAudioItem(audioItem)
        audioItem.album = renamedAlbum

        catalog.containsAudioItem(audioItem) shouldBe true
        catalog.removeAudioItem(audioItem) shouldBe true
        catalog.albums shouldBe emptySet()
    }

    "FXArtistCatalog albums uses the current audio item album when a bucket is stale" {
        val renamedAlbum = ImmutableAlbum("Renamed Album", artist)
        val catalog = FXArtistCatalog(artist)
        val path =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
            }.next()
        val audioItem = FXAudioItemTestBridge.createFxAudioItem(path, files.metadataIO)

        catalog.addAudioItem(audioItem)
        audioItem.album = renamedAlbum

        catalog.albums.single().albumName shouldBe renamedAlbum.name
    }

    "FXArtistCatalog stores items with the same ordering and unique id when repository ids differ" {
        val catalog = FXArtistCatalog(artist)
        val path =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
                trackNumber = 1
                discNumber = 1
            }.next()
        val firstAudioItem = FXAudioItemTestBridge.createFxAudioItem(path, 1, files.metadataIO)
        val secondAudioItem = FXAudioItemTestBridge.createFxAudioItem(path, 2, files.metadataIO)

        catalog.addAudioItem(firstAudioItem) shouldBe true
        catalog.addAudioItem(secondAudioItem) shouldBe true

        catalog.size shouldBe 2
        catalog.albumAudioItems(firstAudioItem.album.name) shouldBe setOf(firstAudioItem, secondAudioItem)
    }

    "FXAudioLibrary indexes same unique id items under different primary artists" {
        val firstArtist = ImmutableArtist.of("First Artist")
        val secondArtist = ImmutableArtist.of("Second Artist")
        val path =
            files.virtualAudioFile {
                this.artist = firstArtist
                this.album = ImmutableAlbum("Shared Album", firstArtist)
                trackNumber = 1
                discNumber = 1
            }.next()
        val firstAudioItem = FXAudioItemTestBridge.createFxAudioItem(path, 1, files.metadataIO)
        val secondAudioItem =
            FXAudioItemTestBridge.createFxAudioItem(path, 2, files.metadataIO).apply {
                this.artist = secondArtist
                this.album = ImmutableAlbum("Shared Album", secondArtist)
            }
        val audioLibrary = FXAudioLibrary(VolatileRepository("SameUniqueIdFxAudioLibrary"))

        audioLibrary.add(firstAudioItem)
        audioLibrary.add(secondAudioItem)
        reactive.advance()

        audioLibrary.getArtistCatalog(firstArtist).isPresent shouldBe true
        audioLibrary.getArtistCatalog(secondArtist).isPresent shouldBe true
        audioLibrary.getArtistCatalog(firstArtist).get().albumAudioItems("Shared Album") shouldBe setOf(firstAudioItem)
        audioLibrary.getArtistCatalog(secondArtist).get().albumAudioItems("Shared Album") shouldBe setOf(secondAudioItem)
        audioLibrary.close()
    }

    "FXArtistCatalog coalesces burst mutations into one JavaFX property refresh" {
        val catalog = FXArtistCatalog(artist)
        val sizeChangeCount = AtomicInteger()
        catalog.sizeProperty.addListener { _, _, _ -> sizeChangeCount.incrementAndGet() }

        repeat(50) {
            val path =
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                }.next()
            catalog.addAudioItem(FXAudioItemTestBridge.createFxAudioItem(path, files.metadataIO))
        }

        reactive.advance()
        WaitForAsyncUtils.waitForFxEvents()

        catalog.sizeProperty.get() shouldBe 50
        catalog.albumCountProperty.get() shouldBe 1
        sizeChangeCount.get() shouldBe 1
    }

    "FXArtistCatalog returns false for equals with different types or null" {
        val catalog = FXArtistCatalog(artist)

        (catalog.equals(null)) shouldBe false
        (catalog.equals("not a catalog")) shouldBe false
    }
})