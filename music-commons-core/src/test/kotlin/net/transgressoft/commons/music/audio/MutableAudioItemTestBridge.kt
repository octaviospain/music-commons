package net.transgressoft.commons.music.audio

import java.nio.file.Files
import java.nio.file.Path

/**
 * Bridge for creating [MutableAudioItem] instances in tests.
 * Required because the primary constructor is internal to the core module.
 *
 * For tests that point at a real audio file on disk, the bridge reads both tag and cover
 * via [AudioMetadataIO] so the resulting item mirrors what `library.createFromFile`
 * would produce. For tests that pass synthetic / non-existent paths, the bridge skips the
 * read and seeds [AudioItemMetadata] defaults.
 */
object MutableAudioItemTestBridge {

    fun createAudioItem(path: Path, id: Int): AudioItem = MutableAudioItem(path, id, readMetadataOrDefault(path))

    fun createAudioItem(path: Path): AudioItem = MutableAudioItem(path, AudioItemTestFactory.nextTestId(), readMetadataOrDefault(path))

    fun createAudioItem(path: Path, id: Int, metadataIO: AudioMetadataIO): AudioItem =
        MutableAudioItem(path, id, readMetadataOrDefault(path, metadataIO))

    fun createAudioItem(path: Path, metadataIO: AudioMetadataIO): AudioItem =
        MutableAudioItem(path, AudioItemTestFactory.nextTestId(), readMetadataOrDefault(path, metadataIO))

    private fun readMetadataOrDefault(path: Path, metadataIO: AudioMetadataIO = JAudioTaggerMetadataIO()): AudioItemMetadata =
        if (Files.exists(path) && Files.isRegularFile(path)) {
            metadataIO.readMetadata(path).copy(coverBytes = metadataIO.loadCover(path))
        } else {
            AudioItemMetadata()
        }
}