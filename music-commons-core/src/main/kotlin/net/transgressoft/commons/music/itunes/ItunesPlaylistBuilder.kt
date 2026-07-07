package net.transgressoft.commons.music.itunes

import net.transgressoft.commons.music.MusicLibrary
import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.music.playlist.ReactiveAudioPlaylist
import net.transgressoft.commons.util.WindowsPathException
import net.transgressoft.commons.util.WindowsPathValidator
import net.transgressoft.commons.util.WindowsViolation
import mu.KotlinLogging

/**
 * Assembles iTunes playlists and folder hierarchies into a [MusicLibrary] during an import operation.
 *
 * Per-import mutable state ([createdNameByPersistentId] and [rejected]) is owned by [createPlaylists]
 * and threaded as parameters through private helpers — the builder itself carries no per-import instance
 * fields and is safe to reuse across multiple import calls.
 */
internal class ItunesPlaylistBuilder<I, P>(
    private val musicLibrary: MusicLibrary<I, P>
) where I : ReactiveAudioItem<I>, P : ReactiveAudioPlaylist<I, P> {

    private val logger = KotlinLogging.logger {}

    internal fun createPlaylists(
        selectedPlaylists: List<ItunesPlaylist>,
        trackIdToItem: Map<Int, ReactiveAudioItem<*>>,
        rootDirectoryName: String?
    ): List<RejectedPlaylistName> {
        val createdNameByPersistentId = mutableMapOf<String, String>()
        val rejected = mutableListOf<RejectedPlaylistName>()

        createFolderDirectories(selectedPlaylists, createdNameByPersistentId, rejected)
        createRegularPlaylists(selectedPlaylists, trackIdToItem, createdNameByPersistentId, rejected)
        wirePlaylistHierarchy(selectedPlaylists, createdNameByPersistentId, rootDirectoryName)

        return rejected
    }

    internal fun expandWithAncestors(
        selectedPlaylists: List<ItunesPlaylist>,
        itunesLibrary: ItunesLibrary
    ): List<ItunesPlaylist> {
        val byPersistentId = itunesLibrary.playlists.associateBy(ItunesPlaylist::persistentId)
        val expanded = LinkedHashSet<ItunesPlaylist>()
        for (playlist in selectedPlaylists) {
            var current: ItunesPlaylist? = playlist
            while (current != null && expanded.add(current)) {
                current = current.parentPersistentId?.let(byPersistentId::get)
            }
        }
        return expanded.toList()
    }

    private fun createFolderDirectories(
        selectedPlaylists: List<ItunesPlaylist>,
        createdNameByPersistentId: MutableMap<String, String>,
        rejected: MutableList<RejectedPlaylistName>
    ) {
        for (playlist in selectedPlaylists) {
            if (!playlist.isFolder) continue
            if (!acceptPlaylistName(playlist.name, rejected)) continue
            val uniqueName = resolveUniqueName(playlist.name)
            musicLibrary.createPlaylistDirectory(uniqueName)
            createdNameByPersistentId[playlist.persistentId] = uniqueName
            logger.trace { "Created playlist directory '$uniqueName'" }
        }
    }

    private fun createRegularPlaylists(
        selectedPlaylists: List<ItunesPlaylist>,
        trackIdToItem: Map<Int, ReactiveAudioItem<*>>,
        createdNameByPersistentId: MutableMap<String, String>,
        rejected: MutableList<RejectedPlaylistName>
    ) {
        for (playlist in selectedPlaylists) {
            if (playlist.isFolder) continue
            if (!acceptPlaylistName(playlist.name, rejected)) continue
            val audioItemIds = playlist.trackIds.mapNotNull { trackIdToItem[it]?.id }
            val uniqueName = resolveUniqueName(playlist.name)
            musicLibrary.createPlaylist(uniqueName, audioItemIds)
            createdNameByPersistentId[playlist.persistentId] = uniqueName
            logger.trace { "Created playlist '$uniqueName' with ${audioItemIds.size} items" }
        }
    }

    private fun acceptPlaylistName(name: String, rejected: MutableList<RejectedPlaylistName>): Boolean =
        try {
            WindowsPathValidator.validateName(name)
            true
        } catch (e: WindowsPathException) {
            rejected.add(RejectedPlaylistName(name, e.violation.toRejectionReason()))
            false
        }

    private fun wirePlaylistHierarchy(
        selectedPlaylists: List<ItunesPlaylist>,
        createdNameByPersistentId: Map<String, String>,
        rootDirectoryName: String?
    ) {
        val rootDirectoryExists =
            rootDirectoryName != null && musicLibrary.findPlaylistByName(rootDirectoryName).isPresent
        for (playlist in selectedPlaylists) {
            val childName = createdNameByPersistentId[playlist.persistentId] ?: continue
            val parentId = playlist.parentPersistentId
            val parentName =
                if (parentId == null) {
                    if (rootDirectoryExists) rootDirectoryName else continue
                } else {
                    createdNameByPersistentId[parentId] ?: continue
                }
            try {
                musicLibrary.playlistHierarchy().addPlaylistsToDirectory(setOf(childName), parentName)
                logger.trace { "Wired '$childName' to folder '$parentName'" }
            } catch (e: Exception) {
                logger.warn("Could not wire '$childName' to folder '$parentName': ${e.message}")
            }
        }
    }

    private fun resolveUniqueName(name: String): String {
        if (!musicLibrary.findPlaylistByName(name).isPresent) return name
        var suffix = 1
        while (musicLibrary.findPlaylistByName("${name}_$suffix").isPresent) {
            suffix++
        }
        return "${name}_$suffix"
    }

    private fun WindowsViolation.toRejectionReason(): RejectionReason =
        when (this) {
            is WindowsViolation.ForbiddenChar -> RejectionReason.ForbiddenChar(char)
            is WindowsViolation.ReservedName -> RejectionReason.ReservedName
            WindowsViolation.TrailingDotOrSpace -> RejectionReason.TrailingDotOrSpace
            // Today validateName(name) does not enforce MAX_PATH on a single segment, so this branch is
            // currently unreachable. Mapped exhaustively (rather than throwing) so any future tightening
            // of the validator surfaces as a typed rejection instead of a hard import failure.
            WindowsViolation.ExceedsMaxPath -> RejectionReason.ExceedsMaxPath
        }
}