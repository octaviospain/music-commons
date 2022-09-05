package net.transgressoft.commons.music.playlist

import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.query.BooleanQueryTerm
import net.transgressoft.commons.query.QueryEntity
import java.io.IOException
import java.nio.file.Path

interface AudioPlaylist<I : AudioItem> : QueryEntity, Comparable<AudioPlaylist<I>> {

    val isDirectory: Boolean

    val name: String

    fun audioItems(): List<I>

    fun audioItemsAllMatch(queryPredicate: BooleanQueryTerm<AudioItem>): Boolean

    fun audioItemsAnyMatch(queryPredicate: BooleanQueryTerm<AudioItem>): Boolean

    @Throws(IOException::class)
    fun exportToM3uFile(destinationPath: Path)
}