package net.transgressoft.commons.fx.music.audio

import net.transgressoft.commons.music.audio.AudioItemMetadata
import net.transgressoft.commons.music.audio.AudioItemTestFactory
import net.transgressoft.commons.music.audio.AudioMetadataIO
import net.transgressoft.commons.music.audio.JAudioTaggerMetadataIO
import net.transgressoft.commons.music.audio.UNASSIGNED_ID
import java.nio.file.Files
import java.nio.file.Path

/**
 * Bridge for creating [FXAudioItem] instances in tests.
 *
 * Mirrors `MutableAudioItemTestBridge` from `music-commons-core`. For tests that point at a real
 * audio file on disk (or on a Jimfs filesystem materialized by `virtualFiles()`), the bridge
 * reads tag + cover via [AudioMetadataIO] so the resulting item mirrors what
 * `FXAudioLibrary.createFromFile` would produce. For tests that pass synthetic / non-existent
 * paths, the bridge skips the read and seeds [AudioItemMetadata] defaults.
 */
internal object FXAudioItemTestBridge {

    fun createFxAudioItem(path: Path, id: Int = UNASSIGNED_ID): FXAudioItem =
        FXAudioItem(path, id, readMetadataOrDefault(path))

    fun createFxAudioItem(path: Path, metadataIO: AudioMetadataIO): FXAudioItem =
        FXAudioItem(path, AudioItemTestFactory.nextTestId(), readMetadataOrDefault(path, metadataIO))

    fun createFxAudioItem(path: Path, id: Int, metadataIO: AudioMetadataIO): FXAudioItem =
        FXAudioItem(path, id, readMetadataOrDefault(path, metadataIO))

    fun createFxAudioItemFromMetadata(path: Path, id: Int, metadata: AudioItemMetadata): FXAudioItem =
        FXAudioItem(path, id, metadata)

    private fun readMetadataOrDefault(path: Path, metadataIO: AudioMetadataIO = JAudioTaggerMetadataIO()): AudioItemMetadata =
        if (Files.exists(path) && Files.isRegularFile(path)) {
            metadataIO.readMetadata(path).copy(coverBytes = metadataIO.loadCover(path))
        } else {
            AudioItemMetadata()
        }
}