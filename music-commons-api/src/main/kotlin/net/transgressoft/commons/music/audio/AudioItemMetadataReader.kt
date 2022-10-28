package net.transgressoft.commons.music.audio

interface AudioItemMetadataReader {

    fun readAudioItem(newId: Int): AudioItem
}
