package net.transgressoft.commons.music.player

import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.music.player.event.AudioItemPlayerEvent
import javafx.beans.property.DoubleProperty
import javafx.beans.property.ReadOnlyObjectProperty
import javafx.util.Duration
import java.util.concurrent.Flow

interface AudioItemPlayer : Flow.Publisher<AudioItemPlayerEvent> {
    enum class Status {
        UNKNOWN, READY, PAUSED, PLAYING, STOPPED, STALLED, HALTED, DISPOSED
    }

    val totalDuration: Duration
    val volumeProperty: DoubleProperty
    val statusProperty: ReadOnlyObjectProperty<Status>
    val currentTimeProperty: ReadOnlyObjectProperty<Duration>

    fun play(audioItem: ReactiveAudioItem<*>)
    fun pause()
    fun resume()
    fun stop()
    fun status(): Status
    fun setVolume(value: Double)
    fun seek(milliSeconds: Double)
    fun onFinish(value: Runnable)
}