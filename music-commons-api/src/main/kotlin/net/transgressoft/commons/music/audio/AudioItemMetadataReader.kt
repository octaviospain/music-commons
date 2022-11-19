package net.transgressoft.commons.music.audio

interface AudioItemMetadataReader {

    fun readAudioItem(id: Int): AudioItem
}
