package net.transgressoft.commons.music.audio

import net.transgressoft.commons.IdentifiableEntity
import java.util.*

interface ArtistCatalog : IdentifiableEntity<String> {

    val artist: Artist

    val size: Int

    val isEmpty: Boolean
        get() = size == 0

    fun findAlbum(album: Album) = findAlbum(album.name)

    fun findAlbum(albumName: String): Optional<Album>

    fun containsAudioItem(audioItem: AudioItem): Boolean
}