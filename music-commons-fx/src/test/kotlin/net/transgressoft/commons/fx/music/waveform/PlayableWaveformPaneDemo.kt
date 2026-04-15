package net.transgressoft.commons.fx.music.waveform

import net.transgressoft.commons.fx.music.FXMusicLibrary
import net.transgressoft.commons.fx.music.audio.ObservableAudioItem
import net.transgressoft.commons.fx.music.player.JavaFxPlayer
import net.transgressoft.commons.music.waveform.ScalableAudioWaveform
import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.stage.Stage
import java.io.File

/**
 * Interactive demo application for [PlayableWaveformPane].
 *
 * Launches a JavaFX window showing the waveform component bound to a [JavaFxPlayer].
 * Demonstrates progress binding, seek-on-click, drag-to-scrub, playhead rendering,
 * and the shimmer loading animation. Use [runPlayableWaveformPaneDemo] Gradle task to run.
 */
class PlayableWaveformPaneDemo : Application() {

    private lateinit var playableWaveformPane: PlayableWaveformPane
    private val player = JavaFxPlayer()

    override fun start(primaryStage: Stage) {
        playableWaveformPane = PlayableWaveformPane()
        val borderPane = BorderPane(playableWaveformPane)
        borderPane.setPrefSize(500.0, 200.0)

        val flacUri = javaClass.getResource("/testfiles/testeable.flac")?.toURI()!!
        val mp3Uri = javaClass.getResource("/testfiles/testeable.mp3")?.toURI()!!

        player.currentTimeProperty.addListener { _, _, newTime ->
            val total = player.totalDuration.toMillis()
            if (total > 0) {
                playableWaveformPane.progressProperty.set(newTime.toMillis() / total)
            }
        }

        playableWaveformPane.addEventHandler(SeekEvent.SEEK) { event ->
            player.seek(event.seekRatio * player.totalDuration.toMillis())
        }

        val library = FXMusicLibrary.builder().build()
        val audioItem = library.audioItemFromFile(File(mp3Uri).toPath())

        borderPane.bottom = buildControls(audioItem)
        primaryStage.title = "PlayableWaveformPane Demo"
        primaryStage.scene = Scene(borderPane)
        primaryStage.show()

        playableWaveformPane.loadWaveform(ScalableAudioWaveform(1, File(flacUri).toPath()))
    }

    private fun buildControls(audioItem: ObservableAudioItem): HBox {
        val playButton = Button("Play").apply { setOnAction { player.play(audioItem) } }
        val pauseButton = Button("Pause").apply { setOnAction { player.pause() } }
        val resumeButton = Button("Resume").apply { setOnAction { player.resume() } }
        val stopButton = Button("Stop").apply { setOnAction { player.stop() } }
        return HBox(10.0, playButton, pauseButton, resumeButton, stopButton)
    }

    override fun stop() {
        player.dispose()
        playableWaveformPane.dispose()
        super.stop()
    }

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            launch(PlayableWaveformPaneDemo::class.java, *args)
        }
    }
}