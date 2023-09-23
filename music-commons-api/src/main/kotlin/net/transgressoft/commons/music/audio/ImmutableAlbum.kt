package net.transgressoft.commons.music.audio

import java.util.*

interface ImmutableAlbum : Album {

    override val audioItems: SortedSet<AudioItem>
}