package net.transgressoft.commons.music.audio

import net.transgressoft.commons.query.QueryEntity
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime

/**
 * @author Octavio Calleya
 */
interface AudioItem : QueryEntity {
    fun path(): Path
    fun path(path: Path): AudioItem
    fun fileName(): String
    fun extension(): String
    fun title(): String
    fun title(name: String): AudioItem
    fun artist(): Artist
    fun artist(artist: Artist): AudioItem
    fun artistsInvolved(): Set<String>
    fun album(): Album
    fun album(album: Album): AudioItem
    fun genre(): Genre
    fun genre(genre: Genre): AudioItem
    fun comments(): String
    fun comments(comments: String): AudioItem
    fun trackNumber(): Short
    fun trackNumber(trackNumber: Short): AudioItem
    fun discNumber(): Short
    fun discNumber(discNumber: Short): AudioItem
    fun bpm(): Float
    fun bpm(bpm: Float): AudioItem
    fun duration(): Duration
    fun encoder(): String
    fun encoder(encoder: String): AudioItem
    fun encoding(): String
    fun encoding(encoding: String): AudioItem
    fun length(): Long
    fun bitRate(): Int
    fun dateOfInclusion(): LocalDateTime
    fun lastDateModified(): LocalDateTime
}