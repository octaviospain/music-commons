package net.transgressoft.commons.music.audio

import net.transgressoft.commons.data.Registry
import java.util.*

interface ArtistCatalogRegistry : Registry<String, ArtistCatalog> {

    fun findFirstByName(artistName: String): Optional<ArtistCatalog>
}