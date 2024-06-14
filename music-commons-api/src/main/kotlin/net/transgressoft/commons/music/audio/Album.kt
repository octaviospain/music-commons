package net.transgressoft.commons.music.audio

/**
 * @author Octavio Calleya
 */
interface Album : Comparable<Album> {
    val name: String
    val albumArtist: Artist
    val isCompilation: Boolean
    val year: Short?
    val label: Label
}