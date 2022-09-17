package net.transgressoft.commons.music.audio

import mu.KotlinLogging
import net.transgressoft.commons.event.QueryEventDispatcher
import net.transgressoft.commons.music.audio.ArtistsInvolvedAttribute.ARTISTS_INVOLVED
import java.nio.file.Files
import java.nio.file.Path

internal class AudioItemInMemoryRepository(
    audioItems: MutableMap<Int, AudioItem> = mutableMapOf(),
    eventDispatcher: QueryEventDispatcher<AudioItem> = DefaultAudioItemEventDispatcher(),
) : AudioItemInMemoryRepositoryBase<AudioItem>(audioItems, eventDispatcher) {

    private val logger = KotlinLogging.logger {}

    @Throws(AudioItemManipulationException::class)
    override fun createFromFile(path: Path): AudioItem {
        require(!Files.notExists(path)) { "File " + path.toAbsolutePath() + " does not exist" }

        val audioItemAttributes = JAudioTaggerMetadataReader(path).readAudioItemAttributes()
        val audioItem = ImmutableAudioItem(newId(), audioItemAttributes)
        logger.debug { "New AudioItem read from file $path" }
        add(audioItem)
        return audioItem
    }

    override fun containsAudioItemWithArtist(artistName: String): Boolean {
        return search(ARTISTS_INVOLVED.containsElement(artistName)).isNotEmpty()
    }
}