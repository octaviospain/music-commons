package net.transgressoft.commons.fx.music.audio

import net.transgressoft.commons.music.audio.ImmutableAlbum
import net.transgressoft.commons.music.audio.ImmutableArtist
import net.transgressoft.commons.music.audio.virtualFiles
import net.transgressoft.commons.music.testing.reactiveScope
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