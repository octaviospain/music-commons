package net.transgressoft.commons.music.audio

import net.transgressoft.commons.event.QueryEventDispatcher
import java.nio.file.Files
import java.nio.file.Path

class AudioItemInMemoryRepository (
    audioItems: MutableMap<Int, AudioItem> = mutableMapOf(),
    eventDispatcher: QueryEventDispatcher<AudioItem>?,
) : AudioItemInMemoryRepositoryBase<AudioItem>(audioItems, eventDispatcher) {

    @Throws(AudioItemManipulationException::class)
    override fun createFromFile(path: Path): AudioItem {
        require(!Files.notExists(path)) { "File " + path.toAbsolutePath() + " does not exist" }

        val audioItemAttributes = JAudioTaggerMetadataReader().readAudioItem(path)
        val audioItem = ImmutableAudioItem(newId(), audioItemAttributes)
        add(audioItem)
        return audioItem
    }
}
