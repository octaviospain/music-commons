package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.IdentifiableEntity
import net.transgressoft.commons.music.audio.AudioItem
import java.io.IOException
import java.nio.file.Path
import java.util.function.Predicate

interface AudioPlaylist<I : AudioItem> : IdentifiableEntity<Int>, Comparable<AudioPlaylist<I>> {

    val isDirectory: Boolean

    val name: String

    val audioItems: List<I>

    val audioItemsRecursive: List<I>

    val playlists: Set<AudioPlaylist<I>>

    fun audioItemsAllMatch(predicate: Predicate<AudioItem>): Boolean

    fun audioItemsAnyMatch(predicate: Predicate<AudioItem>): Boolean

    @Throws(IOException::class)
    fun exportToM3uFile(destinationPath: Path)
}