package net.transgressoft.commons.music.audio

internal data class MutableArtistCatalog(override val artist: Artist, override val albums: MutableMap<String, MutableExtendedAlbum> = HashMap()) :
    ArtistCatalog {

    internal constructor(audioItem: MutableAudioItem) : this(audioItem.artist) {
        audioItem.album.let { album ->
            val newAlbum = MutableExtendedAlbum(album.name, album.albumArtist, album.isCompilation, album.year, album.label)
            newAlbum.audioItems.add(audioItem)
            albums[album.name] = newAlbum
        }
    }

    override val id: String = "${artist.name}-${artist.countryCode.name}"

    override val uniqueId: String = "${artist.name}-${artist.countryCode.name}"

    internal fun addAudioItem(audioItem: MutableAudioItem) {
        albums.merge(audioItem.album.name, MutableExtendedAlbum(audioItem)) { album, _ ->
            album.addAudioItem(audioItem)
            album
        }
    }

    internal fun removeAudioItem(audioItem: MutableAudioItem) {
        albums[audioItem.album.name]?.removeAudioItem(audioItem)
    }
}