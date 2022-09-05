package net.transgressoft.commons.music.audio

import net.transgressoft.commons.event.QueryEventDispatcher
import java.nio.file.Files
import java.nio.file.Path

class AudioItemInMemoryRepository (
    audioItems: MutableMap<Int, AudioItem> = mutableMapOf(),
    eventDispatcher: QueryEventDispatcher<AudioItem>?,
) : AudioItemInMemoryRepositoryBase<AudioItem>(audioItems, eventDispatcher) {

    @Throws(AudioItemManipulationException::class)
    override fun createFromFile(path: Path?): AudioItem {
        requireNotNull(path)
        require(!Files.notExists(path)) { "File " + path.toAbsolutePath() + " does not exist" }

        val audioItem = readAudioItem(path)
        add(audioItem)
        return audioItem
    }

    @Throws(AudioItemManipulationException::class)
    private fun readAudioItem(path: Path?): AudioItem {
        return JAudioTaggerMetadataReader().readAudioItem(path!!)
    }
}
