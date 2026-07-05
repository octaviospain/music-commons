package net.transgressoft.commons.fx.music.audio

import net.transgressoft.commons.music.audio.AlbumDetails
import net.transgressoft.commons.music.audio.Artist
import net.transgressoft.commons.music.audio.AudioItemMetadata
import net.transgressoft.commons.music.audio.AudioItemTestAttributes
import net.transgressoft.commons.music.audio.Genre
import net.transgressoft.commons.music.audio.VirtualFiles
import io.kotest.core.spec.style.scopes.StringSpecRootScope
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.arbitrary.next
import java.nio.file.Path
import java.time.LocalDateTime

/*
 * Shared construction and assertion scaffolding for the flat-bucket FX projections
 * (FXAlbum, FXArtistCatalog, FXGenreIndex).
 *
 * The bucket types are internal to the music-commons-fx module, so this support lives in the
 * FX test source set rather than in music-commons-fx-test, which cannot see the internal
 * constructors.
 */

/**
 * Builds an [FXAudioItem] from a virtual audio file, applying [configure] to its tag attributes,
 * mirroring the repeated `virtualFiles.virtualAudioFile { ... }.next()` +
 * [FXAudioItemTestBridge.createFxAudioItem] arrange used across the bucket specs.
 */
fun VirtualFiles.makeItem(
    id: Int? = null,
    configure: AudioItemTestAttributes.() -> Unit = {}
): FXAudioItem {
    val path = virtualAudioFile(attributesAction = configure).next()
    return if (id == null) {
        FXAudioItemTestBridge.createFxAudioItem(path, metadataIO)
    } else {
        FXAudioItemTestBridge.createFxAudioItem(path, id, metadataIO)
    }
}

/**
 * Convenience builder for a bucket item tagged with a single artist, album, and optional genres —
 * the common shape the equality specs need.
 */
fun VirtualFiles.makeItem(
    artist: Artist,
    album: AlbumDetails,
    genres: Set<Genre> = emptySet(),
    id: Int? = null
): FXAudioItem =
    makeItem(id) {
        this.artist = artist
        this.album = album
        if (genres.isNotEmpty()) this.genres = genres
    }

/**
 * Builds an orphan [FXAudioItem] via the deserialization constructor: empty metadata, no
 * `metadataIO` back-ref, and fresh creation timestamps — modelling a freshly-deserialized entity
 * before the library rehydration pass wires it up.
 */
fun orphanFxItem(path: Path, id: Int): FXAudioItem =
    FXAudioItem(path, id, AudioItemMetadata(), LocalDateTime.now(), LocalDateTime.now(), 0)

/**
 * Registers the equals / hashCode / clone contract shared by the flat-bucket projections as four
 * named [StringSpec] tests.
 *
 * The three FX bucket types implement identical value semantics over their track snapshot: two
 * buckets are equal iff their key and track list match, hash codes diverge when the track list
 * diverges, and [clone] yields an equal-but-distinct instance. Buckets are built from the same
 * source item(s) via [buildFromItems] so the equal/hashCode checks compare identical snapshots.
 *
 * @param label the projection type name, used in the generated test names
 * @param buildFromItems builds a bucket wrapping the supplied tracks (empty list for an empty bucket)
 * @param buildItem builds one bucket-eligible track (invoked once; the same reference is reused)
 * @param clone clones a bucket (typically `{ it.clone() }`)
 */
fun <T : Any> StringSpecRootScope.flatBucketEqualitySemantics(
    label: String,
    buildFromItems: (List<ObservableAudioItem>) -> T,
    buildItem: () -> ObservableAudioItem,
    clone: (T) -> T
) {
    "$label returns false for equals when track list differs" {
        buildFromItems(listOf(buildItem())) shouldNotBe buildFromItems(emptyList())
    }

    "$label returns true for equals when key and track list match" {
        val item = buildItem()
        buildFromItems(listOf(item)) shouldBe buildFromItems(listOf(item))
    }

    "$label produces different hashCode when track list differs" {
        buildFromItems(listOf(buildItem())).hashCode() shouldNotBe buildFromItems(emptyList()).hashCode()
    }

    "$label clone produces an equal but distinct instance" {
        val original = buildFromItems(listOf(buildItem()))
        val cloned = clone(original)

        cloned shouldBe original
        (cloned === original) shouldBe false
    }
}