package net.transgressoft.commons.music.audio

import java.nio.file.Path

open class AudioItemInMemoryRepository(
    audioItems: MutableMap<Int, AudioItem> = mutableMapOf(),
) : AudioItemInMemoryRepositoryBase<AudioItem>(audioItems) {

    override fun getNewMetadataReader(path: Path): JAudioTaggerMetadataReaderBase<AudioItem> = JAudioTaggerMetadataReader(path)

    override fun updateAudioItem(audioItem: AudioItem, change: AudioItemMetadataChange): AudioItem {
        return audioItem.update(change)
    }
}