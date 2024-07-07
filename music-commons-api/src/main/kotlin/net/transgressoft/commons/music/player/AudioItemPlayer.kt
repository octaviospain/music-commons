package net.transgressoft.commons.music.player

import net.transgressoft.commons.music.audio.ReactiveAudioItem

interface AudioItemPlayer {
    enum class Status {
        UNKNOWN, READY, PAUSED, PLAYING, STOPPED, STALLED, HALTED, DISPOSED
    }

    fun play(audioItem: ReactiveAudioItem<*>)
    fun pause()
    fun resume()
    fun stop()
    fun status(): Status
    fun setVolume(value: Double)
    fun seek(milliSeconds: Double)
    fun onFinish(value: Runnable)
}