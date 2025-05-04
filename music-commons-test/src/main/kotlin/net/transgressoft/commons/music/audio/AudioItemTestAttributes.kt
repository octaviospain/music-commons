package net.transgressoft.commons.music.audio

import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime

data class AudioItemTestAttributes(
    var path: Path,
    var title: String,
    var duration: Duration,
    var bitRate: Int,
    var artist: Artist,
    var album: Album,
    var genre: Genre,
    var comments: String? = null,
    var trackNumber: Short? = null,
    var discNumber: Short? = null,
    var bpm: Float? = null,
    var encoder: String? = null,
    var encoding: String? = null,
    var coverImageBytes: ByteArray? = null,
    var dateOfCreation: LocalDateTime,
    var lastDateModified: LocalDateTime,
    var playCount: Short,
    var id: Int = UNASSIGNED_ID
)