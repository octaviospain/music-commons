package net.transgressoft.commons.music.audio

import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime

interface AudioItemBuilder<I : AudioItem> {

     val id: Int
     val path: Path
     val title: String
     val duration: Duration
     val bitRate: Int
     val artist: Artist
     val album: Album
     val genre: Genre
     val comments: String?
     val trackNumber: Short?
     val discNumber: Short?
     val bpm: Float?
     val encoder: String?
     val encoding: String?
     val dateOfCreation: LocalDateTime
     val lastDateModified: LocalDateTime
    
    fun build(): I

    fun id(id: Int): AudioItemBuilder<I>
    fun path(path: Path): AudioItemBuilder<I>
    fun title(title: String): AudioItemBuilder<I>
    fun duration(duration: Duration): AudioItemBuilder<I>
    fun bitRate(bitRate: Int): AudioItemBuilder<I>
    fun artist(artist: Artist): AudioItemBuilder<I>
    fun album(album: Album): AudioItemBuilder<I>
    fun genre(genre: Genre): AudioItemBuilder<I>
    fun comments(comments: String?): AudioItemBuilder<I>
    fun trackNumber(trackNumber: Short?): AudioItemBuilder<I>
    fun discNumber(discNumber: Short?): AudioItemBuilder<I>
    fun bpm(bpm: Float?): AudioItemBuilder<I>
    fun encoder(encoder: String?): AudioItemBuilder<I>
    fun encoding(encoding: String?): AudioItemBuilder<I>
    fun dateOfCreation(dateOfCreation: LocalDateTime): AudioItemBuilder<I>
    fun lastDateModified(lastDateModified: LocalDateTime): AudioItemBuilder<I>
}