package net.transgressoft.commons.music.audio

data class AlbumView<I : ReactiveAudioItem<I>>(val albumName: String, val audioItems: Set<I>) : Comparable<AlbumView<I>> {

    override fun compareTo(other: AlbumView<I>) = audioItems.first().album.compareTo(other.audioItems.first().album)
}