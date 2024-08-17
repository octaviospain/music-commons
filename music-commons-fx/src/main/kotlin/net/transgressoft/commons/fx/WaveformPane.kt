package net.transgressoft.commons.fx

import net.transgressoft.commons.music.waveform.AudioWaveform
import javafx.scene.canvas.Canvas
import javafx.scene.paint.Color
import kotlinx.coroutines.*
import kotlinx.coroutines.javafx.JavaFx

class WaveformPane : Canvas() {

    private var waveform: AudioWaveform? = null
    private var waveformColor: Color = Color.WHITE
    private var backgroundColor: Color = Color.BLACK

    private val job: Job = Job()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private var amplitudesTask: Job? = null

    init {
        widthProperty().addListener { _, _, newWidth ->
            run {
                waveform?.let {
                    if (newWidth.toDouble() > 0)
                        drawWaveformAsync(it, waveformColor, backgroundColor)
                }
            }
        }
        heightProperty().addListener { _, _, newHeight ->
            run {
                waveform?.let {
                    if (newHeight.toDouble() > 0) {
                        drawWaveformAsync(it, waveformColor, backgroundColor)
                    }
                }
            }
        }
    }

    fun drawWaveformAsync(waveform: AudioWaveform, waveformColor: Color = this.waveformColor, backgroundColor: Color = this.backgroundColor) {
        this.waveform = waveform
        this.waveformColor = waveformColor
        this.backgroundColor = backgroundColor

        amplitudesTask?.isActive?.takeIf { it }?.let {
            amplitudesTask?.cancel()
        }

        amplitudesTask = scope.launch {
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

    fun dispose() {
        job.cancel()
    }
}
