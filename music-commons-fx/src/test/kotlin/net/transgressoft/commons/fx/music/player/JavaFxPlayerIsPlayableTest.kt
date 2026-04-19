package net.transgressoft.commons.fx.music.player

import net.transgressoft.commons.music.audio.ReactiveAudioItem
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

/**
 * Pure-predicate tests for [JavaFxPlayer.isPlayable] that exercise the static companion method with
 * mocked [ReactiveAudioItem]s. These tests intentionally do not construct a [JavaFxPlayer] instance
 * or start the JavaFX toolkit, so they stay cross-platform — including on Windows, where loading
 * `javafx.scene.media.MediaPlayer` triggers the bundled gstreamer-lite.dll and crashes in headless
 * Monocle mode. The player's [JavaFxPlayer.companion]'s `playerStatusMap` is `by lazy`, ensuring
 * this spec does not transitively load `MediaPlayer` either.
 */
internal class JavaFxPlayerIsPlayableTest : StringSpec({

    "JavaFxPlayer.isPlayable returns true when encoding and encoder are both null" {
        val item =
            mockk<ReactiveAudioItem<*>> {
                every { extension } returns "mp3"
                every { encoding } returns null
                every { encoder } returns null
            }

        JavaFxPlayer.isPlayable(item) shouldBe true
    }

    "JavaFxPlayer.isPlayable returns true when encoding is null and encoder has a non-iTunes value" {
        val item =
            mockk<ReactiveAudioItem<*>> {
                every { extension } returns "mp3"
                every { encoding } returns null
                every { encoder } returns "LAME 3.100"
            }

        JavaFxPlayer.isPlayable(item) shouldBe true
    }

    "JavaFxPlayer.isPlayable returns true when encoder is null and encoding is not Apple Lossless" {
        val item =
            mockk<ReactiveAudioItem<*>> {
                every { extension } returns "mp3"
                every { encoding } returns "MPEG-1 Audio Layer 3"
                every { encoder } returns null
            }

        JavaFxPlayer.isPlayable(item) shouldBe true
    }

    "JavaFxPlayer.isPlayable returns false when encoding starts with Apple" {
        val item =
            mockk<ReactiveAudioItem<*>> {
                every { extension } returns "m4a"
                every { encoding } returns "Apple Lossless"
                every { encoder } returns null
            }

        JavaFxPlayer.isPlayable(item) shouldBe false
    }

    "JavaFxPlayer.isPlayable returns false when encoder starts with iTunes" {
        val item =
            mockk<ReactiveAudioItem<*>> {
                every { extension } returns "m4a"
                every { encoding } returns null
                every { encoder } returns "iTunes 12.9.0.164"
            }

        JavaFxPlayer.isPlayable(item) shouldBe false
    }
})