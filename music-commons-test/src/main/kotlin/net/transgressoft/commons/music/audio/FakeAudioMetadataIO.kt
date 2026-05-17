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

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.Tag
import java.nio.file.FileSystems
import java.nio.file.Path

/**
 * In-memory test fake of [AudioMetadataIO] backed by per-[Path] maps of real JAudioTagger tags,
 * header info, and cover bytes.
 *
 * Specs stub the metadata they need with [stub], [stubHeader], and [stubCover], then inject the
 * fake into the system under test. No JVM-global static interception and no
 * `mockk<Tag>()` plumbing for individual fields — the fake constructs **real** format-specific
 * tag instances via [AudioFileTagType.newActualTag] so `setField`/`getFirst`/`hasField` behave
 * exactly as they do against on-disk files. This preserves format-specific quirks (e.g. `WavTag`
 * throwing `UnsupportedOperationException` for unsupported fields) which the production
 * `getFieldIfExisting` helper already handles.
 *
 * Tags returned from [readTag] reflect format-correct behavior; new `FieldKey`s require no
 * fake-side maintenance because `setField`/`getFirst` work natively on the real tag types.
 *
 * Example:
 * ```
 * val io = FakeAudioMetadataIO()
 * io.stub(path, AudioFileTagType.ID3_V_24) {
 *     setField(FieldKey.ARTIST, "Some Artist")
 *     setField(FieldKey.ALBUM, "Some Album")
 * }
 * io.stubHeader(path, HeaderInfo("MPEG-1 Layer 3", 320, 180L, "MPEG-1 Layer 3"))
 * io.stubCover(path, coverBytes)
 *
 * // Inject into SUT:
 * val item = MutableAudioItem(path, metadataIO = io)
 * ```
 *
 * [readHeaderInfo] returns [DEFAULT_HEADER] for paths that have not been stubbed via
 * [stubHeader]. Specs that assert on header values must call [stubHeader] explicitly.
 */
class FakeAudioMetadataIO : AudioMetadataIO {

    val tags = mutableMapOf<Path, Tag>()
    val covers = mutableMapOf<Path, ByteArray?>()
    val headers = mutableMapOf<Path, HeaderInfo>()

    /**
     * Stubs a real format-specific [Tag] for [path], populated by [build].
     *
     * The tag is constructed via [AudioFileTagType.newActualTag] so it behaves identically to one
     * read from disk by JAudioTagger.
     *
     * @param path the file path to associate with the tag
     * @param tagType which JAudioTagger tag implementation to create (defaults to ID3v2.4)
     * @param build builder lambda that receives the freshly-created tag for field population
     * @return the constructed tag, also stored for subsequent [readTag] calls on [path]
     */
    @JvmOverloads
    fun stub(path: Path, tagType: AudioFileTagType = AudioFileTagType.ID3_V_24, build: Tag.() -> Unit = {}): Tag {
        val tag = tagType.newActualTag().apply(build)
        tags[path] = tag
        return tag
    }

    /**
     * Stubs the raw cover bytes returned by [readCoverBytes] for [path]. Pass `null` to simulate a
     * file without artwork.
     */
    fun stubCover(path: Path, bytes: ByteArray?) {
        covers[path] = bytes
    }

    /**
     * Stubs the [HeaderInfo] returned by [readHeaderInfo] for [path]. Paths not stubbed fall back
     * to [DEFAULT_HEADER].
     */
    fun stubHeader(path: Path, header: HeaderInfo) {
        headers[path] = header
    }

    override fun readTag(path: Path): Tag =
        tags[path] ?: if (path.fileSystem == FileSystems.getDefault()) {
            // Real on-disk audio files (e.g. `Arb.realAudioFile()` fixtures) are not pre-stubbed —
            // fall through to JAudioTagger so specs that mix Jimfs and real-disk paths via the same
            // VirtualFiles fixture still work.
            AudioFileIO.read(path.toFile()).tag
        } else {
            error("No tag stubbed for $path. Call FakeAudioMetadataIO.stub(path) first.")
        }

    override fun readCoverBytes(path: Path): ByteArray? =
        covers[path] ?: if (path.fileSystem == FileSystems.getDefault()) {
            val file = path.toFile()
            if (!file.exists() || !file.canRead()) null
            else
                runCatching { AudioFileIO.read(file).tag }
                    .getOrNull()
                    ?.let { tag -> if (tag.artworkList.isNotEmpty()) tag.firstArtwork.binaryData else null }
        } else {
            null
        }

    override fun writeTag(path: Path, tag: Tag) {
        if (path.fileSystem == FileSystems.getDefault() && !tags.containsKey(path)) {
            // Persist real on-disk audio files through JAudioTagger so writeMetadata() tests against
            // `Arb.realAudioFile()` continue to mutate the underlying file.
            val audioFile = AudioFileIO.read(path.toFile())
            audioFile.tag = tag
            audioFile.commit()
        }
        tags[path] = tag
    }

    override fun readHeaderInfo(path: Path): HeaderInfo =
        headers[path] ?: if (path.fileSystem == FileSystems.getDefault()) {
            val header = AudioFileIO.read(path.toFile()).audioHeader
            val bitRateStr = header.bitRate
            HeaderInfo(
                encodingType = header.encodingType,
                bitRate = if (bitRateStr.startsWith("~")) bitRateStr.substring(1).toInt() else bitRateStr.toInt(),
                trackLengthSeconds = header.trackLength.toLong(),
                format = header.format
            )
        } else {
            DEFAULT_HEADER
        }

    companion object {

        /**
         * Reasonable default header returned for paths that have not been explicitly stubbed via
         * [stubHeader] — encoding/format of MP3 at 320kbps, 3-minute duration.
         */
        val DEFAULT_HEADER =
            HeaderInfo(
                encodingType = "MPEG-1 Layer 3",
                bitRate = 320,
                trackLengthSeconds = 180L,
                format = "MPEG-1 Layer 3"
            )
    }
}