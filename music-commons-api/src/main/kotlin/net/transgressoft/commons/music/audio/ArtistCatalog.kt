package net.transgressoft.commons.music.audio

import net.transgressoft.commons.IdentifiableEntity

interface ArtistCatalog : IdentifiableEntity<String> {

    val artist: Artist

    val albums: Map<String, ExtendedAlbum>
}