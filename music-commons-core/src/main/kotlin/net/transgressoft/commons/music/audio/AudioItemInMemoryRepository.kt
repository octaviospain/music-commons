package net.transgressoft.commons.music.audio

import mu.KotlinLogging
import net.transgressoft.commons.music.audio.AudioItemAttribute.*
import java.nio.file.Files
import java.nio.file.Path

open class AudioItemInMemoryRepository(
    audioItems: MutableMap<Int, AudioItem> = mutableMapOf(),
) : AudioItemInMemoryRepositoryBase<AudioItem>(audioItems) {

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

    override fun isAlbumNotEmpty(album: Album) = search(ALBUM.equalsTo(album)).isNotEmpty()
}