package net.transgressoft.commons.fx.music.player

import net.transgressoft.commons.event.TransEventPublisherBase
import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.music.player.AudioItemPlayer
import net.transgressoft.commons.music.player.event.AudioItemPlayerEvent
import net.transgressoft.commons.music.player.event.AudioItemPlayerEvent.*
import net.transgressoft.commons.music.player.event.AudioItemPlayerEvent.Type.*
import javafx.beans.property.*
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration

/**
 * Basic player that uses the native JavaFX [MediaPlayer]
 *
 * @author Octavio Calleya
 */
class JavaFxPlayer : TransEventPublisherBase<AudioItemPlayerEvent>("JavaFxPlayer"), AudioItemPlayer {

    companion object {
        private const val PLAY_COUNT_THRESHOLD_POLICY = 0.6

        private val SUPPORTED_AUDIO_TYPES = arrayOf("mp3", "m4a", "wav")

        private val playerStatusMap: Map<MediaPlayer.Status, AudioItemPlayer.Status> = buildMap {
            put(MediaPlayer.Status.UNKNOWN, AudioItemPlayer.Status.UNKNOWN)
            put(MediaPlayer.Status.READY, AudioItemPlayer.Status.READY)
            put(MediaPlayer.Status.PAUSED, AudioItemPlayer.Status.PAUSED)
            put(MediaPlayer.Status.PLAYING, AudioItemPlayer.Status.PLAYING)
            put(MediaPlayer.Status.STOPPED, AudioItemPlayer.Status.STOPPED)
            put(MediaPlayer.Status.STALLED, AudioItemPlayer.Status.STALLED)
            put(MediaPlayer.Status.HALTED, AudioItemPlayer.Status.HALTED)
            put(MediaPlayer.Status.DISPOSED, AudioItemPlayer.Status.DISPOSED)
        }

        fun isPlayable(audioItem: ReactiveAudioItem<*>): Boolean {
            return setOf(*SUPPORTED_AUDIO_TYPES).contains(audioItem.extension) &&
                    !(audioItem.encoding!!.startsWith("Apple") || audioItem.encoder!!.startsWith("iTunes"))
        }
    }

    init {
        activateEvents(PLAYED)
    }

    private val _statusProperty: ObjectProperty<AudioItemPlayer.Status> = SimpleObjectProperty(this, "player status", AudioItemPlayer.Status.UNKNOWN)
    private val _volumeProperty: DoubleProperty = SimpleDoubleProperty(this, "volume", 0.0)
    private val _currentTimeProperty: ObjectProperty<Duration> = SimpleObjectProperty(this, "current time", Duration.ZERO)

    private var mediaPlayer: MediaPlayer? = null

    override val totalDuration: Duration
        get() = mediaPlayer?.totalDuration ?: Duration.INDEFINITE

    override val volumeProperty: DoubleProperty = _volumeProperty

    override val statusProperty: ReadOnlyObjectProperty<AudioItemPlayer.Status> = _statusProperty

    override val currentTimeProperty: ReadOnlyObjectProperty<Duration> = _currentTimeProperty

    override fun play(audioItem: ReactiveAudioItem<*>) {
        require(isPlayable(audioItem)) { "Unsupported file format. Supported file types are " + SUPPORTED_AUDIO_TYPES.contentToString() }

        val file = audioItem.path.toFile()
        val audioItemDuration = audioItem.duration.toMillis()
        var playCountIncreased = false
        val media = Media(file.toURI().toString())

        mediaPlayer?.dispose()
        mediaPlayer?.volumeProperty()?.unbind()

        mediaPlayer = MediaPlayer(media)
        mediaPlayer!!.volumeProperty().bind(_volumeProperty)
        mediaPlayer!!.statusProperty()
            .addListener { _, _, newValue ->
                _statusProperty.set(
                    playerStatusMap[newValue]
                )
            }
        mediaPlayer!!.currentTimeProperty()
            .addListener { _, _, newValue ->
                _currentTimeProperty.set(newValue)
                if (isTimeToIncreasePlayCount(audioItemDuration, newValue, playCountIncreased)) {
                    putEventAction(Played(audioItem))
                    playCountIncreased = true
                }
            }
        mediaPlayer!!.play()
    }

    private fun isTimeToIncreasePlayCount(audioItemDuration: Long, currentTime: Duration, playCountIncreased: Boolean): Boolean {
        val threshold = audioItemDuration * PLAY_COUNT_THRESHOLD_POLICY
        return currentTime.toMillis() >= threshold && playCountIncreased.not()
    }

    override fun pause() {
        mediaPlayer?.pause()
    }

    override fun resume() {
        mediaPlayer?.let {
            if (it.status == MediaPlayer.Status.PAUSED) {
                it.play()
            }
        }
    }

    override fun stop() {
        mediaPlayer?.stop()
    }

    override fun status(): AudioItemPlayer.Status = mediaPlayer?.let { playerStatusMap[it.status] } ?: AudioItemPlayer.Status.UNKNOWN

    override fun setVolume(value: Double) {
        if (value < 0)
            _volumeProperty.set(0.0)
        else
            _volumeProperty.set(value)
    }

    override fun seek(milliSeconds: Double) {
        mediaPlayer?.seek(Duration.millis(milliSeconds))
    }

    override fun onFinish(value: Runnable) {
        mediaPlayer?.onEndOfMedia = value
    }
}
