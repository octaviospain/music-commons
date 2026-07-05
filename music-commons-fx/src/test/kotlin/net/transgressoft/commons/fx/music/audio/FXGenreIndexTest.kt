package net.transgressoft.commons.fx.music.audio

import net.transgressoft.commons.music.audio.AlbumDetails
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.Blues
import net.transgressoft.commons.music.audio.Genre
import net.transgressoft.commons.music.audio.Jazz
import net.transgressoft.commons.music.audio.Rock
import net.transgressoft.commons.music.audio.virtualFiles
import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.lirp.persistence.VolatileRepository
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.optional.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import org.testfx.api.FxToolkit
import org.testfx.util.WaitForAsyncUtils
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Tests for [FXGenreIndex] equality, hash code, observable properties, and flat-bucket semantics,
 * verifying that the build-once construction from a list snapshot produces correct state for a
 * multi-key genre projection.
 */
@ExperimentalCoroutinesApi
internal class FXGenreIndexTest : StringSpec({

    val reactive = reactiveScope()
    val files = virtualFiles()

    val artist = Artist.of("Genre Test Artist")
    val album = AlbumDetails("Genre Test Album", artist)
    val genre: Genre = Rock

    beforeSpec {
        FxToolkit.registerPrimaryStage()
    }

    flatBucketEqualitySemantics(
        label = "FXGenreIndex",
        buildFromItems = { FXGenreIndex(genre, it) },
        buildItem = {
            files.makeItem(artist, album, genres = setOf(genre))
        },
        clone = { it.clone() }
    )

    "FXGenreIndex preserves all items from the input list verbatim" {
        val firstAudioItem =
            files.makeItem(id = 1) {
                this.artist = artist
                this.album = album
                this.genres = setOf(genre)
                trackNumber = 1
                discNumber = 1
            }
        val secondAudioItem =
            files.makeItem(id = 2) {
                this.artist = artist
                this.album = album
                this.genres = setOf(genre)
                trackNumber = 2
                discNumber = 1
            }

        val index = FXGenreIndex(genre, listOf(firstAudioItem, secondAudioItem))

        index.size shouldBe 2
        index.tracks.map { it.id }.toSet() shouldBe setOf(1, 2)
    }

    "FXGenreIndex multi-key: item with two genres appears in both genre buckets" {
        val audioItem =
            files.makeItem {
                this.artist = artist
                this.album = album
                title = "Crossover Track"
                this.genres = setOf(Rock, Jazz)
            }

        val rockIndex = FXGenreIndex(Rock, listOf(audioItem))
        val jazzIndex = FXGenreIndex(Jazz, listOf(audioItem))

        rockIndex.size shouldBe 1
        jazzIndex.size shouldBe 1
        rockIndex.tracks shouldBe listOf(audioItem)
        jazzIndex.tracks shouldBe listOf(audioItem)
    }

    "FXAudioLibrary indexes items in genre index buckets after adding them" {
        FXAudioLibrary(VolatileRepository("GenreIndexFxAudioLibrary")).use { audioLibrary ->
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

            audioLibrary.getGenreIndex(Rock).isPresent shouldBe true
            audioLibrary.getGenreIndex(Rock).get().size shouldBe 1
        }
    }

    "FXGenreIndex observable properties are populated at construction with correct values" {
        val items = (1..10).map { files.makeItem(artist, album, genres = setOf(genre)) }

        val index = FXGenreIndex(genre, items)

        WaitForAsyncUtils.waitForFxEvents()

        assertSoftly {
            index.sizeProperty.get() shouldBe items.size
            index.emptyProperty.get() shouldBe false
            index.genreProperty.get() shouldBe genre
            index.tracksProperty shouldHaveSize items.size
        }
    }

    "FXGenreIndex returns false for equals with different types or null" {
        val index = FXGenreIndex(genre, emptyList())

        assertSoftly {
            (index.equals(null)) shouldBe false
            (index.equals("not an index")) shouldBe false
        }
    }

    "FXGenreIndex exposes empty and genre properties consistent with an empty state" {
        val index = FXGenreIndex(genre, emptyList())

        assertSoftly {
            index.emptyProperty.get() shouldBe true
            index.genreProperty.get() shouldBe genre
            index.uniqueId shouldBe genre.name
            index.compareTo(FXGenreIndex(genre, emptyList())) shouldBe 0
        }
    }

    "FXAudioLibrary places untagged item in Genre.None bucket" {
        FXAudioLibrary(VolatileRepository("GenreNoneFxAudioLibrary")).use { audioLibrary ->
            val path =
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                    title = "Untagged Track"
                    this.genres = emptySet()
                }.next()
            val audioItem = FXAudioItemTestBridge.createFxAudioItem(path, files.metadataIO)
            audioLibrary.add(audioItem)
            reactive.advance()

            eventually(2.seconds) {
                WaitForAsyncUtils.waitForFxEvents()
                audioLibrary.getGenreIndex(Genre.None).isPresent shouldBe true
                audioLibrary.getGenreIndex(Genre.None).get().size shouldBe 1
            }
        }
    }

    "FXAudioLibrary moves item out of and back into Genre.None bucket as genres change" {
        FXAudioLibrary(VolatileRepository("GenreNoneTransitionFxAudioLibrary")).use { audioLibrary ->
            val path =
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                    title = "Transition Track"
                    this.genres = emptySet()
                }.next()
            val audioItem = FXAudioItemTestBridge.createFxAudioItem(path, files.metadataIO)
            audioLibrary.add(audioItem)
            reactive.advance()

            eventually(2.seconds) {
                WaitForAsyncUtils.waitForFxEvents()
                audioLibrary.getGenreIndex(Genre.None).isPresent shouldBe true
            }

            // Item gains a genre — leaves the no-genre bucket and enters the real genre bucket
            audioItem.genres = setOf(Rock)
            reactive.advance()

            eventually(2.seconds) {
                WaitForAsyncUtils.waitForFxEvents()
                audioLibrary.getGenreIndex(Genre.None).shouldBeEmpty()
                audioLibrary.getGenreIndex(Rock).isPresent shouldBe true
            }

            // Item loses all genres — returns to the no-genre bucket
            audioItem.genres = emptySet()
            reactive.advance()

            eventually(2.seconds) {
                WaitForAsyncUtils.waitForFxEvents()
                audioLibrary.getGenreIndex(Genre.None).isPresent shouldBe true
                audioLibrary.getGenreIndex(Rock).shouldBeEmpty()
            }
        }
    }

    "FXAudioLibrary genreIndexesProperty iterates genre indexes in natural order with Genre.None first" {
        FXAudioLibrary(VolatileRepository("GenreOrderFxAudioLibrary")).use { audioLibrary ->
            // Add items tagged with Blues and Rock, plus one untagged (goes to Genre.None)
            val bluesPath =
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                    this.genres = setOf(Blues)
                }.next()
            val rockPath =
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                    this.genres = setOf(Rock)
                }.next()
            val untaggedPath =
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                    this.genres = emptySet()
                }.next()

            audioLibrary.add(FXAudioItemTestBridge.createFxAudioItem(bluesPath, files.metadataIO))
            audioLibrary.add(FXAudioItemTestBridge.createFxAudioItem(rockPath, files.metadataIO))
            audioLibrary.add(FXAudioItemTestBridge.createFxAudioItem(untaggedPath, files.metadataIO))

            reactive.advance()

            eventually(2.seconds) {
                WaitForAsyncUtils.waitForFxEvents()
                audioLibrary.genreIndexesProperty.size shouldBe 3
                // Genre.None (name = "") sorts first, then Blues ("Blues"), then Rock ("Rock")
                audioLibrary.genreIndexesProperty.map { gi: ObservableGenreIndex -> gi.genre.name } shouldBe listOf("", "Blues", "Rock")
                // Index-addressable: proves the property is a List, not a Set
                audioLibrary.genreIndexesProperty[0].genre shouldBe Genre.None
            }
        }
    }

    "FXAudioLibrary Genre.None bucket disappears when last untagged item is removed" {
        FXAudioLibrary(VolatileRepository("GenreNoneRemovalFxAudioLibrary")).use { audioLibrary ->
            val path =
                files.virtualAudioFile {
                    this.artist = artist
                    this.album = album
                    title = "Removable Untagged Track"
                    this.genres = emptySet()
                }.next()
            val audioItem = FXAudioItemTestBridge.createFxAudioItem(path, files.metadataIO)
            audioLibrary.add(audioItem)
            reactive.advance()

            eventually(2.seconds) {
                WaitForAsyncUtils.waitForFxEvents()
                audioLibrary.getGenreIndex(Genre.None).isPresent shouldBe true
            }

            audioLibrary.remove(audioItem)
            reactive.advance()

            eventually(2.seconds) {
                WaitForAsyncUtils.waitForFxEvents()
                audioLibrary.getGenreIndex(Genre.None).shouldBeEmpty()
            }
        }
    }
})