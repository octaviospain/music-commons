package net.transgressoft.commons.music.audio

import java.util.*

interface ExtendedAlbum : Album {

    val audioItems: SortedSet<MutableAudioItem>
}