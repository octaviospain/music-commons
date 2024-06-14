package net.transgressoft.commons.music.audio

import net.transgressoft.commons.IdentifiableEntity
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime

interface AudioItem : IdentifiableEntity<Int> {
    val path: Path
    val fileName: String
    val extension: String
    var title: String
    val duration: Duration
    val bitRate: Int
    var artist: Artist
    val artistsInvolved: Set<String>
    var album: Album
    var genre: Genre
    var comments: String?
    var trackNumber: Short?
    var discNumber: Short?
    var bpm: Float?
    val encoder: String?
    val encoding: String?
    val length: Long
    var coverImageBytes: ByteArray?
    val dateOfCreation: LocalDateTime
    val lastDateModified: LocalDateTime

    fun writeMetadata()
}
