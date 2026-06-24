package net.transgressoft.commons.fx.music.audio

import net.transgressoft.commons.music.audio.Album
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.Genre
import net.transgressoft.commons.music.audio.Jazz
import net.transgressoft.commons.music.audio.Rock
import net.transgressoft.commons.music.audio.UNASSIGNED_ID
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
 * Tests for [FXGenreCatalog] equality, hash code, observable properties, and flat-bucket semantics,
 * verifying that the build-once construction from a list snapshot produces correct state for a
 * multi-key genre projection.
 */
@ExperimentalCoroutinesApi
internal class FXGenreCatalogTest : StringSpec({

    val reactive = reactiveScope()
    val files = virtualFiles()

    val artist = Artist.of("Genre Test Artist")
    val album = Album("Genre Test Album", artist)
    val genre: Genre = Rock

    beforeSpec {
        FxToolkit.registerPrimaryStage()
    }

    "FXGenreCatalog returns false for equals when audioItemsSet differs" {
        val path =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
                this.genres = setOf(genre)
            }.next()
        val audioItem = FXAudioItemTestBridge.createFxAudioItem(path, files.metadataIO)

        val catalog1 = FXGenreCatalog(genre, listOf(audioItem))
        val catalog2 = FXGenreCatalog(genre, emptyList())

        catalog1 shouldNotBe catalog2
    }

    "FXGenreCatalog returns true for equals when genre and audioItemsSet match" {
        val path =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
                this.genres = setOf(genre)
            }.next()
        val audioItem = FXAudioItemTestBridge.createFxAudioItem(path, files.metadataIO)

        val catalog1 = FXGenreCatalog(genre, listOf(audioItem))
        val catalog2 = FXGenreCatalog(genre, listOf(audioItem))

        catalog1 shouldBe catalog2
    }

    "FXGenreCatalog produces different hashCode when audioItemsSet differs" {
        val path =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
                this.genres = setOf(genre)
            }.next()
        val audioItem = FXAudioItemTestBridge.createFxAudioItem(path, files.metadataIO)

        val catalog1 = FXGenreCatalog(genre, listOf(audioItem))
        val catalog2 = FXGenreCatalog(genre, emptyList())

        catalog1.hashCode() shouldNotBe catalog2.hashCode()
    }

    "FXGenreCatalog clone produces an equal but distinct instance" {
        val path =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
                this.genres = setOf(genre)
            }.next()
        val audioItem = FXAudioItemTestBridge.createFxAudioItem(path, files.metadataIO)
        val catalog = FXGenreCatalog(genre, listOf(audioItem))

        val cloned = catalog.clone()

        cloned shouldBe catalog
        (cloned === catalog) shouldBe false
    }

    "FXGenreCatalog stores distinct items with different repository ids in the same genre bucket" {
        // Two items with different paths and track numbers so they have distinct metadata-based equality.
        // Both items are placed in the genre bucket and the catalog must retain both.
        val path1 =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
                this.genres = setOf(genre)
                trackNumber = 1
                discNumber = 1
            }.next()
        val path2 =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
                this.genres = setOf(genre)
                trackNumber = 2
                discNumber = 1
            }.next()
        val firstAudioItem = FXAudioItemTestBridge.createFxAudioItem(path1, 1, files.metadataIO)
        val secondAudioItem = FXAudioItemTestBridge.createFxAudioItem(path2, 2, files.metadataIO)

        val catalog = FXGenreCatalog(genre, listOf(firstAudioItem, secondAudioItem))

        catalog.size shouldBe 2
        catalog.audioItems.map { it.id }.toSet() shouldBe setOf(1, 2)
    }

    "FXGenreCatalog retains all distinct items sharing the same sort key when item comparator returns 0" {
        // Two items that share all comparable fields (same artist, album, track, disc) but differ by id.
        // Two distinct items sharing a sort key must both be retained in the flat bucket: the TreeSet
        // deduplicates by Comparable.compareTo, but the buildFlatSet loop guards on isSameAudioItem
        // (id-based), so both items must be retained.
        val path =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
                this.genres = setOf(genre)
                trackNumber = 1
                discNumber = 1
            }.next()
        val itemA = FXAudioItemTestBridge.createFxAudioItem(path, 10, files.metadataIO)
        val itemB = FXAudioItemTestBridge.createFxAudioItem(path, 20, files.metadataIO)

        val catalog = FXGenreCatalog(genre, listOf(itemA, itemB))

        // Both items must be present; the catalog must not silently absorb one of them.
        catalog.size shouldBe 2
    }

    "FXGenreCatalog retains two UNASSIGNED_ID items with same sort key but different paths" {
        // Regression: the flat-set comparator must fall back to uniqueId (path-derived) when both
        // items have UNASSIGNED_ID, so distinct unsaved items sharing a natural sort key are both kept.
        val path1 =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
                this.genres = setOf(genre)
                trackNumber = 1
                discNumber = 1
            }.next()
        val path2 =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
                this.genres = setOf(genre)
                trackNumber = 1
                discNumber = 1
            }.next()
        val itemA = FXAudioItemTestBridge.createFxAudioItem(path1, UNASSIGNED_ID, files.metadataIO)
        val itemB = FXAudioItemTestBridge.createFxAudioItem(path2, UNASSIGNED_ID, files.metadataIO)

        val catalog = FXGenreCatalog(genre, listOf(itemA, itemB))

        catalog.size shouldBe 2
    }

    "FXGenreCatalog multi-key: item with two genres appears in both genre buckets" {
        val path =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
                // Clean titles with no feat/vs/& to avoid spurious artist-name injection
                title = "Crossover Track"
                this.genres = setOf(Rock, Jazz)
            }.next()
        val audioItem = FXAudioItemTestBridge.createFxAudioItem(path, files.metadataIO)

        val rockCatalog = FXGenreCatalog(Rock, listOf(audioItem))
        val jazzCatalog = FXGenreCatalog(Jazz, listOf(audioItem))

        rockCatalog.size shouldBe 1
        jazzCatalog.size shouldBe 1
        rockCatalog.audioItems shouldBe setOf(audioItem)
        jazzCatalog.audioItems shouldBe setOf(audioItem)
    }

    "FXAudioLibrary indexes items in genre catalog buckets after adding them" {
        FXAudioLibrary(VolatileRepository("GenreCatalogFxAudioLibrary")).use { audioLibrary ->
            val path =
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                    this.genres = setOf(Rock)
                }.next()
            val audioItem = FXAudioItemTestBridge.createFxAudioItem(path, files.metadataIO)
            audioLibrary.add(audioItem)
            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            audioLibrary.getGenreCatalog(Rock).isPresent shouldBe true
            audioLibrary.getGenreCatalog(Rock).get().size shouldBe 1
        }
    }

    "FXGenreCatalog observable properties are populated at construction with correct values" {
        val paths =
            (1..10).map {
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                    this.genres = setOf(genre)
                }.next()
            }
        val items = paths.map { FXAudioItemTestBridge.createFxAudioItem(it, files.metadataIO) }

        val catalog = FXGenreCatalog(genre, items)

        WaitForAsyncUtils.waitForFxEvents()

        catalog.sizeProperty.get() shouldBe items.size
        catalog.emptyProperty.get() shouldBe false
        catalog.genreProperty.get() shouldBe genre
        catalog.audioItemsProperty.size shouldBe items.size
    }

    "FXGenreCatalog returns false for equals with different types or null" {
        val catalog = FXGenreCatalog(genre, emptyList())

        (catalog.equals(null)) shouldBe false
        (catalog.equals("not a catalog")) shouldBe false
    }

    "FXGenreCatalog exposes empty and genre properties consistent with an empty state" {
        val catalog = FXGenreCatalog(genre, emptyList())

        catalog.emptyProperty.get() shouldBe true
        catalog.genreProperty.get() shouldBe genre
        catalog.uniqueId shouldBe genre.name
        catalog.compareTo(FXGenreCatalog(genre, emptyList())) shouldBe 0
    }
})