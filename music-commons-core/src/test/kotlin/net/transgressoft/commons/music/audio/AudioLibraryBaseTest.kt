package net.transgressoft.commons.music.audio

import net.transgressoft.commons.music.audio.VirtualFiles.virtualAudioFile
import net.transgressoft.lirp.event.ReactiveScope
import net.transgressoft.lirp.persistence.VolatileRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.optional.shouldBeEmpty
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@ExperimentalCoroutinesApi
internal class AudioLibraryBaseTest : StringSpec({

    val testDispatcher = UnconfinedTestDispatcher()
    val testScope = CoroutineScope(testDispatcher)
    lateinit var repository: VolatileRepository<Int, AudioItem>
    lateinit var audioLibrary: TestAudioLibrary

    beforeSpec {
        ReactiveScope.flowScope = testScope
        ReactiveScope.ioScope = testScope
    }

    beforeEach {
        repository = VolatileRepository("AudioLibraryBaseTest")
        audioLibrary = TestAudioLibrary(repository)
    }

    afterSpec {
        ReactiveScope.resetDefaultFlowScope()
        ReactiveScope.resetDefaultIoScope()
        unmockkAll()
    }

    "AudioLibraryBase adds audio item and syncs artist catalog" {
        val audioItem = audioLibrary.createFromFile(Arb.virtualAudioFile().next())

        testDispatcher.scheduler.advanceUntilIdle()

        audioLibrary.getArtistCatalog(audioItem.artist) shouldBePresent { catalog ->
            catalog.artist shouldBe audioItem.artist
        }
        audioLibrary.findAlbumAudioItems(audioItem.artist, audioItem.album.name).shouldContainOnly(audioItem)
    }

    "AudioLibraryBase removes audio item and cleans up artist catalog" {
        val audioItem = audioLibrary.createFromFile(Arb.virtualAudioFile().next())
        testDispatcher.scheduler.advanceUntilIdle()

        audioLibrary.remove(audioItem) shouldBe true
        testDispatcher.scheduler.advanceUntilIdle()

        audioLibrary.getArtistCatalog(audioItem.artist).shouldBeEmpty()
        audioLibrary.findAlbumAudioItems(audioItem.artist, audioItem.album.name).isEmpty() shouldBe true
    }

    "AudioLibraryBase updates artist catalog on item mutation" {
        val audioItem = audioLibrary.createFromFile(Arb.virtualAudioFile().next())
        testDispatcher.scheduler.advanceUntilIdle()

        audioItem.title = "New Title"
        testDispatcher.scheduler.advanceUntilIdle()

        audioLibrary.contains { it.title == "New Title" } shouldBe true
        audioLibrary.size() shouldBe 1
    }

    "AudioLibraryBase close() stops event delivery" {
        val audioItem = audioLibrary.createFromFile(Arb.virtualAudioFile().next())
        testDispatcher.scheduler.advanceUntilIdle()

        val artistBeforeClose = audioItem.artist
        audioLibrary.close()

        // Add another item directly to repository after close — catalog should not pick it up
        val virtualPath = Arb.virtualAudioFile().next()
        val newItem = MutableAudioItem(virtualPath, Int.MAX_VALUE - 1)
        repository.add(newItem)
        testDispatcher.scheduler.advanceUntilIdle()

        // The catalog for the new item's artist should not exist because subscription was cancelled
        audioLibrary.getArtistCatalog(newItem.artist).shouldBeEmpty()
        // The original item's catalog is still present from before close
        audioLibrary.getArtistCatalog(artistBeforeClose) shouldBePresent { it.artist shouldBe artistBeforeClose }
    }

    "AudioLibraryBase findAlbumAudioItems returns items by artist and album" {
        val theBeatles = ImmutableArtist.of("The Beatles")
        val abbeyRoad = ImmutableAlbum("Abbey Road", theBeatles)

        val file1 =
            Arb.virtualAudioFile {
                artist = theBeatles
                album = abbeyRoad
            }.next()
        val item1 = audioLibrary.createFromFile(file1)

        val file2 =
            Arb.virtualAudioFile {
                artist = theBeatles
                album = abbeyRoad
            }.next()
        val item2 = audioLibrary.createFromFile(file2)

        testDispatcher.scheduler.advanceUntilIdle()

        val result = audioLibrary.findAlbumAudioItems(theBeatles, abbeyRoad.name)
        result.size shouldBe 2
        result.any { it.id == item1.id } shouldBe true
        result.any { it.id == item2.id } shouldBe true
    }
})