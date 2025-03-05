package net.transgressoft.commons.music.playlist

import io.kotest.core.TestConfiguration

internal object AudioPlaylistTestUtil : TestConfiguration() {

    fun AudioPlaylist<*>.asJsonKeyValue(): String {
        val audioItemsString =
            buildString {
                append("[")
                audioItems.forEachIndexed { index, it ->
                    append(it.id)
                    if (index < audioItems.size - 1) {
                        append(",")
                    }
                }
                append("],")
            }
        val playlistIds =
            buildString {
                append("[")
                playlists.forEachIndexed { index, it ->
                    append(it.id)
                    if (index < playlists.size - 1) {
                        append(",")
                    }
                }
                append("]")
            }
        return """
            "$id": {
                "id": $id,
                "isDirectory": $isDirectory,
                "name": "$name",
                "audioItemIds": $audioItemsString
                "playlistIds": $playlistIds
            }"""
    }

    fun Collection<AudioPlaylist<*>>.asJsonKeyValues(): String {
        return buildString {
            append("{")
            this@asJsonKeyValues.forEachIndexed { index, it ->
                append(it.asJsonKeyValue())
                if (index < this@asJsonKeyValues.size - 1) {
                    append(",")
                }
            }
            append("}")
        }
    }
}