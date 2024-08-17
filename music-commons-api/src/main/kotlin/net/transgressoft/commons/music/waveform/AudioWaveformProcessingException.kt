package net.transgressoft.commons.music.waveform

class AudioWaveformProcessingException : Exception {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}