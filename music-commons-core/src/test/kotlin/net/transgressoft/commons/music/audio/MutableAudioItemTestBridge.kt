package net.transgressoft.commons.music.audio

import java.nio.file.Files
import java.nio.file.Path

/**
 * Bridge for creating [MutableAudioItem] instances in tests.
 * Required because the primary constructor is internal to the core module.
 *
 * For tests that point at a real audio file on disk, the bridge reads tag metadata
 * via [AudioMetadataIO] and wires the back-ref on the returned item, mirroring what
 * [DefaultAudioLibrary.createFromFile] produces. Cover bytes are loaded lazily on first
 * explicit [ReactiveAudioItem.coverImageBytes] access. For tests that pass synthetic or
 * non-existent paths, the bridge skips the read and seeds [AudioItemMetadata] defaults.
 */
object MutableAudioItemTestBridge {

    fun createAudioItem(path: Path, id: Int): AudioItem = createAudioItem(path, id, JAudioTaggerMetadataIO())

    fun createAudioItem(path: Path): AudioItem = createAudioItem(path, AudioItemTestFactory.nextTestId(), JAudioTaggerMetadataIO())

    fun createAudioItem(path: Path, id: Int, metadataIO: AudioMetadataIO): AudioItem =
        buildItem(path, id, metadataIO)

    fun createAudioItem(path: Path, metadataIO: AudioMetadataIO): AudioItem =
        buildItem(path, AudioItemTestFactory.nextTestId(), metadataIO)

    private fun buildItem(path: Path, id: Int, metadataIO: AudioMetadataIO): AudioItem {
        val metadata =
            if (Files.exists(path) && Files.isRegularFile(path)) {
                metadataIO.readMetadata(path)
            } else {
                AudioItemMetadata()
            }
        return MutableAudioItem(path, id, metadata).also { item ->
            if (Files.exists(path) && Files.isRegularFile(path)) {
                item.metadataIO = metadataIO
            }
        }
    }
}