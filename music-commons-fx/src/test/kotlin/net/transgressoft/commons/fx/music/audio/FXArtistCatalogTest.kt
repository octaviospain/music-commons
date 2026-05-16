package net.transgressoft.commons.fx.music.audio

import net.transgressoft.commons.music.audio.ImmutableAlbum
import net.transgressoft.commons.music.audio.ImmutableArtist
import net.transgressoft.commons.music.audio.VirtualFiles.virtualAudioFile
import net.transgressoft.commons.music.testing.reactiveScope
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import org.testfx.api.FxToolkit
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Tests for [FXArtistCatalog] equality and hash code behavior, ensuring
 * `audioItemsByAlbumName` is included in structural comparison.
 */
@ExperimentalCoroutinesApi
internal class FXArtistCatalogTest : StringSpec({

    val reactive = reactiveScope()

    val artist = ImmutableArtist.of("Test Artist")
    val album = ImmutableAlbum("Test Album", artist)

    beforeSpec {
        FxToolkit.registerPrimaryStage()
    }

    "FXArtistCatalog returns false for equals when audioItemsByAlbumName differs" {
        val catalog1 = FXArtistCatalog(artist)
        val catalog2 = FXArtistCatalog(artist)

        val path =
            Arb.virtualAudioFile {
                this.artist = artist
                this.album = album
            }.next()
        val audioItem = FXAudioItem(path)

        catalog1.addAudioItem(audioItem)

        catalog1 shouldNotBe catalog2
    }

    "FXArtistCatalog returns true for equals when artist and audioItemsByAlbumName match" {
        val catalog1 = FXArtistCatalog(artist)
        val catalog2 = FXArtistCatalog(artist)

        val path =
            Arb.virtualAudioFile {
                this.artist = artist
                this.album = album
            }.next()
        val audioItem = FXAudioItem(path)

        catalog1.addAudioItem(audioItem)
        catalog2.addAudioItem(audioItem)

        catalog1 shouldBe catalog2
    }

    "FXArtistCatalog produces different hashCode when audioItemsByAlbumName differs" {
        val catalog1 = FXArtistCatalog(artist)
        val catalog2 = FXArtistCatalog(artist)

        val path =
            Arb.virtualAudioFile {
                this.artist = artist
                this.album = album
            }.next()
        val audioItem = FXAudioItem(path)

        catalog1.addAudioItem(audioItem)

        catalog1.hashCode() shouldNotBe catalog2.hashCode()
    }

    "FXArtistCatalog detects state change after addAudioItem via clone comparison" {
        val catalog = FXArtistCatalog(artist)
        val cloneBefore = catalog.clone()

        val path =
            Arb.virtualAudioFile {
                this.artist = artist
                this.album = album
            }.next()
        val audioItem = FXAudioItem(path)
        catalog.addAudioItem(audioItem)

        cloneBefore shouldNotBe catalog
    }

    "FXArtistCatalog returns false for equals with different types or null" {
        val catalog = FXArtistCatalog(artist)

        (catalog.equals(null)) shouldBe false
        (catalog.equals("not a catalog")) shouldBe false
    }
})