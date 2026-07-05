/******************************************************************************
 * Copyright (C) 2025  Octavio Calleya Garcia                                 *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 * (at your option) any later version.                                        *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.     *
 ******************************************************************************/

package net.transgressoft.commons.fx.music.waveform

import net.transgressoft.commons.music.waveform.AudioWaveform
import javafx.scene.canvas.Canvas
import javafx.scene.paint.Color
import mu.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * JavaFX canvas component for rendering audio waveforms.
 *
 * Automatically redraws the waveform when the canvas is resized, computing amplitude
 * data asynchronously to avoid blocking the JavaFX Application Thread. Cancels any
 * pending waveform computations when a new waveform is requested or when the size changes.
 */
class WaveformPane : Canvas() {

    private val logger = KotlinLogging.logger {}

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

    /**
     * Renders the given [waveform] onto this canvas asynchronously.
     *
     * Any in-progress rendering task is cancelled before the new one begins. The amplitude data
     * is computed off the JavaFX Application Thread and the canvas is painted once results arrive.
     * Resizing the canvas automatically re-triggers this method with the current waveform.
     *
     * @param waveform the waveform whose amplitude data will be rendered
     * @param waveformColor the color used to draw the waveform bars; defaults to the current setting
     * @param backgroundColor the color used to fill the canvas background; defaults to the current setting
     */
    fun drawWaveformAsync(waveform: AudioWaveform, waveformColor: Color = this.waveformColor, backgroundColor: Color = this.backgroundColor) {
        this.waveform = waveform
        this.waveformColor = waveformColor
        this.backgroundColor = backgroundColor

        amplitudesTask?.isActive?.takeIf { it }?.let {
            amplitudesTask?.cancel()
        }

        amplitudesTask =
            scope.launch {
                if (width > 0) {
                    val gc = graphicsContext2D
                    gc.fill = backgroundColor
                    gc.fillRect(0.0, 0.0, width, height)

                    try {
                        val w = width.toInt()
                        val h = height.toInt()
                        val amplitudes =
                            withContext(Dispatchers.Default) {
                                waveform.amplitudes(w, h)
                            }
                        drawWaveform(amplitudes, waveformColor)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logger.warn(e) { "Failed to compute waveform for $waveform" }
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

    /**
     * Cancels the coroutine scope, stopping any in-progress waveform rendering.
     *
     * Must be called when the component is no longer needed to release coroutine resources.
     */
    fun dispose() {
        job.cancel()
    }
}