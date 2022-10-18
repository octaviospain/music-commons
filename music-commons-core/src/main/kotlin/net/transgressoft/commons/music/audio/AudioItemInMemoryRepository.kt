package net.transgressoft.commons.music.audio

import mu.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path

open class AudioItemInMemoryRepository(
    audioItems: MutableMap<Int, AudioItem> = mutableMapOf(),
) : AudioItemInMemoryRepositoryBase<AudioItem>(audioItems) {

    private val logger = KotlinLogging.logger {}

    @Throws(AudioItemManipulationException::class)
    override fun createFromFile(path: Path): AudioItem {
        require(!Files.notExists(path)) { "File " + path.toAbsolutePath() + " does not exist" }

        val audioItemAttributes = AudioItemUtils.readAudioItemAttributes(path)
        val audioItem = ImmutableAudioItem(newId(), audioItemAttributes)
        logger.debug { "New AudioItem read from file $path" }
        add(audioItem)
        return audioItem
    }

    override fun containsAudioItemWithArtist(artistName: String): Boolean {
        return search { it.artistsInvolved.contains(artistName) }.isNotEmpty()
    }

    override fun isAlbumNotEmpty(album: Album) = search { it.album == album }.isNotEmpty()
}