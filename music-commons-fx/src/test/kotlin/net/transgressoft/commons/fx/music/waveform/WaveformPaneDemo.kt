package net.transgressoft.commons.fx.music.waveform

import net.transgressoft.commons.media.waveform.ScalableAudioWaveform
import net.transgressoft.commons.music.audio.AudioFileType
import javafx.application.Application
import javafx.collections.FXCollections
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.HBox
import javafx.scene.paint.Color
import javafx.stage.FileChooser
import javafx.stage.Stage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Interactive demo application for [WaveformPane].
 *
 * Tests waveform generation across all supported audio formats and codec variants.
 * Resources are loaded from the classpath (typically the music-commons-test JAR) and
 * extracted to temp files so that AudioSystem can decode them.
 */
class WaveformPaneDemo : Application() {

    private lateinit var waveformPane: WaveformPane

    private val formats = FXCollections.observableArrayList(TEST_FIXTURES)
    private val extractedFiles = mutableMapOf<String, Path>()

    override fun start(primaryStage: Stage) {
        waveformPane = WaveformPane()
        val anchorPane = AnchorPane(waveformPane)
        anchorPane.setPrefSize(600.0, 250.0)
        waveformPane.heightProperty().bind(anchorPane.heightProperty().subtract(40))
        waveformPane.widthProperty().bind(anchorPane.widthProperty())

        val formatSelector =
            ComboBox(formats).apply {
                value = "testeable.flac"
                setOnAction { loadWaveformForFixture(value) }
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
                        waveformPane.drawWaveformAsync(
                            ScalableAudioWaveform(1, file.toPath()),
                            Color.GREEN,
                            Color.MAGENTA
                        )
                    } catch (e: Exception) {
                        println("Error loading file: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }

        val controls =
            HBox(10.0, formatSelector, browseButton).apply {
                AnchorPane.setBottomAnchor(this, 10.0)
                AnchorPane.setLeftAnchor(this, 10.0)
            }

        anchorPane.children.add(controls)

        primaryStage.title = "WaveformPane Demo - All Formats"
        primaryStage.scene = Scene(anchorPane)
        primaryStage.show()

        loadWaveformForFixture("testeable.flac")
    }

    private fun loadWaveformForFixture(fixtureName: String) {
        val path =
            extractToTempFile(fixtureName) ?: run {
                println("Test file not found: $fixtureName")
                return
            }
        waveformPane.drawWaveformAsync(
            ScalableAudioWaveform(1, path),
            Color.GREEN,
            Color.MAGENTA
        )
    }

    private fun extractToTempFile(fixtureName: String): Path? {
        extractedFiles[fixtureName]?.let { return it }
        val stream = javaClass.getResourceAsStream("/testfiles/$fixtureName") ?: return null
        return stream.use {
            val ext = fixtureName.substringAfterLast('.')
            Files.createTempFile("waveform-demo-", ".$ext").apply {
                toFile().deleteOnExit()
                Files.copy(it, this, StandardCopyOption.REPLACE_EXISTING)
                extractedFiles[fixtureName] = this
            }
        }
    }

    override fun stop() {
        waveformPane.dispose()
        super.stop()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            launch(WaveformPaneDemo::class.java, *args)
        }
    }
}