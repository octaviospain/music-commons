package net.transgressoft.commons.music.itunes

import java.time.LocalDateTime

/**
 * Immutable representation of a single track parsed from an iTunes library XML file.
 *
 * All fields are extracted from the plist `Tracks` dictionary. Fields absent from the XML
 * default to empty/zero values. The [location] field stores the raw `file://` URI as found
 * in the XML; callers convert it to a [java.nio.file.Path] before import.
 */
data class ItunesTrack(
    val id: Int,
    val title: String,
    val artist: String,
    val albumArtist: String,
    val album: String,
    val genre: String?,
    val year: Short?,
    val trackNumber: Short?,
    val discNumber: Short?,
    val totalTimeMs: Long,
    val bitRate: Int,
    val playCount: Short,
    val rating: Int,
    val bpm: Float?,
    val comments: String?,
    val location: String,
    val isCompilation: Boolean,
    val persistentId: String?,
    val dateAdded: LocalDateTime?
)