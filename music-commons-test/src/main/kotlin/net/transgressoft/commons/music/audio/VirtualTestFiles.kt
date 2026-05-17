/******************************************************************************
 * Copyright (C) 2025  Octavio Calleya Garcia                                 *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 * (at your option) any later version.                                        *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.     *
 ******************************************************************************/

package net.transgressoft.commons.music.audio

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import io.kotest.core.extensions.SpecExtension
import io.kotest.core.spec.Spec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.next
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.images.ArtworkFactory
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Per-spec test fixture that materializes Jimfs-backed virtual audio files with real
 * JAudioTagger tag instances served by a [FakeAudioMetadataIO]. One instance is created
 * per spec by [virtualFiles], and the per-spec Jimfs filesystem is closed by
 * [VirtualFilesExtension] when the spec finishes.
 *
 * Each instance owns its own in-memory [FileSystem], so two specs never collide on Jimfs
 * inodes. No JVM-global mutable state is used — specs execute fully in parallel.
 *
 * Tests inject [metadataUtils] (or [audioMetadataIO]) into [MutableAudioItem] / [FXAudioItem]
 * / library constructors so reads/writes route through the fake instead of the real
 * `DefaultAudioMetadataIO`. The [fileSystem] is passed into serializer constructors when
 * JSON round-trip against Jimfs paths is required.
 */
class VirtualFiles internal constructor() {

    val fileSystem: FileSystem = Jimfs.newFileSystem(Configuration.unix())

    val audioMetadataIO: FakeAudioMetadataIO = FakeAudioMetadataIO()

    val metadataUtils: AudioItemMetadataUtils = AudioItemMetadataUtils(audioMetadataIO)

    fun virtualAlbumAudioFiles(
        artist: Artist? = null,
        album: Album? = null,
        size: IntRange = 20..50,
        fileSystem: FileSystem = this.fileSystem
    ): Arb<List<Path>> =
        arbitrary {
            val arbitraryArtist = artist ?: Arb.artist().bind()
            val arbitraryAlbum = album ?: Arb.album().bind()
            buildList {
                repeat(Arb.int(size).bind()) {
                    add(
                        virtualAudioFile(fileSystem = fileSystem) {
                            this.artist = arbitraryArtist
                            this.album = arbitraryAlbum
                            this.trackNumber = (it.plus(1)).toShort()
                            this.discNumber = 1
                            this.coverImageBytes = testCoverBytes
                        }.bind()
                    )
                }
            }
        }

    fun virtualAudioFile(
        audioFileTagType: AudioFileTagType = Arb.enum<AudioFileTagType>().next(),
        fileSystem: FileSystem = this.fileSystem,
        attributesAction: AudioItemTestAttributes.() -> Unit = {}
    ): Arb<Path> =
        arbitrary {
            val attributes = Arb.audioAttributes().bind()
            attributesAction(attributes)
            create(audioFileTagType, attributes, fileSystem)
        }

    internal fun create(
        tagType: AudioFileTagType,
        attributes: AudioItemTestAttributes,
        fileSystem: FileSystem = this.fileSystem
    ): Path {
        val fileName =
            buildString {
                append(attributes.trackNumber).append(" ")
                append(attributes.title.sanitizePathSegment())
            }
        val filePath =
            buildString {
                append("/").append(attributes.artist.name.sanitizePathSegment()).append("/")
                append(attributes.album.name.sanitizePathSegment()).append("/")
            }
        return createPath(filePath, fileName, tagType.fileType.extension, tagType, attributes, fileSystem)
    }

    /**
     * Materializes a virtual audio file at the exact [targetPath] on [fileSystem],
     * stubbing the [FakeAudioMetadataIO] so that downstream services (e.g.
     * `audioItemFromFile(path)`) see a fully readable audio file with the supplied tag.
     *
     * The codec/container is derived from the file extension on [targetPath]. The
     * supplied [attributes] populate the real tag instance returned by
     * [FakeAudioMetadataIO.readTag] for [targetPath].
     *
     * @param targetPath absolute path on [fileSystem] where the virtual file should appear
     * @param attributes tag values to expose through the stubbed [Tag]
     * @param tagType    tag implementation matching the desired codec
     * @return the same [targetPath], usable as the audio item's path
     */
    fun createAt(
        targetPath: Path,
        attributes: AudioItemTestAttributes,
        tagType: AudioFileTagType
    ): Path = createPathAt(targetPath, tagType, attributes)

    // Strips characters forbidden in Windows path segments (< > : " / \ | ? * and control chars 0-31),
    // trims leading/trailing dots and spaces, and prefixes Windows reserved stems (CON, NUL, COM1..9,
    // LPT1..9) with an underscore. Artist, album, and title attributes are generated by Arb.string(),
    // which produces arbitrary Unicode — passing those raw into Path.of(...) triggers InvalidPathException
    // on Windows even though the virtual filesystem is Jimfs-unix, because the serialized path round-trips
    // through native java.nio.file.Path during JSON deserialization. The fake tag still receives the original
    // (unsanitized) values so round-trip assertions on artist/album/title continue to hold.
    private fun String.sanitizePathSegment(): String {
        // Truncate before sanitizing so each path segment stays well under Windows MAX_PATH (260
        // chars). Arb.string() default range is 0..100 chars; three deep segments + path prefix +
        // extension can otherwise exceed MAX_PATH on Windows. Truncating to ~60 chars per segment
        // leaves headroom for the directory prefix and filename suffix.
        val truncated = if (length > 60) substring(0, 60) else this
        val cleaned =
            truncated
                .replace(Regex("""[<>:"/\\|?*\x00-\x1F]"""), "_")
                .trim('.', ' ')
                .ifEmpty { "_" }
        val stem = cleaned.substringBefore(".")
        return if (stem.uppercase() in WINDOWS_RESERVED_STEMS) "_$cleaned" else cleaned
    }

    // Must match WindowsPathValidator.RESERVED_NAMES exactly, including the superscript variants —
    // Arb.string() generates arbitrary Unicode and can produce "COM¹"/"COM²"/"COM³"/"LPT¹" etc.
    // which the production validator rejects.
    private val WINDOWS_RESERVED_STEMS =
        setOf(
            "CON", "PRN", "AUX", "NUL",
            "COM0", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "COM¹", "COM²", "COM³",
            "LPT0", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9",
            "LPT¹", "LPT²", "LPT³"
        )

    private fun createPath(
        pathToFile: String,
        fileName: String,
        extension: String,
        tagType: AudioFileTagType,
        attributes: AudioItemTestAttributes,
        fileSystem: FileSystem
    ): Path {
        // Build the parent directory under the injected filesystem's native root so the same
        // generator works for Jimfs unix ("/"), osx ("/"), and windows ("C:\") configurations.
        val root = fileSystem.rootDirectories.first()
        val segments = pathToFile.trim('/').split('/').filter { it.isNotEmpty() }
        var dir: Path = root
        for (segment in segments) {
            dir = dir.resolve(segment)
        }
        Files.createDirectories(dir)

        val targetPath = dir.resolve("$fileName.$extension")
        return createPathAt(targetPath, tagType, attributes)
    }

    private fun createPathAt(
        targetPath: Path,
        tagType: AudioFileTagType,
        attributes: AudioItemTestAttributes
    ): Path {
        val parent = targetPath.parent
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent)
        }

        Files.write(targetPath, byteArrayOf(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)

        // Stub the real JAudioTagger tag and header values on the fake for this path.
        audioMetadataIO.stub(targetPath, tagType) {
            populateTag(this, attributes)
            attributes.coverImageBytes?.let { coverBytes -> attachArtwork(this, coverBytes) }
        }
        audioMetadataIO.stubHeader(targetPath, headerInfoFor(targetPath))
        audioMetadataIO.stubCover(targetPath, attributes.coverImageBytes)

        return targetPath
    }
}

// Synthesizes header info per file extension — 320 kbps, 3-minute duration — matching the
// per-extension `encoding` assertions in DefaultAudioLibraryTest's batch test.
private fun headerInfoFor(path: Path): HeaderInfo {
    val ext = path.fileName.toString().substringAfterLast('.', "").lowercase()
    val encoding =
        when (ext) {
            "mp3" -> "MPEG-1 Layer 3"
            "flac" -> "FLAC"
            "wav" -> "WAV"
            "m4a" -> "AAC"
            "ogg" -> "Vorbis"
            else -> ext.uppercase()
        }
    return HeaderInfo(
        encodingType = encoding,
        bitRate = 320,
        trackLengthSeconds = 180L,
        format = encoding
    )
}

// Populates a real JAudioTagger tag with the test attributes. The tag is constructed by
// AudioFileTagType.newActualTag() and answers setField/getFirst/hasField natively — no MockK
// plumbing on the caller side. Unsupported field/format combinations (e.g. WavTag + COUNTRY)
// are swallowed via runCatching so they behave the same way the production `getFieldIfExisting`
// helper handles them.
@Suppress("LongMethod")
private fun populateTag(tag: Tag, attributes: AudioItemTestAttributes) {
    val artist = attributes.artist.name
    val album = attributes.album.name
    val albumArtist = attributes.album.albumArtist.name
    val title = attributes.title
    val genre = Genre.joinGenres(attributes.genres)
    val year = attributes.album.year
    val trackNumber = attributes.trackNumber
    val discNumber = attributes.discNumber
    val bpm = attributes.bpm
    val comment = attributes.comments
    val encoder = attributes.encoder
    val grouping = attributes.album.label.name
    val isCompilation = attributes.album.isCompilation
    val country = attributes.artist.countryCode.name

    setFieldQuietly(tag, FieldKey.TITLE, title)
    setFieldQuietly(tag, FieldKey.ALBUM, album)
    setFieldQuietly(tag, FieldKey.ARTIST, artist)
    setFieldQuietly(tag, FieldKey.ALBUM_ARTIST, albumArtist)
    setFieldQuietly(tag, FieldKey.GENRE, genre)
    year?.let { setFieldQuietly(tag, FieldKey.YEAR, it.toString()) }
    setFieldQuietly(tag, FieldKey.GROUPING, grouping)
    setFieldQuietly(tag, FieldKey.IS_COMPILATION, isCompilation.toString())
    setFieldQuietly(tag, FieldKey.COUNTRY, country)

    comment?.let { setFieldQuietly(tag, FieldKey.COMMENT, it) }
    trackNumber?.let { setFieldQuietly(tag, FieldKey.TRACK, it.toString()) }
    discNumber?.let { setFieldQuietly(tag, FieldKey.DISC_NO, it.toString()) }
    bpm?.let { setFieldQuietly(tag, FieldKey.BPM, it.toString()) }
    encoder?.let { setFieldQuietly(tag, FieldKey.ENCODER, it) }
}

// Some tag/format combinations (notably WavTag + COUNTRY) throw UnsupportedOperationException
// or KeyNotFoundException when setField is called for an unsupported field. The production
// readers route these through runCatching; mirror that here so populateTag is robust across
// every AudioFileTagType.
private fun setFieldQuietly(tag: Tag, key: FieldKey, value: String) {
    runCatching { tag.setField(key, value) }
}

// Attaches cover artwork to the real JAudioTagger tag so the production AudioItemMetadataUtils
// can extract it via parseCoverBytes. Routes through ArtworkFactory.getNew() to avoid the
// temp-file write that AudioItemMetadataUtils.createArtwork does for production write paths.
private fun attachArtwork(tag: Tag, coverBytes: ByteArray) {
    runCatching {
        val artwork = ArtworkFactory.getNew()
        artwork.binaryData = coverBytes
        artwork.mimeType = "image/jpeg"
        artwork.description = ""
        artwork.pictureType = 3 // PictureTypes.DEFAULT_ID (Cover Front)
        tag.addField(artwork)
    }
}

/**
 * Kotest [SpecExtension] that closes the per-spec Jimfs filesystem after the spec finishes.
 * Specs run fully in parallel: the fixture owns no JVM-global mutable state.
 */
class VirtualFilesExtension : SpecExtension {

    val files: VirtualFiles = VirtualFiles()

    override suspend fun intercept(spec: Spec, execute: suspend (Spec) -> Unit) {
        try {
            execute(spec)
        } finally {
            // Jimfs FileSystem is Closeable; without this each spec leaks one in-memory
            // filesystem (inode/page caches included) for the lifetime of the JVM.
            files.fileSystem.close()
        }
    }
}

/**
 * Convenience factory: constructs a [VirtualFilesExtension], registers it on the spec, and
 * returns the per-spec [VirtualFiles] instance.
 *
 *     class FooTest : StringSpec({
 *         val files = virtualFiles()
 *         "test" {
 *             val path = files.virtualAudioFile().next()
 *             val item = MutableAudioItem(path, 1, files.metadataUtils)
 *         }
 *     })
 */
fun Spec.virtualFiles(): VirtualFiles {
    val ext = VirtualFilesExtension()
    extension(ext)
    return ext.files
}