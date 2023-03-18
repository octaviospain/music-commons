package net.transgressoft.commons.fx

import javafx.scene.canvas.Canvas
import javafx.scene.paint.Color
import kotlinx.coroutines.*
import kotlinx.coroutines.javafx.JavaFx
import net.transgressoft.commons.music.waveform.AudioWaveform

class WaveformPane(
    private var waveform: AudioWaveform,
    width: Double,
    height: Double,
    waveformColor: Color = Color.WHITE,
    backgroundColor: Color = Color.BLACK,
) : Canvas() {

    private var amplitudesTask: Job? = null

    init {
        this.width = width
        this.height = height
        drawWaveformAsync(waveform, backgroundColor, waveformColor)

        widthProperty().addListener { _, newWidth, newHeight ->
            run {
                if (newWidth.toDouble() > 0 && newHeight.toDouble() > 0)
                    drawWaveformAsync(waveform, waveformColor, backgroundColor)
            }
        }
        heightProperty().addListener { _, newWidth, newHeight ->
            run {
                if (newWidth.toDouble() > 0 && newHeight.toDouble() > 0) {
                    drawWaveformAsync(waveform, waveformColor, backgroundColor)
                }
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun drawWaveformAsync(waveform: AudioWaveform, waveformColor: Color, backgroundColor: Color) {
        this.waveform = waveform

        amplitudesTask?.isActive?.takeIf { it }?.let {
            amplitudesTask?.cancel()
        }

        amplitudesTask = GlobalScope.launch {
            if (width > 0) {
                val gc = graphicsContext2D
                gc.fill = backgroundColor
                gc.fillRect(0.0, 0.0, width, height)

                val amplitudes = waveform.amplitudes(width.toInt(), height.toInt())
                withContext(Dispatchers.JavaFx) {
                    drawWaveform(amplitudes, waveformColor)
                }
            }
        }
    }

    private fun drawWaveform(amplitudes: FloatArray, waveformColor: Color) {
        val gc = graphicsContext2D
        for (i in amplitudes.indices) {
            val value = amplitudes[i]
            val y1 = (height - 2 * value) / 2
            val y2 = y1 + 2 * value
            gc.stroke = waveformColor
            gc.strokeLine(i.toDouble(), y1, i.toDouble(), y2)
        }
    }
}