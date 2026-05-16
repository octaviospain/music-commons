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

import net.transgressoft.commons.music.common.toJsonUri
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import io.kotest.core.extensions.SpecExtension
import io.kotest.core.spec.Spec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.next
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.images.Artwork
import java.io.File
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import kotlin.io.path.absolutePathString
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Process-wide mutex shared by every [VirtualFilesExtension] instance. Serializes any pair
// of specs whose `virtualFiles()` factories race on the JVM-global `mockkStatic` interceptors
// (java.nio.file.Paths, org.jaudiotagger.audio.AudioFileIO, kotlin.io.FilesKt__*, and the
// PathJsonExtensions extension functions). Mirrors lirp's `reactiveScopeMutex`.
internal val virtualFilesMutex = Mutex()

private val STATIC_MOCK_TARGETS =
    listOf(
        "kotlin.io.FilesKt__FileReadWriteKt",
        "kotlin.io.FilesKt__UtilsKt",
        "java.nio.file.Paths",
        "org.jaudiotagger.audio.AudioFileIO",
        "net.transgressoft.commons.music.common.PathJsonExtensionsKt"
    )

/**
 * Per-spec test fixture that materializes Jimfs-backed virtual audio files with mocked
 * JAudioTagger metadata. One instance is created per spec by [virtualFiles], and lifecycle
 * (mockkStatic install/uninstall) is managed by [VirtualFilesExtension].
 *
 * Each instance owns its own in-memory [FileSystem], so two specs never collide on Jimfs
 * inodes. Static interceptors are JVM-global and shared between concurrent specs via
 * [virtualFilesMutex] — specs using `virtualFiles()` run serialized against each other, but
 * remain free to run in parallel with specs that don't use this fixture.
 */
class VirtualFiles internal constructor() {

    val fileSystem: FileSystem = Jimfs.newFileSystem(Configuration.unix())

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
        val tag = createMockedTag(tagType.newMockedTag(), attributes)
        val path = createPath(filePath, fileName, tagType.fileType.extension, tag, fileSystem)
        return path
    }

    /**
     * Materializes a virtual audio file at the exact [targetPath] on [fileSystem],
     * stubbing JAudioTagger reads and `java.nio.file` membership so that downstream
     * services (e.g. `audioItemFromFile(path)`) see a fully readable audio file.
     *
     * The codec/container is derived from the file extension on [targetPath]. The
     * supplied [attributes] populate the mocked tag.
     *
     * @param targetPath absolute path on [fileSystem] where the virtual file should appear
     * @param attributes tag values to expose through the mocked [Tag]
     * @param tagType    mocked tag implementation matching the desired codec
     * @return the same [targetPath], usable as the audio item's path
     */
    fun createAt(
        targetPath: Path,
        attributes: AudioItemTestAttributes,
        tagType: AudioFileTagType,
        fileSystem: FileSystem = targetPath.fileSystem
    ): Path {
        val tag = createMockedTag(tagType.newMockedTag(), attributes)
        return createPathAt(targetPath, tag, fileSystem)
    }

    internal fun installStaticMocks() {
        STATIC_MOCK_TARGETS.forEach { mockkStatic(it) }
    }

    internal fun uninstallStaticMocks() {
        STATIC_MOCK_TARGETS.forEach { unmockkStatic(it) }
    }

    // Strips characters forbidden in Windows path segments (< > : " / \ | ? * and control chars 0-31),
    // trims leading/trailing dots and spaces, and prefixes Windows reserved stems (CON, NUL, COM1..9,
    // LPT1..9) with an underscore. Artist, album, and title attributes are generated by Arb.string(),
    // which produces arbitrary Unicode — passing those raw into Path.of(...) triggers InvalidPathException
    // on Windows even though the virtual filesystem is Jimfs-unix, because the serialized path round-trips
    // through native java.nio.file.Path during JSON deserialization. Tag mocks still receive the original
    // (unsanitized) values so round-trip assertions on artist/album/title continue to hold.
    private fun String.sanitizePathSegment(): String {
        val cleaned =
            replace(Regex("""[<>:"/\\|?*\x00-\x1F]"""), "_")
                .trim('.', ' ')
                .ifEmpty { "_" }
        val stem = cleaned.substringBefore(".")
        return if (stem.uppercase() in WINDOWS_RESERVED_STEMS) "_$cleaned" else cleaned
    }

    private val WINDOWS_RESERVED_STEMS =
        setOf(
            "CON", "PRN", "AUX", "NUL",
            "COM0", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT0", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
        )

    private fun createMockedTag(tag: Tag, attributes: AudioItemTestAttributes): Tag {
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
        val coverBytes = attributes.coverImageBytes

        every { tag.getFirst(FieldKey.TITLE) } returns title
        every { tag.getFirst(FieldKey.ALBUM) } returns album
        every { tag.getFirst(FieldKey.ARTIST) } returns artist
        every { tag.getFirst(FieldKey.ALBUM_ARTIST) } returns albumArtist
        every { tag.getFirst(FieldKey.GENRE) } returns genre
        every { tag.getFirst(FieldKey.YEAR) } returns year.toString()
        every { tag.getFirst(FieldKey.GROUPING) } returns grouping
        every { tag.getFirst(FieldKey.IS_COMPILATION) } returns isCompilation.toString()
        every { tag.getFirst(FieldKey.COUNTRY) } returns country

        comment?.let { every { tag.getFirst(FieldKey.COMMENT) } returns it }
        trackNumber?.let { every { tag.getFirst(FieldKey.TRACK) } returns it.toString() }
        discNumber?.let { every { tag.getFirst(FieldKey.DISC_NO) } returns it.toString() }
        bpm?.let { every { tag.getFirst(FieldKey.BPM) } returns it.toString() }
        encoder?.let { every { tag.getFirst(FieldKey.ENCODER) } returns it }

        every { tag.hasField(FieldKey.TITLE) } returns true
        every { tag.hasField(FieldKey.ALBUM) } returns true
        every { tag.hasField(FieldKey.ARTIST) } returns true
        every { tag.hasField(FieldKey.ALBUM_ARTIST) } returns true
        every { tag.hasField(FieldKey.GENRE) } returns true
        every { tag.hasField(FieldKey.YEAR) } returns true
        every { tag.hasField(FieldKey.GROUPING) } returns true
        every { tag.hasField(FieldKey.IS_COMPILATION) } returns true
        every { tag.hasField(FieldKey.COUNTRY) } returns true
        every { tag.hasField(FieldKey.TRACK) } returns (trackNumber != null)
        every { tag.hasField(FieldKey.DISC_NO) } returns (discNumber != null)
        every { tag.hasField(FieldKey.BPM) } returns (bpm != null)
        every { tag.hasField(FieldKey.COMMENT) } returns (comment != null)
        every { tag.hasField(FieldKey.ENCODER) } returns (encoder != null)

        coverBytes?.let {
            every { tag.firstArtwork } returns
                mockk<Artwork> {
                    every { binaryData } returns it
                }
            every { tag.artworkList } returns listOf(mockk<Artwork>())
        }

        return tag
    }

    private fun createPath(pathToFile: String, fileName: String, extension: String, tag: Tag, fileSystem: FileSystem): Path {
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
        return createPathAt(targetPath, tag, fileSystem)
    }

    private fun createPathAt(targetPath: Path, tag: Tag, fileSystem: FileSystem): Path {
        val parent = targetPath.parent
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent)
        }
        val extension = targetPath.fileName.toString().substringAfterLast('.', missingDelimiterValue = "")
        val filePath = spyk(targetPath)
        // Rendered with the *injected* fileSystem's separator (Jimfs unix → "/", Jimfs windows → "\"),
        // which is independent of the host JVM. Using that string as the stub key is what makes round-trip
        // Paths.get(...) match after serialization encodes audioFilePath.absolutePathString().
        val filePathString = filePath.toString()
        val file = JimfsFileAdapter(filePath)

        Files.write(filePath, byteArrayOf(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)

        // Produce a file:// URI from the native path string so that toJsonUri() returns a
        // file:// URI instead of a jimfs:// URI. Path.of(filePathString) uses the default
        // (real) filesystem to build a URI from the platform-native path string, giving the
        // correct file:// form for all three Jimfs configs (unix/windows/osx).
        val fileUri = Path.of(filePathString).toUri()
        val fileUriString = fileUri.toString()
        every { filePath.toFile() } returns file
        every { filePath.absolutePathString() } returns filePathString
        every { filePath.toAbsolutePath() } returns filePath
        every { filePath.normalize() } returns filePath
        // Stub toJsonUri() at the extension function level since Jimfs Path.toUri() returns
        // a jimfs:// scheme that toPathFromJsonUri() would reject.
        every { filePath.toJsonUri() } returns fileUriString

        val audioFile =
            mockk<AudioFile> {
                every { getTag() } returns tag
                every { audioHeader } returns
                    mockk {
                        every { trackLength } returns 180
                        every { sampleRateAsNumber } returns 44100
                        every { format } returns extension
                        every { encodingType } returns format.uppercase()
                        every { bitRate } returns "320"
                    }
            }

        every { Paths.get(filePathString) } returns filePath
        every { Paths.get(fileUri) } returns filePath
        every { Files.exists(filePath) } returns true
        every { Files.isRegularFile(filePath) } returns true
        every { Files.isReadable(filePath) } returns true
        every { AudioFileIO.read(file) } returns audioFile

        return filePath
    }
}

/**
 * Kotest [SpecExtension] that wraps a spec's execution in [virtualFilesMutex] and installs
 * the [VirtualFiles] static mocks before [execute] runs, then unmocks them and closes the
 * per-spec Jimfs filesystem after [execute] returns. Register per-spec via the
 * [virtualFiles] convenience factory.
 */
class VirtualFilesExtension : SpecExtension {

    val files: VirtualFiles = VirtualFiles()

    override suspend fun intercept(spec: Spec, execute: suspend (Spec) -> Unit) {
        virtualFilesMutex.withLock {
            files.installStaticMocks()
            try {
                execute(spec)
            } finally {
                try {
                    files.uninstallStaticMocks()
                } finally {
                    // Jimfs FileSystem is Closeable; without this each spec leaks one in-memory
                    // filesystem (inode/page caches included) for the lifetime of the JVM.
                    files.fileSystem.close()
                }
            }
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
 *         }
 *     })
 */
fun Spec.virtualFiles(): VirtualFiles {
    val ext = VirtualFilesExtension()
    extension(ext)
    return ext.files
}

internal class JimfsFileAdapter(private val path: Path) : File(path.toString()) {
    override fun exists() = Files.exists(path)

    override fun getName() = path.fileName.toString()

    override fun toPath() = path

    override fun getAbsolutePath() = path.toAbsolutePath().toString()

    override fun length() = Files.size(path)
}