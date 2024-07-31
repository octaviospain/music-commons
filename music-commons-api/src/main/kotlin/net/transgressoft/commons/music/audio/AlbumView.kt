package net.transgressoft.commons.music.audio

data class AlbumView<I : ReactiveAudioItem<I>>(val albumName: String, val audioItems: Set<I>)