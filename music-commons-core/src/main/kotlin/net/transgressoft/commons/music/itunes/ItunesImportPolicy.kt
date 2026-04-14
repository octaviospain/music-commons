package net.transgressoft.commons.music.itunes

import net.transgressoft.commons.music.audio.AudioFileType

/**
 * Configures how tracks from an iTunes library are imported into a [MusicLibrary][net.transgressoft.commons.music.MusicLibrary].
 *
 * The policy controls metadata source, play count transfer, tag write-back, missing file handling,
 * and file type filtering. All fields have sensible defaults matching the most common import scenario.
 *
 * @property useFileMetadata When `true` (default), all metadata is read from audio file tags via
 *   `createFromFile()`. When `false`, only technical metadata (bitrate, duration, encoder) is read
 *   from file tags, and user-facing fields (title, artist, album, genre) come from iTunes data.
 * @property holdPlayCount When `true` (default), the iTunes play count is applied to the imported item
 *   regardless of the [useFileMetadata] setting.
 * @property writeMetadata When `true` (default), iTunes metadata is written to the audio file's ID3/Vorbis
 *   tags after import. This is independent of [useFileMetadata]: when both `writeMetadata=true` and
 *   `useFileMetadata=false`, iTunes metadata overwrites existing file tags. This operation is destructive.
 * @property ignoreNotFound When `true` (default), tracks whose files do not exist on disk are skipped
 *   and counted in [ItunesImportResult.skippedCount]. When `false`, missing files cause an error entry.
 * @property acceptedFileTypes Set of audio file types to import. Tracks with file extensions not in this
 *   set are skipped.
 */
data class ItunesImportPolicy(
    val useFileMetadata: Boolean = true,
    val holdPlayCount: Boolean = true,
    val writeMetadata: Boolean = true,
    val ignoreNotFound: Boolean = true,
    val acceptedFileTypes: Set<AudioFileType> = AudioFileType.entries.toSet()
)