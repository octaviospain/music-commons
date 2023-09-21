package net.transgressoft.commons.music.audio

import net.transgressoft.commons.data.Registry
import java.util.*

interface ArtistCatalogRegistry : Registry<String, ArtistCatalog> {

    fun findFirst(artist: Artist) = findFirst(artist.name)

    fun findFirst(artistName: String): Optional<ArtistCatalog>
}