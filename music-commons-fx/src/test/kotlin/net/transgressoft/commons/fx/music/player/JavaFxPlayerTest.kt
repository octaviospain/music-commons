package net.transgressoft.commons.fx.music.player

import net.transgressoft.commons.music.audio.ReactiveAudioItem
import net.transgressoft.commons.music.player.UnsupportedAudioPlaybackException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.StringSpec
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.extension.ExtendWith
import org.testfx.api.FxToolkit
import org.testfx.framework.junit5.ApplicationExtension
import java.nio.file.Files

@ExtendWith(ApplicationExtension::class)
@Tags("linux-only")
internal class JavaFxPlayerTest : StringSpec({

    beforeSpec {
        FxToolkit.registerPrimaryStage()
    }

    "JavaFxPlayer.play throws UnsupportedAudioPlaybackException when Media source URI is invalid" {
        val player = JavaFxPlayer()
        try {
            val nonExistentFile =
                Files.createTempFile("corrupt-audio", ".mp3").also {
                    it.toFile().delete() // ensure file does not exist so JavaFX Media throws
                }
            val item =
                mockk<ReactiveAudioItem<*>> {
                    every { extension } returns "mp3"
                    every { encoding } returns null
                    every { encoder } returns null
                    every { fileName } returns "corrupt.mp3"
                    every { duration } returns java.time.Duration.ofSeconds(0)
                    every { path } returns nonExistentFile
                }

            shouldThrow<UnsupportedAudioPlaybackException> {
                player.play(item)
            }
        } finally {
            player.dispose()
        }
    }
})