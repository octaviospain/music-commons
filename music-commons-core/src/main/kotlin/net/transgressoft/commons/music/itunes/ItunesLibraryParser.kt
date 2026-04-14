package net.transgressoft.commons.music.itunes

import com.dd.plist.NSArray
import com.dd.plist.NSDate
import com.dd.plist.NSDictionary
import com.dd.plist.NSNumber
import com.dd.plist.PropertyListParser
import mu.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneOffset

/**
 * Parses an iTunes `library.xml` plist file into an [ItunesLibrary] domain object.
 *
 * Uses [PropertyListParser] from the dd-plist library for XML plist parsing.
 * Smart playlists (those with a `Smart Info` key) and tracks without a `file://` location
 * are silently skipped during parsing.
 */
object ItunesLibraryParser {

    private val logger = KotlinLogging.logger {}

    /**
     * Parses the iTunes library XML file at the given path.
     *
     * @param xmlPath path to the `library.xml` iTunes export file
     * @return an [ItunesLibrary] containing all valid tracks and non-smart playlists
     * @throws IllegalArgumentException if the file does not exist
     */
    fun parse(xmlPath: Path): ItunesLibrary {
        require(Files.exists(xmlPath)) { "iTunes library file '${xmlPath.toAbsolutePath()}' does not exist" }
        val root = PropertyListParser.parse(xmlPath.toFile()) as NSDictionary
        val tracks = parseTracks(root.objectForKey("Tracks") as? NSDictionary ?: NSDictionary())
        val playlists = parsePlaylists(root.objectForKey("Playlists") as? NSArray ?: NSArray(0))
        logger.info { "Parsed iTunes library: ${tracks.size} tracks, ${playlists.size} playlists" }
        return ItunesLibrary(tracks, playlists)
    }

    private fun parseTracks(tracksDict: NSDictionary): Map<Int, ItunesTrack> =
        tracksDict.keys.mapNotNull { key ->
            val entry = tracksDict.objectForKey(key) as? NSDictionary ?: return@mapNotNull null
            val id = (entry.objectForKey("Track ID") as? NSNumber)?.intValue() ?: return@mapNotNull null
            val location =
                entry.objectForKey("Location")?.toString()
                    ?.takeIf { it.startsWith("file://") }
                    ?: return@mapNotNull null
            id to
                ItunesTrack(
                    id = id,
                    title = entry.objectForKey("Name")?.toString() ?: "",
                    artist = entry.objectForKey("Artist")?.toString() ?: "",
                    albumArtist = entry.objectForKey("Album Artist")?.toString() ?: "",
                    album = entry.objectForKey("Album")?.toString() ?: "",
                    genre = entry.objectForKey("Genre")?.toString(),
                    year = (entry.objectForKey("Year") as? NSNumber)?.intValue()?.toShort(),
                    trackNumber = (entry.objectForKey("Track Number") as? NSNumber)?.intValue()?.toShort(),
                    discNumber = (entry.objectForKey("Disc Number") as? NSNumber)?.intValue()?.toShort(),
                    totalTimeMs = (entry.objectForKey("Total Time") as? NSNumber)?.longValue() ?: 0L,
                    bitRate = (entry.objectForKey("Bit Rate") as? NSNumber)?.intValue() ?: 0,
                    playCount = (entry.objectForKey("Play Count") as? NSNumber)?.intValue()?.toShort() ?: 0,
                    rating = (entry.objectForKey("Rating") as? NSNumber)?.intValue() ?: 0,
                    bpm = (entry.objectForKey("BPM") as? NSNumber)?.floatValue(),
                    comments = entry.objectForKey("Comments")?.toString(),
                    location = location,
                    isCompilation = (entry.objectForKey("Compilation") as? NSNumber)?.boolValue() ?: false,
                    persistentId = entry.objectForKey("Persistent ID")?.toString(),
                    dateAdded =
                        (entry.objectForKey("Date Added") as? NSDate)
                            ?.date?.toInstant()?.atZone(ZoneOffset.UTC)?.toLocalDateTime()
                )
        }.toMap()

    private fun parsePlaylists(playlistsArray: NSArray): List<ItunesPlaylist> =
        playlistsArray.array.mapNotNull { item ->
            val dict = item as? NSDictionary ?: return@mapNotNull null
            if (dict.objectForKey("Smart Info") != null) return@mapNotNull null
            val persistentId = dict.objectForKey("Playlist Persistent ID")?.toString() ?: return@mapNotNull null
            val trackIds =
                (dict.objectForKey("Playlist Items") as? NSArray)
                    ?.array
                    ?.mapNotNull { trackItem ->
                        ((trackItem as? NSDictionary)?.objectForKey("Track ID") as? NSNumber)?.intValue()
                    } ?: emptyList()
            ItunesPlaylist(
                name = dict.objectForKey("Name")?.toString() ?: return@mapNotNull null,
                persistentId = persistentId,
                parentPersistentId = dict.objectForKey("Parent Persistent ID")?.toString(),
                isFolder = (dict.objectForKey("Folder") as? NSNumber)?.boolValue() ?: false,
                trackIds = trackIds
            )
        }
}