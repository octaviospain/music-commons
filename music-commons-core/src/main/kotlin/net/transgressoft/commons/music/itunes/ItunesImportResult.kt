package net.transgressoft.commons.music.itunes

/**
 * Result of an iTunes library import operation.
 *
 * @property importedCount Number of tracks successfully imported as [AudioItem][net.transgressoft.commons.music.audio.AudioItem] entities.
 * @property skippedCount Number of tracks skipped (missing file, unsupported type, or duplicate).
 * @property errorCount Number of tracks that caused errors during import.
 * @property errors Per-track error details, each pairing the track's iTunes title with the exception message.
 * @property playlistsCreated Number of playlists successfully created in the hierarchy.
 */
data class ItunesImportResult(
    val importedCount: Int,
    val skippedCount: Int,
    val errorCount: Int,
    val errors: List<ImportError> = emptyList(),
    val playlistsCreated: Int = 0
)

/**
 * Details of a single track import error.
 *
 * @property trackTitle Title of the iTunes track that failed.
 * @property message Error description.
 */
data class ImportError(
    val trackTitle: String,
    val message: String
)