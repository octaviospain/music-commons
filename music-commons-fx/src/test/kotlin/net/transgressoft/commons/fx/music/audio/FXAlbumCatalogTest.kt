package net.transgressoft.commons.fx.music.audio

import net.transgressoft.commons.music.audio.Album
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.AudioItemMetadata
import net.transgressoft.commons.music.audio.UNASSIGNED_ID
import net.transgressoft.commons.music.audio.testCoverBytes
import net.transgressoft.commons.music.audio.virtualFiles
import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.lirp.persistence.VolatileRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.arbitrary.next
import org.testfx.api.FxToolkit
import org.testfx.util.WaitForAsyncUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Tests for [FXAlbumCatalog] equality, hash code, observable properties, and flat-bucket semantics,
 * verifying that the build-once construction from a list snapshot produces correct state.
 */
@ExperimentalCoroutinesApi
internal class FXAlbumCatalogTest : StringSpec({

    val reactive = reactiveScope()
    val files = virtualFiles()

    val artist = Artist.of("Test Artist")
    val album = Album("Test Album", artist)

    beforeSpec {
        FxToolkit.registerPrimaryStage()
    }

    "FXAlbumCatalog returns false for equals when audioItemsSet differs" {
        val path =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
            }.next()
        val audioItem = FXAudioItemTestBridge.createFxAudioItem(path, files.metadataIO)

        val catalog1 = FXAlbumCatalog(album, listOf(audioItem))
        val catalog2 = FXAlbumCatalog(album, emptyList())

        catalog1 shouldNotBe catalog2
    }

    "FXAlbumCatalog returns true for equals when album and audioItemsSet match" {
        val path =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
            }.next()
        val audioItem = FXAudioItemTestBridge.createFxAudioItem(path, files.metadataIO)

        val catalog1 = FXAlbumCatalog(album, listOf(audioItem))
        val catalog2 = FXAlbumCatalog(album, listOf(audioItem))

        catalog1 shouldBe catalog2
    }

    "FXAlbumCatalog produces different hashCode when audioItemsSet differs" {
        val path =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
            }.next()
        val audioItem = FXAudioItemTestBridge.createFxAudioItem(path, files.metadataIO)

        val catalog1 = FXAlbumCatalog(album, listOf(audioItem))
        val catalog2 = FXAlbumCatalog(album, emptyList())

        catalog1.hashCode() shouldNotBe catalog2.hashCode()
    }

    "FXAlbumCatalog clone produces an equal but distinct instance" {
        val path =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
            }.next()
        val audioItem = FXAudioItemTestBridge.createFxAudioItem(path, files.metadataIO)
        val catalog = FXAlbumCatalog(album, listOf(audioItem))

        val cloned = catalog.clone()

        cloned shouldBe catalog
        (cloned === catalog) shouldBe false
    }

    "FXAlbumCatalog stores distinct items with different repository ids in the same album" {
        // Two items from the same file with different ids — FXAudioItem.equals() is metadata-based
        // (path, title, artist, etc.) so they compare as equal by value, but isSameAudioItem
        // considers them distinct because their integer ids differ. The catalog must retain both.
        val path1 =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
                trackNumber = 1
                discNumber = 1
            }.next()
        val path2 =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
                trackNumber = 2
                discNumber = 1
            }.next()
        val firstAudioItem = FXAudioItemTestBridge.createFxAudioItem(path1, 1, files.metadataIO)
        val secondAudioItem = FXAudioItemTestBridge.createFxAudioItem(path2, 2, files.metadataIO)

        val catalog = FXAlbumCatalog(album, listOf(firstAudioItem, secondAudioItem))

        catalog.size shouldBe 2
        catalog.audioItems.map { it.id }.toSet() shouldBe setOf(1, 2)
    }

    "FXAlbumCatalog retains all distinct items sharing the same sort key when item comparator returns 0" {
        // Two items that share all comparable fields (same artist, album, track, disc) but differ by id.
        // Two distinct items sharing a sort key must both be retained in the flat bucket: the TreeSet
        // deduplicates by Comparable.compareTo, but the buildFlatSet loop guards on isSameAudioItem
        // (id-based), so both items must be retained.
        val path =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
                trackNumber = 1
                discNumber = 1
            }.next()
        val itemA = FXAudioItemTestBridge.createFxAudioItem(path, 10, files.metadataIO)
        val itemB = FXAudioItemTestBridge.createFxAudioItem(path, 20, files.metadataIO)

        val catalog = FXAlbumCatalog(album, listOf(itemA, itemB))

        // Both items must be present; the catalog must not silently absorb one of them.
        catalog.size shouldBe 2
    }

    "FXAlbumCatalog retains two UNASSIGNED_ID items with same sort key but different paths" {
        // Regression: the flat-set comparator must fall back to uniqueId (path-derived) when both
        // items have UNASSIGNED_ID, so distinct unsaved items sharing a natural sort key are both kept.
        val path1 =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
                trackNumber = 1
                discNumber = 1
            }.next()
        val path2 =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
                trackNumber = 1
                discNumber = 1
            }.next()
        val itemA = FXAudioItemTestBridge.createFxAudioItem(path1, UNASSIGNED_ID, files.metadataIO)
        val itemB = FXAudioItemTestBridge.createFxAudioItem(path2, UNASSIGNED_ID, files.metadataIO)

        val catalog = FXAlbumCatalog(album, listOf(itemA, itemB))

        catalog.size shouldBe 2
    }

    "FXAudioLibrary indexes items in album catalog bucket after adding them" {
        FXAudioLibrary(VolatileRepository("AlbumCatalogFxAudioLibrary")).use { audioLibrary ->
            val path =
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                }.next()
            val audioItem = FXAudioItemTestBridge.createFxAudioItem(path, files.metadataIO)
            audioLibrary.add(audioItem)
            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            audioLibrary.getAlbumCatalog(album).isPresent shouldBe true
            audioLibrary.getAlbumCatalog(album).get().size shouldBe 1
        }
    }

    "FXAlbumCatalog observable properties are populated at construction with correct values" {
        val paths =
            (1..10).map {
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                }.next()
            }
        val items = paths.map { FXAudioItemTestBridge.createFxAudioItem(it, files.metadataIO) }

        val catalog = FXAlbumCatalog(album, items)

        WaitForAsyncUtils.waitForFxEvents()

        catalog.sizeProperty.get() shouldBe items.size
        catalog.emptyProperty.get() shouldBe false
        catalog.albumProperty.get() shouldBe album
        catalog.audioItemsProperty.size shouldBe items.size
    }

    "FXAlbumCatalog returns false for equals with different types or null" {
        val catalog = FXAlbumCatalog(album, emptyList())

        (catalog.equals(null)) shouldBe false
        (catalog.equals("not a catalog")) shouldBe false
    }

    "FXAlbumCatalog exposes empty and album properties consistent with an empty state" {
        val catalog = FXAlbumCatalog(album, emptyList())

        catalog.emptyProperty.get() shouldBe true
        catalog.albumProperty.get() shouldBe album
        catalog.uniqueId shouldBe album.name
        catalog.compareTo(FXAlbumCatalog(album, emptyList())) shouldBe 0
    }

    "FXAlbumCatalog coverProperty resolves from the first cover-bearing item when earlier items have no cover" {
        // First item has no cover; second item has cover. The catalog must surface the cover from item 2.
        val noCoverPath =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
                trackNumber = 1
                discNumber = 1
            }.next()
        val coverPath =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
                trackNumber = 2
                discNumber = 1
            }.next()

        val noCoverItem = FXAudioItemTestBridge.createFxAudioItemFromMetadata(noCoverPath, 1, AudioItemMetadata())
        val coverItem =
            FXAudioItemTestBridge.createFxAudioItemFromMetadata(coverPath, 2, AudioItemMetadata(coverBytes = testCoverBytes))

        val catalog = FXAlbumCatalog(album, listOf(noCoverItem, coverItem))

        WaitForAsyncUtils.waitForFxEvents()

        // Lazy contract: no cover work is done at construction, so the property is still empty.
        catalog.coverProperty.get().isPresent shouldBe false

        // Accessing coverImageBytes triggers resolution and the deferred FX-thread property publish.
        catalog.coverImageBytes shouldBe testCoverBytes

        WaitForAsyncUtils.waitForFxEvents()

        catalog.coverProperty.get() shouldBePresent { }
    }

    "FXAlbumCatalog coverProperty is empty Optional and coverImageBytes is null when no item has cover" {
        val path =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
            }.next()
        val noCoverItem = FXAudioItemTestBridge.createFxAudioItemFromMetadata(path, 1, AudioItemMetadata())

        val catalog = FXAlbumCatalog(album, listOf(noCoverItem))

        WaitForAsyncUtils.waitForFxEvents()

        catalog.coverImageBytes shouldBe null
        catalog.coverProperty.get().isPresent shouldBe false
    }
})