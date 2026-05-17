package net.transgressoft.commons.music.audio

import net.transgressoft.commons.music.audio.virtualFiles
import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.lirp.persistence.VolatileRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.optional.shouldBeEmpty
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
internal class AudioLibraryBaseTest : StringSpec({

    val reactive = reactiveScope()
    val files = virtualFiles()
    lateinit var repository: VolatileRepository<Int, AudioItem>
    lateinit var audioLibrary: TestAudioLibrary

    beforeEach {
        repository = VolatileRepository("AudioLibraryBaseTest")
        audioLibrary = TestAudioLibrary(repository, files.metadataUtils)
    }

    "AudioLibraryBase adds audio item and syncs artist catalog" {
        val audioItem = audioLibrary.createFromFile(files.virtualAudioFile().next())

        reactive.advance()

        audioLibrary.getArtistCatalog(audioItem.artist) shouldBePresent { catalog ->
            catalog.artist shouldBe audioItem.artist
        }
        audioLibrary.findAlbumAudioItems(audioItem.artist, audioItem.album.name).shouldContainOnly(audioItem)
    }

    "AudioLibraryBase removes audio item and cleans up artist catalog" {
        val audioItem = audioLibrary.createFromFile(files.virtualAudioFile().next())
        reactive.advance()

        audioLibrary.remove(audioItem) shouldBe true
        reactive.advance()

        audioLibrary.getArtistCatalog(audioItem.artist).shouldBeEmpty()
        audioLibrary.findAlbumAudioItems(audioItem.artist, audioItem.album.name).isEmpty() shouldBe true
    }

    "AudioLibraryBase updates artist catalog on item mutation" {
        val audioItem = audioLibrary.createFromFile(files.virtualAudioFile().next())
        reactive.advance()

        audioItem.title = "New Title"
        reactive.advance()

        audioLibrary.contains { it.title == "New Title" } shouldBe true
        audioLibrary.size() shouldBe 1
    }

    "AudioLibraryBase close() stops event delivery" {
        val audioItem = audioLibrary.createFromFile(files.virtualAudioFile().next())
        reactive.advance()

        val artistBeforeClose = audioItem.artist
        audioLibrary.close()

        // Add another item directly to repository after close — catalog should not pick it up
        val virtualPath = files.virtualAudioFile().next()
        val newItem = MutableAudioItem(virtualPath, Int.MAX_VALUE - 1, files.metadataUtils)
        repository.add(newItem)
        reactive.advance()

        // The catalog for the new item's artist should not exist because subscription was cancelled
        audioLibrary.getArtistCatalog(newItem.artist).shouldBeEmpty()
        // The original item's catalog is still present from before close
        audioLibrary.getArtistCatalog(artistBeforeClose) shouldBePresent { it.artist shouldBe artistBeforeClose }
    }

    "AudioLibraryBase findAlbumAudioItems returns items by artist and album" {
        val theBeatles = ImmutableArtist.of("The Beatles")
        val abbeyRoad = ImmutableAlbum("Abbey Road", theBeatles)

        val file1 =
            files.virtualAudioFile {
                artist = theBeatles
                album = abbeyRoad
            }.next()
        val item1 = audioLibrary.createFromFile(file1)

        val file2 =
            files.virtualAudioFile {
                artist = theBeatles
                album = abbeyRoad
            }.next()
        val item2 = audioLibrary.createFromFile(file2)

        reactive.advance()

        val result = audioLibrary.findAlbumAudioItems(theBeatles, abbeyRoad.name)
        result.size shouldBe 2
        result.any { it.id == item1.id } shouldBe true
        result.any { it.id == item2.id } shouldBe true
    }
})