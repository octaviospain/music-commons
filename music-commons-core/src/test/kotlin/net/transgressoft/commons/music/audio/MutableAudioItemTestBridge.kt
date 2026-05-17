package net.transgressoft.commons.music.audio

import java.nio.file.Path

/**
 * Bridge for creating [MutableAudioItem] instances in tests.
 * Required because the primary constructor is internal to the core module.
 */
object MutableAudioItemTestBridge {

    fun createAudioItem(path: Path, id: Int): AudioItem = MutableAudioItem(path, id)

    fun createAudioItem(path: Path): AudioItem = MutableAudioItem(path, AudioItemTestFactory.nextTestId())

    fun createAudioItem(path: Path, id: Int, metadataUtils: AudioItemMetadataUtils): AudioItem =
        MutableAudioItem(path, id, metadataUtils)

    fun createAudioItem(path: Path, metadataUtils: AudioItemMetadataUtils): AudioItem =
        MutableAudioItem(path, AudioItemTestFactory.nextTestId(), metadataUtils)
}