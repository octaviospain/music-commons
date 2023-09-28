package net.transgressoft.commons.music.audio

import net.transgressoft.commons.data.Registry
import java.util.*

internal interface ArtistCatalogRegistry : Registry<String, MutableArtistCatalog> {

    fun findFirst(artist: Artist) = findFirst(artist.name)

    fun findFirst(artistName: String): Optional<MutableArtistCatalog>

    fun findAlbum(albumName: String, artist: Artist): Optional<Album>
}