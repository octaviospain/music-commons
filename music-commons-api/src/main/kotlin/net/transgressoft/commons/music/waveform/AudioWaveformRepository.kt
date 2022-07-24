package net.transgressoft.commons.music.waveform

import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.query.Repository

interface AudioWaveformRepository<W : AudioWaveform> : Repository<W> {

    @Throws(AudioWaveformProcessingException::class)
    fun create(audioItem: AudioItem, width: Short, height: Short): W
}

class AudioWaveformProcessingException : Exception {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}