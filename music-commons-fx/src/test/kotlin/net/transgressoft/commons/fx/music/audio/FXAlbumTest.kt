package net.transgressoft.commons.fx.music.audio

import net.transgressoft.commons.music.audio.AlbumDetails
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.AudioItemMetadata
import net.transgressoft.commons.music.audio.canonicalKey
import net.transgressoft.commons.music.audio.id
import net.transgressoft.commons.music.audio.testCoverBytes
import net.transgressoft.commons.music.audio.virtualFiles
import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.lirp.event.CrudEvent
import net.transgressoft.lirp.event.CrudEvent.Type.DELETE
import net.transgressoft.lirp.event.CrudEvent.Type.UPDATE
import net.transgressoft.lirp.event.StandardCrudEvent
import net.transgressoft.lirp.persistence.VolatileRepository
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.arbitrary.next
import org.testfx.api.FxToolkit
import org.testfx.util.WaitForAsyncUtils
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Tests for [FXAlbum] equality, hash code, observable properties, and flat-bucket semantics,
 * verifying that the build-once construction from a list snapshot produces correct state.
 */
@ExperimentalCoroutinesApi
internal class FXAlbumTest : StringSpec({

    val reactive = reactiveScope()
    val files = virtualFiles()

    val artist = Artist.of("Test Artist")
    val album = AlbumDetails("Test Album", artist)

    beforeSpec {
        FxToolkit.registerPrimaryStage()
    }

    afterSpec {
        FxToolkit.cleanupStages()
    }

    "FXAlbum returns false for equals when track list differs" {
        val path =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
            }.next()
        val audioItem = FXAudioItemTestBridge.createFxAudioItem(path, files.metadataIO)

        val fxAlbum1 = FXAlbum(album, listOf(audioItem))
        val fxAlbum2 = FXAlbum(album, emptyList())

        fxAlbum1 shouldNotBe fxAlbum2
    }

    "FXAlbum returns true for equals when album and track list match" {
        val path =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
            }.next()
        val audioItem = FXAudioItemTestBridge.createFxAudioItem(path, files.metadataIO)

        val fxAlbum1 = FXAlbum(album, listOf(audioItem))
        val fxAlbum2 = FXAlbum(album, listOf(audioItem))

        fxAlbum1 shouldBe fxAlbum2
    }

    "FXAlbum produces different hashCode when track list differs" {
        val path =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
            }.next()
        val audioItem = FXAudioItemTestBridge.createFxAudioItem(path, files.metadataIO)

        val fxAlbum1 = FXAlbum(album, listOf(audioItem))
        val fxAlbum2 = FXAlbum(album, emptyList())

        fxAlbum1.hashCode() shouldNotBe fxAlbum2.hashCode()
    }

    "FXAlbum clone produces an equal but distinct instance" {
        val path =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
            }.next()
        val audioItem = FXAudioItemTestBridge.createFxAudioItem(path, files.metadataIO)
        val fxAlbum = FXAlbum(album, listOf(audioItem))

        val cloned = fxAlbum.clone()

        cloned shouldBe fxAlbum
        (cloned === fxAlbum) shouldBe false
    }

    "FXAlbum preserves all items from the input list verbatim" {
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

        val fxAlbum = FXAlbum(album, listOf(firstAudioItem, secondAudioItem))

        fxAlbum.size shouldBe 2
        fxAlbum.tracks.map { it.id }.toSet() shouldBe setOf(1, 2)
    }

    "FXAlbum preserves construction order of tracks" {
        // lirp delivers an ordered list; FXAlbum stores it verbatim without reordering
        val paths =
            (1..4).map { trackNum ->
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                    trackNumber = trackNum.toShort()
                    discNumber = 1
                }.next()
            }
        val items = paths.mapIndexed { idx, path -> FXAudioItemTestBridge.createFxAudioItem(path, idx + 1, files.metadataIO) }

        val fxAlbum = FXAlbum(album, items)

        fxAlbum.tracks shouldBe items
    }

    "FXAudioLibrary indexes items in album bucket after adding them" {
        FXAudioLibrary(VolatileRepository("AlbumFxAudioLibrary")).use { audioLibrary ->
            val path =
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                }.next()
            val audioItem = FXAudioItemTestBridge.createFxAudioItem(path, files.metadataIO)
            audioLibrary.add(audioItem)
            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            audioLibrary.getAlbum(album).isPresent shouldBe true
            audioLibrary.getAlbum(album).get().size shouldBe 1
        }
    }

    "FXAlbum observable properties are populated at construction with correct values" {
        val paths =
            (1..10).map {
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                }.next()
            }
        val items = paths.map { FXAudioItemTestBridge.createFxAudioItem(it, files.metadataIO) }

        val fxAlbum = FXAlbum(album, items)

        WaitForAsyncUtils.waitForFxEvents()

        fxAlbum.sizeProperty.get() shouldBe items.size
        fxAlbum.emptyProperty.get() shouldBe false
        fxAlbum.albumProperty.get() shouldBe album
        fxAlbum.tracksProperty shouldHaveSize items.size
    }

    "FXAlbum returns false for equals with different types or null" {
        val fxAlbum = FXAlbum(album, emptyList())

        (fxAlbum.equals(null)) shouldBe false
        (fxAlbum.equals("not an album")) shouldBe false
    }

    "FXAlbum exposes empty and album properties consistent with an empty state" {
        val fxAlbum = FXAlbum(album, emptyList())

        fxAlbum.emptyProperty.get() shouldBe true
        fxAlbum.albumProperty.get() shouldBe album
        fxAlbum.uniqueId shouldBe album.canonicalKey().id()
        fxAlbum.compareTo(FXAlbum(album, emptyList())) shouldBe 0
    }

    "FXAlbum coverProperty resolves from the first cover-bearing item when earlier items have no cover" {
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

        val fxAlbum = FXAlbum(album, listOf(noCoverItem, coverItem))

        WaitForAsyncUtils.waitForFxEvents()

        // Lazy contract: no cover work is done at construction, so the property is still empty.
        fxAlbum.coverProperty.get().isPresent shouldBe false

        // Accessing coverImageBytes triggers resolution and the deferred FX-thread property publish.
        fxAlbum.coverImageBytes shouldBe testCoverBytes

        WaitForAsyncUtils.waitForFxEvents()

        fxAlbum.coverProperty.get() shouldBePresent { }
    }

    "FXAlbum coverProperty is empty Optional and coverImageBytes is null when no item has cover" {
        val path =
            files.virtualAudioFile {
                this.artist = artist
                this.album = album
            }.next()
        val noCoverItem = FXAudioItemTestBridge.createFxAudioItemFromMetadata(path, 1, AudioItemMetadata())

        val fxAlbum = FXAlbum(album, listOf(noCoverItem))

        WaitForAsyncUtils.waitForFxEvents()

        fxAlbum.coverImageBytes shouldBe null
        fxAlbum.coverProperty.get().isPresent shouldBe false
    }

    "FXAudioLibrary merges compilation tracks with varying albumArtist into single album bucket" {
        val compilationAlbumName = "Cherry Moon 9"
        FXAudioLibrary(VolatileRepository("MergeCompilationFxTest")).use { audioLibrary ->
            val blankArtistAlbum = AlbumDetails(compilationAlbumName, Artist.UNKNOWN, isCompilation = true)
            val variousArtistAlbum = AlbumDetails(compilationAlbumName, Artist.of("Various Artists"), isCompilation = false)

            (1..11).forEach { trackNum ->
                val path =
                    files.virtualAudioFile {
                        this.album = blankArtistAlbum
                        trackNumber = trackNum.toShort()
                        discNumber = 1
                    }.next()
                audioLibrary.add(FXAudioItemTestBridge.createFxAudioItem(path, files.metadataIO))
            }

            val variousPath =
                files.virtualAudioFile {
                    this.album = variousArtistAlbum
                    trackNumber = 12
                    discNumber = 1
                }.next()
            audioLibrary.add(FXAudioItemTestBridge.createFxAudioItem(variousPath, files.metadataIO))

            reactive.advance()

            eventually(2.seconds) {
                WaitForAsyncUtils.waitForFxEvents()
                audioLibrary.albumsProperty.size shouldBe 1
            }
        }
    }

    "FXAudioLibrary emits UPDATE event for album when track year changes" {
        val yearAlbum = AlbumDetails("Hardcore Devil", Artist.of("Test Artist"), year = 2011)
        val repository = VolatileRepository<Int, ObservableAudioItem>("YearUpdateFxTest")
        FXAudioLibrary(repository).use { audioLibrary ->
            val path =
                files.virtualAudioFile {
                    this.album = yearAlbum
                    trackNumber = 1
                    discNumber = 1
                }.next()
            val audioItem = FXAudioItemTestBridge.createFxAudioItem(path, files.metadataIO)
            audioLibrary.add(audioItem)
            reactive.advance()

            eventually(2.seconds) {
                WaitForAsyncUtils.waitForFxEvents()
                audioLibrary.getAlbum(yearAlbum).isPresent shouldBe true
            }

            val updateEvents = mutableListOf<CrudEvent<AlbumDetails, ObservableAlbum>>()
            val deleteEvents = mutableListOf<CrudEvent<AlbumDetails, ObservableAlbum>>()
            audioLibrary.albumPublisher.subscribe(UPDATE) { updateEvents.add(it) }
            audioLibrary.albumPublisher.subscribe(DELETE) { deleteEvents.add(it) }

            // Emit a corrected clone so the projection detects a reference change and re-runs the
            // value-transform. The canonical key (name + artist, year zeroed) is unchanged, so the
            // bucket survives and emits Update, not Delete+Create; an in-place mutation on the same
            // reference would be skipped by the projection's position-and-reference no-op guard.
            val correctedItem = audioItem.clone().also { it.album = yearAlbum.copy(year = null) }
            repository.emitAsync(StandardCrudEvent.Update(correctedItem, audioItem))

            eventually(2.seconds) {
                reactive.advance()
                WaitForAsyncUtils.waitForFxEvents()
                updateEvents.size shouldBe 1
                deleteEvents.size shouldBe 0
                audioLibrary.getAlbum(yearAlbum).get().album.year shouldBe null
            }
        }
    }
})