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
) {
    fun path(_path: Path) = apply { path = _path }

    fun title(_title: String) = apply { title = _title }

    fun duration(_duration: Duration) = apply { duration = _duration }

    fun bitRate(_bitRate: Int) = apply { bitRate = _bitRate }

    fun artist(_artist: Artist) = apply { artist = _artist }

    fun album(_album: Album) = apply { album = _album }

    fun genre(_genre: Genre) = apply { genre = _genre }

    fun comments(_comments: String?) = apply { comments = _comments }

    fun trackNumber(_trackNumber: Short?) = apply { trackNumber = _trackNumber }

    fun discNumber(_discNumber: Short?) = apply { discNumber = _discNumber }

    fun bpm(_bpm: Float?) = apply { bpm = _bpm }

    fun encoder(_encoder: String?) = apply { encoder = _encoder }

    fun encoding(_encoding: String?) = apply { encoding = _encoding }

    fun coverImageBytes(_coverImageBytes: ByteArray?) = apply { coverImageBytes = _coverImageBytes }

    fun dateOfCreation(_dateOfCreation: LocalDateTime) = apply { dateOfCreation = _dateOfCreation }

    fun lastDateModified(_lastDateModified: LocalDateTime) = apply { lastDateModified = _lastDateModified }

    fun playCount(_playCount: Short) = apply { playCount = _playCount }

    fun id(_id: Int) = apply { id = _id }
}