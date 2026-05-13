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

package net.transgressoft.commons.media.player

import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.music.player.AudioItemPlayer.Status
import net.transgressoft.commons.music.player.UnsupportedAudioPlaybackException
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * Unit tests for [CoreAudioItemPlayer] that exercise the public API without triggering
 * real audio output. Complements [CoreAudioItemPlayerPlaybackTest], which requires a
 * working audio device and is gated behind the `requires-playback` tag.
 */
internal class CoreAudioItemPlayerUnitTest : StringSpec({

    fun audioItem(path: Path): ReactiveAudioItem<*> =
        mockk(relaxed = true) {
            every { this@mockk.path } returns path
            every { fileName } returns path.fileName.toString()
            every { extension } returns path.toString().substringAfterLast('.', "")
            every { encoding } returns null
            every { encoder } returns null
        }

    "initial status is UNKNOWN" {
        val player = CoreAudioItemPlayer()
        try {
            player.status() shouldBe Status.UNKNOWN
        } finally {
            player.dispose()
        }
    }

    "initial totalDuration is ZERO" {
        val player = CoreAudioItemPlayer()
        try {
            player.totalDuration shouldBe Duration.ZERO
        } finally {
            player.dispose()
        }
    }

    "initial getCurrentTime is ZERO" {
        val player = CoreAudioItemPlayer()
        try {
            player.getCurrentTime() shouldBe Duration.ZERO
        } finally {
            player.dispose()
        }
    }

    "pause is a no-op when nothing is playing" {
        val player = CoreAudioItemPlayer()
        try {
            shouldNotThrowAny { player.pause() }
            player.status() shouldBe Status.UNKNOWN
        } finally {
            player.dispose()
        }
    }

    "resume is a no-op when nothing is paused" {
        val player = CoreAudioItemPlayer()
        try {
            shouldNotThrowAny { player.resume() }
            player.status() shouldBe Status.UNKNOWN
        } finally {
            player.dispose()
        }
    }

    "stop is a no-op when nothing is playing" {
        val player = CoreAudioItemPlayer()
        try {
            player.stop()
            player.status() shouldBe Status.STOPPED
        } finally {
            player.dispose()
        }
    }

    "setVolume clamps to [0.0, 1.0]" {
        val player = CoreAudioItemPlayer()
        try {
            shouldNotThrowAny {
                player.setVolume(-1.0)
                player.setVolume(0.0)
                player.setVolume(0.5)
                player.setVolume(1.0)
                player.setVolume(2.0)
            }
        } finally {
            player.dispose()
        }
    }

    "seek before play stores the target without throwing" {
        val player = CoreAudioItemPlayer()
        try {
            shouldNotThrowAny { player.seek(Duration.ofMillis(500)) }
            player.status() shouldBe Status.UNKNOWN
        } finally {
            player.dispose()
        }
    }

    "onFinish setter replaces previously registered callback" {
        val player = CoreAudioItemPlayer()
        try {
            shouldNotThrowAny {
                player.onFinish { /* first */ }
                player.onFinish { /* second replaces first */ }
            }
        } finally {
            player.dispose()
        }
    }

    "dispose transitions to DISPOSED and is idempotent" {
        val player = CoreAudioItemPlayer()
        player.dispose()
        player.status() shouldBe Status.DISPOSED
        shouldNotThrowAny { player.dispose() }
        player.status() shouldBe Status.DISPOSED
    }

    "transport calls after dispose are silent no-ops" {
        val player = CoreAudioItemPlayer()
        player.dispose()

        // None of these may throw, and the status must remain DISPOSED.
        val missingFile = Files.createTempFile("disposed-noop", ".mp3").also { it.toFile().delete() }
        shouldNotThrowAny {
            player.play(audioItem(missingFile))
            player.pause()
            player.resume()
            player.stop()
            player.setVolume(0.5)
            player.seek(Duration.ofMillis(100))
            player.onFinish { /* unused */ }
        }
        player.status() shouldBe Status.DISPOSED
    }

    "play throws UnsupportedAudioPlaybackException when the file does not exist" {
        val player = CoreAudioItemPlayer()
        val missing = Files.createTempFile("missing-audio", ".mp3").also { it.toFile().delete() }
        try {
            shouldThrow<UnsupportedAudioPlaybackException> {
                player.play(audioItem(missing))
            }
        } finally {
            player.dispose()
        }
    }

    "play throws UnsupportedAudioPlaybackException when the path points to a directory" {
        val player = CoreAudioItemPlayer()
        val dir = Files.createTempDirectory("audio-directory-")
        try {
            shouldThrow<UnsupportedAudioPlaybackException> {
                player.play(audioItem(dir))
            }
        } finally {
            player.dispose()
            File(dir.toString()).delete()
        }
    }

    "play throws UnsupportedAudioPlaybackException for an unrecognised audio format" {
        val player = CoreAudioItemPlayer()
        // A regular text file: AudioSystem.getAudioFileFormat must reject it.
        val notAudio = Files.createTempFile("not-audio", ".txt")
        notAudio.toFile().writeText("definitely not audio bytes")
        try {
            shouldThrow<UnsupportedAudioPlaybackException> {
                player.play(audioItem(notAudio))
            }
        } finally {
            player.dispose()
            notAudio.toFile().delete()
        }
    }
})