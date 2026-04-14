package net.transgressoft.commons.music.itunes

/**
 * Immutable snapshot of an iTunes library parsed from a `library.xml` plist file.
 *
 * Contains all tracks indexed by their iTunes track ID (for O(1) lookup during playlist import)
 * and all non-smart playlists including folder playlists for hierarchy reconstruction.
 * Consumers inspect [playlists] to select which ones to import, then pass this object
 * along with the selection to `ItunesImportService`.
 */
data class ItunesLibrary(
    val tracks: Map<Int, ItunesTrack>,
    val playlists: List<ItunesPlaylist>
)