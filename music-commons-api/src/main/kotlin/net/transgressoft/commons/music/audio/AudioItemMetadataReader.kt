package net.transgressoft.commons.music.audio

interface AudioItemMetadataReader<I: AudioItem> {

    fun readAudioItem(id: Int): I
}
