package net.transgressoft.commons.fx.music.waveform

import net.transgressoft.commons.fx.music.FXMusicLibrary
import net.transgressoft.commons.fx.music.audio.ObservableAudioItem
import net.transgressoft.commons.fx.music.player.FXAudioItemPlayer
import net.transgressoft.commons.media.waveform.ScalableAudioWaveform
import net.transgressoft.commons.music.audio.AudioFileType
import net.transgressoft.commons.music.player.AudioItemPlayer
import javafx.application.Application
import javafx.collections.FXCollections
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.Slider
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.stage.FileChooser
import javafx.stage.Stage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration

/**
 * Interactive demo application for [PlayableWaveformPane].
 *
 * Provides a single Play/Pause toggle plus a Stop button (resets to start) so that
 * playback behavior matches user expectations across all supported audio fixtures.
 */
class PlayableWaveformPaneDemo : Application() {

    private lateinit var playableWaveformPane: PlayableWaveformPane
    private lateinit var library: FXMusicLibrary
    private val player = FXAudioItemPlayer()
    private lateinit var statusLabel: Label
    private lateinit var playPauseButton: Button
    private lateinit var volumeSlider: Slider

    private val formats = FXCollections.observableArrayList(TEST_FIXTURES)
    private val extractedFiles = mutableMapOf<String, Path>()
    private var currentAudioItem: ObservableAudioItem? = null

    override fun start(primaryStage: Stage) {
        playableWaveformPane = PlayableWaveformPane()
        val borderPane = BorderPane(playableWaveformPane)
        borderPane.setPrefSize(700.0, 300.0)

        statusLabel = Label("Select a fixture to load")

        val formatSelector =
            ComboBox(formats).apply {
                value = "testeable.flac"
                setOnAction { loadFixture(value) }
            }

        playPauseButton =
            Button("Play").apply {
                setOnAction { togglePlayPause() }
            }
        val stopButton =
            Button("Stop").apply {
                setOnAction { stopPlayback() }
            }

        player.statusProperty.addListener { _, _, status ->
            playPauseButton.text = if (status == AudioItemPlayer.Status.PLAYING) "Pause" else "Play"
        }

        volumeSlider =
            Slider(0.0, 1.0, 1.0).apply {
                prefWidth = 100.0
                valueProperty().addListener { _, _, newValue ->
                    player.setVolume(newValue.toDouble())
                }
            }

        val browseButton =
            Button("Browse...").apply {
                setOnAction {
                    try {
                        val chooser = FileChooser()
                        chooser.title = "Select Audio File"
                        val extList = AudioFileType.extensions.map { "*.$it" }
                        chooser.extensionFilters.add(
                            FileChooser.ExtensionFilter("Audio Files", *extList.toTypedArray())
                        )
                        val file = chooser.showOpenDialog(primaryStage) ?: return@setOnAction
                        stopPlayback()
                        currentAudioItem = library.audioItemFromFile(file.toPath())
                        playableWaveformPane.loadWaveform(ScalableAudioWaveform(1, file.toPath()))
                        statusLabel.text = "Loaded: ${file.name}"
                    } catch (e: Exception) {
                        statusLabel.text = "Error loading file: ${e.message}"
                        currentAudioItem = null
                        e.printStackTrace()
                    }
                }
            }

        val controls = HBox(10.0, Label("Fixture:"), formatSelector, browseButton, playPauseButton, stopButton, Label("Vol:"), volumeSlider, statusLabel)
        borderPane.bottom = controls

        player.currentTimeProperty.addListener { _, _, newTime ->
            val total = player.totalDuration.toMillis()
            if (total > 0) {
                playableWaveformPane.progressProperty.set(newTime.toMillis() / total)
            }
        }

        playableWaveformPane.addEventHandler(SeekEvent.SEEK) { event ->
            val seekMillis = (event.seekRatio * player.totalDuration.toMillis()).toLong()
            player.seek(Duration.ofMillis(seekMillis))
            playableWaveformPane.progressProperty.set(event.seekRatio)
        }

        library = FXMusicLibrary.builder().build()

        primaryStage.title = "PlayableWaveformPane Demo - All Fixtures"
        primaryStage.scene = Scene(borderPane)
        primaryStage.show()

        loadFixture("testeable.flac")
    }

    private fun togglePlayPause() {
        val item = currentAudioItem ?: return
        try {
            when (player.status()) {
                AudioItemPlayer.Status.PLAYING -> player.pause()
                AudioItemPlayer.Status.PAUSED -> player.resume()
                else -> player.play(item)
            }
        } catch (e: Exception) {
            statusLabel.text = "Cannot play ${item.fileName}: ${e.message}"
            e.printStackTrace()
        }
    }

    private fun stopPlayback() {
        player.stop()
        playableWaveformPane.progressProperty.set(0.0)
        playPauseButton.text = "Play"
    }

    private fun loadFixture(fixtureName: String) {
        try {
            val path = extractedFiles[fixtureName] ?: extractToTempFile(fixtureName)?.also { extractedFiles[fixtureName] = it }
            if (path == null) {
                statusLabel.text = "Test file not found: $fixtureName"
                return
            }
            // Reset playback state so the new fixture starts cleanly.
            stopPlayback()
            currentAudioItem = library.audioItemFromFile(path)
            playableWaveformPane.loadWaveform(ScalableAudioWaveform(1, path))
            statusLabel.text = "Loaded: $fixtureName"
        } catch (e: Exception) {
            statusLabel.text = "Error loading $fixtureName: ${e.message}"
            currentAudioItem = null
            e.printStackTrace()
        }
    }

    private fun extractToTempFile(fixtureName: String): Path? {
        val stream = javaClass.getResourceAsStream("/testfiles/$fixtureName") ?: return null
        return stream.use {
            val ext = fixtureName.substringAfterLast('.')
            Files.createTempFile("waveform-demo-", ".$ext").apply {
                toFile().deleteOnExit()
                Files.copy(it, this, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    override fun stop() {
        player.dispose()
        playableWaveformPane.dispose()
        library.close()
        super.stop()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            launch(PlayableWaveformPaneDemo::class.java, *args)
        }
    }
}