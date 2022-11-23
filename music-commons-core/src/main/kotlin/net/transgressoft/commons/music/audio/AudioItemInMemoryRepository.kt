package net.transgressoft.commons.music.audio

import java.nio.file.Path

open class AudioItemInMemoryRepository(
    audioItems: MutableMap<Int, AudioItem> = mutableMapOf(),
) : AudioItemInMemoryRepositoryBase<AudioItem>(audioItems) {

    override fun getNewMetadataReader(path: Path): JAudioTaggerMetadataReaderBase<AudioItem> = JAudioTaggerMetadataReader(path)

    override fun containsAudioItemWithArtist(artistName: String): Boolean {
        return search { it.artistsInvolved.contains(artistName) }.isNotEmpty()
    }

    override fun isAlbumNotEmpty(album: Album) = search { it.album == album }.isNotEmpty()
}