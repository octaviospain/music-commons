/******************************************************************************
 * Copyright (C) 2026  Octavio Calleya Garcia                                 *
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

package net.transgressoft.commons.fx.music.player

import net.transgressoft.commons.media.player.CoreAudioItemPlayer
import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.music.player.AudioItemPlayer
import net.transgressoft.commons.music.player.UnsupportedAudioPlaybackException
import javafx.animation.AnimationTimer
import javafx.application.Platform
import javafx.beans.property.DoubleProperty
import javafx.beans.property.ReadOnlyObjectProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.util.Duration as FxDuration

/**
 * JavaFX wrapper around [CoreAudioItemPlayer] that exposes JavaFX observable properties
 * for volume, status, and current time on the JavaFX Application Thread.
 */
class FXAudioItemPlayer(
    private val corePlayer: CoreAudioItemPlayer = CoreAudioItemPlayer()
) : AudioItemPlayer by corePlayer {

    val volumeProperty: DoubleProperty
        field = SimpleDoubleProperty(this, "volume", 1.0)

    val statusProperty: ReadOnlyObjectProperty<AudioItemPlayer.Status>
        field = SimpleObjectProperty(this, "status", AudioItemPlayer.Status.UNKNOWN)

    val currentTimeProperty: ReadOnlyObjectProperty<FxDuration>
        field = SimpleObjectProperty(this, "currentTime", FxDuration.ZERO)

    @Volatile
    private var externalOnFinish: Runnable? = null

    private val progressTicker: AnimationTimer =
        object : AnimationTimer() {
            override fun handle(now: Long) {
                if (corePlayer.status() == AudioItemPlayer.Status.PLAYING) {
                    val ms = corePlayer.getCurrentTime().toMillis().toDouble()
                    currentTimeProperty.set(FxDuration.millis(ms))
                }
            }
        }

    init {
        volumeProperty.addListener { _, _, newValue ->
            corePlayer.setVolume(newValue.toDouble())
        }
        corePlayer.onFinish {
            syncStatus()
            externalOnFinish?.run()
        }
        progressTicker.start()
    }

    override fun onFinish(value: Runnable) {
        externalOnFinish = value
    }

    @Throws(UnsupportedAudioPlaybackException::class)
    override fun play(audioItem: ReactiveAudioItem<*>) {
        corePlayer.play(audioItem)
        syncStatus()
    }

    override fun pause() {
        corePlayer.pause()
        syncStatus()
    }

    override fun resume() {
        corePlayer.resume()
        syncStatus()
    }

    override fun stop() {
        corePlayer.stop()
        syncStatus()
    }

    override fun dispose() {
        progressTicker.stop()
        corePlayer.dispose()
    }

    override fun setVolume(value: Double) {
        Platform.runLater { volumeProperty.set(value) }
    }

    private fun syncStatus() {
        val currentStatus = corePlayer.status()
        val currentTime = corePlayer.getCurrentTime()
        Platform.runLater {
            statusProperty.set(currentStatus)
            currentTimeProperty.set(FxDuration.millis(currentTime.toMillis().toDouble()))
        }
    }
}