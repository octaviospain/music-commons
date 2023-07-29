package net.transgressoft.commons.music.audio

import net.transgressoft.commons.query.QueryEntity
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime

/**
 * @author Octavio Calleya
 */
interface AudioItem : QueryEntity {
    val path: Path
    val fileName: String
    val extension: String
    val title: String
    val duration: Duration
    val bitRate: Int
    val artist: Artist
    val artistsInvolved: Set<String>
    val album: Album
    val genre: Genre
    val comments: String?
    val trackNumber: Short?
    val discNumber: Short?
    val bpm: Float?
    val encoder: String?
    val encoding: String?
    val length: Long
    val dateOfCreation: LocalDateTime
    val lastDateModified: LocalDateTime

    fun toBuilder(): AudioItemBuilder<out AudioItem>

    fun update(change: AudioItemMetadataChange): AudioItem

    suspend fun writeMetadata()
}