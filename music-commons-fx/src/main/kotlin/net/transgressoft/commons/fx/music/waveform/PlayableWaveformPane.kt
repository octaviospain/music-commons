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
import javafx.animation.AnimationTimer
import javafx.application.Platform
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.event.Event
import javafx.scene.Cursor
import javafx.scene.canvas.Canvas
import javafx.scene.layout.Region
import javafx.scene.paint.Color
import javafx.scene.paint.CycleMethod
import javafx.scene.paint.LinearGradient
import javafx.scene.paint.Stop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Playback-aware waveform visualization component for JavaFX.
 *
 * Renders audio amplitude data as a two-pass waveform split at the current playback
 * position: bars before [progressProperty] use [playedColorProperty] and bars after use
 * [waveformColorProperty]. A playhead vertical line is drawn at the split position when
 * progress > 0. While amplitude data is being computed asynchronously, a shimmer animation
 * sweeps left-to-right to indicate loading state.
 *
 * Mouse interaction supports click-to-seek and drag-to-scrub. Visual position updates
 * immediately on press and drag; a [SeekEvent] fires on mouse release with the final seek
 * ratio clamped to [0.0, 1.0]. [progressProperty] is not mutated during drag.
 *
 * Consumers bind [progressProperty] to their player's current time and register a
 * [SeekEvent.SEEK] handler to relay seek commands to the player.
 */
class PlayableWaveformPane : Region() {

    private val canvas = Canvas()

    val progressProperty = SimpleDoubleProperty(this, "progress", 0.0)

    val waveformColorProperty = SimpleObjectProperty(this, "waveformColor", Color.WHITE)

    val playedColorProperty = SimpleObjectProperty(this, "playedColor", Color.GREEN)

    val backgroundColorProperty: ObjectProperty<Color> = SimpleObjectProperty(this, "backgroundColor", Color.BLACK)

    private val job: Job = Job()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private var amplitudesTask: Job? = null
    private var cachedAmplitudes: FloatArray? = null
    private var waveform: AudioWaveform? = null

    private var isDragging = false
    private var visualProgress = 0.0

    internal companion object {
        const val SHIMMER_CYCLE_MS = 1200.0
    }

    private var shimmerTimer: AnimationTimer? = null
    private var shimmerStartTime = 0L

    init {
        children.add(canvas)
        canvas.widthProperty().bind(widthProperty())
        canvas.heightProperty().bind(heightProperty())

        progressProperty.addListener { _, _, newValue ->
            if (!isDragging) {
                cachedAmplitudes?.let { runOnFxThread { drawWaveform(it, newValue.toDouble()) } }
            }
        }
        waveformColorProperty.addListener { _, _, _ ->
            cachedAmplitudes?.let { runOnFxThread { drawWaveform(it, progressProperty.value) } }
        }
        playedColorProperty.addListener { _, _, _ ->
            cachedAmplitudes?.let { runOnFxThread { drawWaveform(it, progressProperty.value) } }
        }
        backgroundColorProperty.addListener { _, _, _ ->
            cachedAmplitudes?.let { runOnFxThread { drawWaveform(it, progressProperty.value) } }
        }

        widthProperty().addListener { _, _, newWidth ->
            if (newWidth.toDouble() > 0) {
                waveform?.let { loadWaveform(it) }
            }
        }
        heightProperty().addListener { _, _, newHeight ->
            if (newHeight.toDouble() > 0) {
                waveform?.let { loadWaveform(it) }
            }
        }

        canvas.setOnMousePressed { e ->
            cursor = Cursor.CROSSHAIR
            isDragging = true
            visualProgress = (e.x / width).coerceIn(0.0, 1.0)
            cachedAmplitudes?.let { drawWaveform(it, visualProgress) }
        }
        canvas.setOnMouseDragged { e ->
            if (isDragging) {
                visualProgress = (e.x / width).coerceIn(0.0, 1.0)
                cachedAmplitudes?.let { drawWaveform(it, visualProgress) }
            }
        }
        canvas.setOnMouseReleased { e ->
            cursor = Cursor.HAND
            isDragging = false
            val seekRatio = (e.x / width).coerceIn(0.0, 1.0)
            visualProgress = seekRatio
            cachedAmplitudes?.let { drawWaveform(it, seekRatio) }
            Event.fireEvent(this, SeekEvent(this, this, seekRatio))
        }
    }

    /**
     * Loads the given [waveform] asynchronously. A shimmer animation starts immediately;
     * it stops and the waveform is drawn when amplitude data arrives.
     *
     * Any previously pending amplitude computation is cancelled before the new one begins.
     */
    fun loadWaveform(waveform: AudioWaveform) {
        this.waveform = waveform
        cachedAmplitudes = null
        amplitudesTask?.cancel()
        startShimmer()

        amplitudesTask =
            scope.launch {
                try {
                    if (width > 0 && height > 0) {
                        val w = width.toInt()
                        val h = height.toInt()
                        val amplitudes = waveform.amplitudes(w, h)
                        withContext(Dispatchers.JavaFx) {
                            stopShimmer()
                            cachedAmplitudes = amplitudes
                            drawWaveform(amplitudes, progressProperty.value)
                        }
                    } else {
                        withContext(Dispatchers.JavaFx) { stopShimmer() }
                    }
                } catch (_: kotlinx.coroutines.CancellationException) {
                    throw kotlinx.coroutines.CancellationException()
                } catch (_: Exception) {
                    withContext(Dispatchers.JavaFx) { stopShimmer() }
                }
            }
    }

    private fun drawWaveform(amplitudes: FloatArray, progress: Double) {
        val gc = canvas.graphicsContext2D
        gc.fill = backgroundColorProperty.value
        gc.fillRect(0.0, 0.0, width, height)

        val splitX = (progress * width).toInt().coerceIn(0, amplitudes.size)

        gc.lineWidth = 1.0
        for (i in amplitudes.indices) {
            val value = amplitudes[i]
            val y1 = (height - 2 * value) / 2
            val y2 = y1 + 2 * value
            gc.stroke = if (i < splitX) playedColorProperty.value else waveformColorProperty.value
            gc.strokeLine(i.toDouble(), y1, i.toDouble(), y2)
        }

        if (progress > 0.0 && splitX > 0) {
            gc.stroke = playedColorProperty.value
            gc.lineWidth = 2.0
            gc.strokeLine(splitX.toDouble(), 0.0, splitX.toDouble(), height)
            gc.lineWidth = 1.0
        }
    }

    private fun runOnFxThread(action: () -> Unit) {
        if (Platform.isFxApplicationThread()) action() else Platform.runLater(action)
    }

    private fun startShimmer() {
        shimmerTimer?.stop()
        shimmerStartTime = 0L
        cursor = Cursor.DEFAULT
        shimmerTimer =
            object : AnimationTimer() {
                override fun handle(now: Long) {
                    if (shimmerStartTime == 0L) shimmerStartTime = now
                    val elapsed = (now - shimmerStartTime) / 1_000_000.0
                    val fraction = (elapsed % SHIMMER_CYCLE_MS) / SHIMMER_CYCLE_MS
                    drawShimmer(fraction)
                }
            }
        shimmerTimer?.start()
    }

    private fun stopShimmer() {
        shimmerTimer?.stop()
        shimmerTimer = null
        cursor = Cursor.HAND
    }

    private fun drawShimmer(elapsedFraction: Double) {
        val gc = canvas.graphicsContext2D
        val bandWidth = width * 0.15
        val bandCenter = elapsedFraction * (width + bandWidth) - bandWidth / 2
        val startStop = ((bandCenter - bandWidth / 2) / width).coerceIn(0.0, 1.0)
        val centerStop = (bandCenter / width).coerceIn(0.0, 1.0)
        val endStop = ((bandCenter + bandWidth / 2) / width).coerceIn(0.0, 1.0)

        val bg = backgroundColorProperty.value
        val highlight = bg.brighter().brighter()
        val gradient =
            LinearGradient(
                0.0, 0.0, width, 0.0, false, CycleMethod.NO_CYCLE,
                Stop(startStop, bg),
                Stop(centerStop, highlight),
                Stop(endStop, bg)
            )
        gc.fill = gradient
        gc.fillRect(0.0, 0.0, width, height)
    }

    /**
     * Cancels the shimmer animation and the coroutine scope. Must be called when the
     * component is no longer needed to release resources.
     */
    fun dispose() {
        stopShimmer()
        job.cancel()
    }
}