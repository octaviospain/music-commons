package net.transgressoft.commons.music.audio

import net.transgressoft.commons.ReactiveEntity
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime
import kotlinx.coroutines.Job

interface ReactiveAudioItem<I : ReactiveAudioItem<I>> : ReactiveEntity<Int, I> {
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
    val playCount: Short

    fun writeMetadata(): Job
}
