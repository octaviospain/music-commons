package net.transgressoft.commons.fx.music.waveform

import net.transgressoft.commons.fx.WaveformPane
import net.transgressoft.commons.music.waveform.ScalableAudioWaveform
import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.layout.AnchorPane
import javafx.scene.paint.Color
import javafx.stage.Stage
import java.io.File

class WaveformPaneDemo : Application() {

    private lateinit var waveformPane: WaveformPane

    override fun start(primaryStage: Stage) {
        waveformPane = WaveformPane()
        val anchorPane = AnchorPane(waveformPane)
        anchorPane.setPrefSize(500.0, 200.0)
        waveformPane.heightProperty().bind(anchorPane.heightProperty())
        waveformPane.widthProperty().bind(anchorPane.widthProperty())
        val uri = javaClass.getResource("/testfiles/testeable.flac")?.toURI()!!

        primaryStage.scene = Scene(anchorPane)
        primaryStage.show()
        waveformPane.drawWaveformAsync(ScalableAudioWaveform(1, File(uri).toPath()), Color.GREEN, Color.MAGENTA)
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