package net.transgressoft.commons.music.audio

data class ArtistView<I : ReactiveAudioItem<I>>(val artist: Artist, val albums: Set<AlbumView<I>>)
