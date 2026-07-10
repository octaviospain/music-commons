package net.transgressoft.commons.music.itunes

/**
 * Progress snapshot emitted during an iTunes import operation.
 *
 * @property itemsProcessed Number of tracks processed so far.
 * @property totalItems Total number of tracks to process.
 * @property currentFile Path of the track currently being processed.
 * @since 1.0
 */
public data class ImportProgress(
    val itemsProcessed: Int,
    val totalItems: Int,
    val currentFile: String
)