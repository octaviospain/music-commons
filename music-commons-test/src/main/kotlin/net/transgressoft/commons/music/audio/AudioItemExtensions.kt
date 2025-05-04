package net.transgressoft.commons.music.audio

fun AudioItem.update(change: AudioItemChange) {
    change.title?.let { title = it }
    change.artist?.let { artist = it }
    album =
        ImmutableAlbum(
            change.albumName ?: album.name,
            change.albumArtist ?: album.albumArtist,
            change.isCompilation ?: album.isCompilation,
            change.year?.takeIf { year -> year > 0 } ?: album.year,
            change.label ?: album.label
        )
    change.genre ?: genre
    change.comments ?: comments
    change.trackNumber?.takeIf { trackNum -> trackNum > 0 } ?: trackNumber
    change.discNumber?.takeIf { discNum -> discNum > 0 } ?: discNumber
    change.bpm?.takeIf { bpm -> bpm > 0 } ?: bpm
    change.coverImageBytes ?: coverImageBytes
}