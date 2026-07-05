package net.transgressoft.commons.music.audio

import java.nio.file.Path

/**
 * Domain-typed seam for reading and writing audio metadata against a file at a given [Path].
 *
 * Production callers wire in the JAudioTagger-backed implementation in `music-commons-core`; tests
 * inject a filesystem-aware in-memory implementation from `music-commons-test` that stores metadata
 * in maps keyed by [Path], with no JAudioTagger dependency. Future native backends drop in here as
 * additional implementations with no consumer-side change.
 */
interface AudioMetadataIO {

    /**
     * Reads tag and header metadata from the audio file at [path] and returns a populated
     * [AudioItemMetadata].
     *
     * Implementations fall back to safe defaults (empty title, [Artist.UNKNOWN], [AlbumDetails.UNKNOWN],
     * empty genre set) when the file's tag block is unreadable or absent. Cover image bytes are
     * not included on this path — call [loadCover] when needed.
     *
     * @param path location of the audio file to inspect
     * @return populated [AudioItemMetadata] with all 12 non-cover fields seeded from tag + header
     */
    fun readMetadata(path: Path): AudioItemMetadata

    /**
     * Reads the raw cover image bytes from the audio file at [path], or `null` when the file has
     * no artwork or cannot be read.
     *
     * No defensive copy is made at this layer — callers that hand the bytes to external consumers
     * are responsible for copying when isolation is required.
     */
    fun loadCover(path: Path): ByteArray?

    /**
     * Convenience overload that reads the cover bytes from the file backing [item].
     */
    fun loadCover(item: ReactiveAudioItem<*>): ByteArray? = loadCover(item.path)

    /**
     * Writes the current property values of [item] back to the audio file at [item].path,
     * synchronously.
     *
     * No coroutine wrapping — caller chooses the dispatcher.
     *
     * @throws AudioItemManipulationException if cover artwork creation fails during the write
     */
    fun writeMetadata(item: ReactiveAudioItem<*>)
}