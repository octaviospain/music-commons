package net.transgressoft.commons.fx.music.player

import net.transgressoft.commons.fx.music.audio.FXAudioItem
import net.transgressoft.commons.fx.music.audio.FXAudioItemTestUtil.arbitraryMp3File
import net.transgressoft.commons.fx.music.audio.ObservableAudioItemJsonRepository
import net.transgressoft.commons.music.player.AudioItemPlayer
import net.transgressoft.commons.music.player.AudioItemPlayer.Status.PAUSED
import net.transgressoft.commons.music.player.AudioItemPlayer.Status.PLAYING
import net.transgressoft.commons.music.player.AudioItemPlayer.Status.STOPPED
import net.transgressoft.commons.music.player.AudioItemPlayer.Status.UNKNOWN
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.arbitrary.next
import javafx.stage.Stage
import javafx.util.Duration
import org.awaitility.Awaitility.await
import org.awaitility.kotlin.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.testfx.framework.junit5.ApplicationExtension
import org.testfx.framework.junit5.Start
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

@ExtendWith(ApplicationExtension::class)
class JavaFxPlayerTest {

    lateinit var player: AudioItemPlayer
    lateinit var jsonFile: File
    lateinit var observableAudioItemRepository: ObservableAudioItemJsonRepository
    lateinit var audioItem: FXAudioItem

    @Start
    fun start(stage: Stage) {}

    @BeforeEach
    fun beforeEach() {
        player = JavaFxPlayer()
        jsonFile = Files.createTempFile("observableAudioItemRepository-test", ".json").toFile().apply { deleteOnExit() }
        observableAudioItemRepository = ObservableAudioItemJsonRepository("ObservableAudioItemRepo", jsonFile)
        audioItem = observableAudioItemRepository.createFromFile(arbitraryMp3File.next().toPath())
    }

    @Test
    fun `Playing an audio item increases the play count and serializes the repository`() {
        player.subscribe(observableAudioItemRepository.playerSubscriber)
        val audioItemLength = audioItem.duration.toMillis()
        val timeToIncreasePlayCount = (audioItemLength * 0.6).roundToLong()

        player.play(audioItem)

        await.atMost(timeToIncreasePlayCount.plus(500), TimeUnit.MILLISECONDS).untilAsserted {
            audioItem.playCount shouldBe 1
            observableAudioItemRepository.findFirst { it.playCount.toInt() == 1 } shouldBePresent { it shouldBe audioItem }
            jsonFile.readText() shouldContain "\"playCount\": 1"
        }
    }

    @Test
    fun `Playing an audio item modifies its observable properties`() {
        player.status() shouldBe UNKNOWN
        player.statusProperty.get() shouldBe UNKNOWN
        player.currentTimeProperty.get() shouldBe Duration.ZERO
        player.totalDuration shouldBe Duration.INDEFINITE
        player.volumeProperty.get() shouldBe 0.0

        player.play(audioItem)
        player.seek(500.0)
        player.setVolume(0.5)

        await().atMost(1, TimeUnit.SECONDS).untilAsserted {
            player.status() shouldBe PLAYING
            player.statusProperty.get() shouldBe PLAYING
            player.currentTimeProperty.get() shouldBeGreaterThan Duration.millis(500.0)
            player.totalDuration.toSeconds().roundToLong() shouldBe audioItem.duration.toSeconds()
            player.volumeProperty.get() shouldBe 0.5
        }

        player.pause()
        val pausedTime = player.currentTimeProperty.get()
        await().atMost(500, TimeUnit.MILLISECONDS).untilAsserted {
            player.status() shouldBe PAUSED
            player.statusProperty.get() shouldBe PAUSED
            player.currentTimeProperty.get() shouldBe pausedTime
        }

        player.stop()
        await().atMost(1, TimeUnit.SECONDS).untilAsserted {
            player.status() shouldBe STOPPED
            player.statusProperty.get() shouldBe STOPPED
            player.currentTimeProperty.get() shouldBe Duration.ZERO
        }
    }
}