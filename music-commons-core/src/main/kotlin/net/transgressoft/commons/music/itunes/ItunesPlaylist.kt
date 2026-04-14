package net.transgressoft.commons.music.itunes

/**
 * Immutable representation of a playlist parsed from an iTunes library XML file.
 *
 * Regular playlists contain [trackIds] referencing [ItunesTrack.id] values.
 * Folder playlists have [isFolder] set to `true` and child playlists reference them
 * via [parentPersistentId] matching the folder's [persistentId].
 */
data class ItunesPlaylist(
    val name: String,
    val persistentId: String,
    val parentPersistentId: String?,
    val isFolder: Boolean,
    val trackIds: List<Int>
)