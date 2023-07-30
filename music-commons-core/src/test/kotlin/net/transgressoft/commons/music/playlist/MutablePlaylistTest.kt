package net.transgressoft.commons.music.playlist

import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.next
import net.transgressoft.commons.music.audio.AudioItem
import net.transgressoft.commons.music.audio.AudioItemTestUtil
import java.nio.file.Path
import java.time.Duration
import java.util.*
import kotlin.io.path.exists

/**
 * @author Octavio Calleya
 */
internal class MutablePlaylistTest : StringSpec({

    "Mutable audio playlist attributes and operations" {
        val playlist1 = MutablePlaylist(1, false, "Playlist1")

        playlist1.id shouldBe 1
        playlist1.isDirectory shouldBe false
        playlist1.name shouldBe "Playlist1"
        playlist1.uniqueId shouldBe "1-Playlist1"
        playlist1.audioItems.isEmpty() shouldBe true
        playlist1.toString() shouldBe "MutablePlaylist(id=1, isDirectory=false, name='Playlist1', audioItems=[], playlists=[])"

        playlist1.name = "Modified playlist1"

        playlist1.name shouldBe "Modified playlist1"
        playlist1.uniqueId shouldBe "1-Modified playlist1"

        val audioItems = Arb.list(AudioItemTestUtil.arbitraryAudioItem(), 4..4).next().also {
            playlist1.audioItems.addAll(it)
        }

        playlist1.audioItems.size shouldBe 4
        playlist1.audioItemsAllMatch { it.title == "Song title" } shouldBe false

        val customAudioItem = AudioItemTestUtil.arbitraryAudioItem(title = "Song title").next().also {
            playlist1.audioItems.add(it)
        }

        playlist1.audioItems.size shouldBe 5
        playlist1.audioItemsAnyMatch { it.title == "Song title"} shouldBe true
        playlist1.audioItems.removeAll(audioItems)
        playlist1.audioItems.size shouldBe 1
        playlist1.audioItemsAllMatch { it.title == "Song title" } shouldBe true

        val playlist2 = MutablePlaylist(1, false, "Modified playlist1", listOf(customAudioItem))
        playlist1 shouldBe playlist2
        playlist1 shouldBeEqualComparingTo playlist2

        playlist1.audioItems.clear()
        playlist1.audioItems.isEmpty() shouldBe true
    }

    "Mutable audio directory attributes and operations" {
        val directory1 = MutablePlaylist(1, true, "Directory1")

        directory1.isDirectory shouldBe true
        directory1.playlists.isEmpty() shouldBe true
        directory1.audioItems.isEmpty() shouldBe true
        directory1.toString() shouldBe "MutablePlaylist(id=1, isDirectory=true, name='Directory1', audioItems=[], playlists=[])"

        val audioItems = Arb.list(AudioItemTestUtil.arbitraryAudioItem(), 5..5).next()
        val p1 = MutablePlaylist(10, true, "p1", audioItems = audioItems)
        val p2 = MutablePlaylist(11, true, "p2")
        val d1 = MutablePlaylist(12, true, "d1", audioItems = listOf(AudioItemTestUtil.arbitraryAudioItem(title = "One").next()))

        directory1.playlists.addAll(listOf(p1, p2, d1))
        directory1.playlists.size shouldBe 3
        directory1.audioItems.isEmpty() shouldBe true
        directory1.audioItemsRecursive.size shouldBe 6
        directory1.playlists.contains(d1) shouldBe true

        d1.audioItems.clear()
        p1.audioItems.clear()
        directory1.audioItems.isEmpty() shouldBe true
        directory1.audioItemsRecursive.isEmpty() shouldBe true

        directory1.playlists.clear()

        val directory2 = MutablePlaylist(1, true, "Directory1")
        directory1 shouldBe directory2
    }

    "Export playlist to .m3u file" {
        val tempDirectory = tempdir("playlist-export-test").also { it.deleteOnExit() }.toPath()

        val numberOfAudioItems = 5
        val randomQueenPlaylist = randomQueenAudioItems(tempDirectory, size = numberOfAudioItems)
        val aNightAtTheOperaPlaylist = randomQueenAudioItems(tempDirectory, "A night at the opera", numberOfAudioItems)
        val aKindOfMagicPlaylist = randomQueenAudioItems(tempDirectory, "A kind of magic", numberOfAudioItems)
        val playlists = setOf(aNightAtTheOperaPlaylist, aKindOfMagicPlaylist)
        val playlist = ImmutablePlaylist(1, true, "Queen", randomQueenPlaylist.audioItems, playlists)

        playlist.audioItems.shouldContainExactly(randomQueenPlaylist.audioItems)
        playlist.playlists.shouldContainExactlyInAnyOrder(aKindOfMagicPlaylist, aNightAtTheOperaPlaylist)
        playlist.audioItemsRecursive.size shouldBe numberOfAudioItems * 3

        val randomQueenPlaylistPath = tempDirectory.resolve("${playlist.name}.m3u")
        playlist.exportToM3uFile(randomQueenPlaylistPath)

        tempDirectory.toFile().let {
            it.list()?.size shouldBe 2
            it.listFiles {_, name -> name.equals("${playlist.name}.m3u") }?.size shouldBe 1
            it.listFiles {_, name -> name.equals(playlist.name) }?.size shouldBe 1
        }

        val aNightAtTheOperaPlaylistPath = tempDirectory.resolve(playlist.name).resolve("${aNightAtTheOperaPlaylist.name}.m3u")
        val aKindOfMagicPlaylistPath = tempDirectory.resolve(playlist.name).resolve("${aKindOfMagicPlaylist.name}.m3u")
        aNightAtTheOperaPlaylistPath.exists() shouldBe true
        aKindOfMagicPlaylistPath.exists() shouldBe true

        assertPlaylistM3uFile(randomQueenPlaylistPath, playlist)
    }
})

internal fun randomQueenAudioItems(tempDirectory: Path, albumName: String = "", size: Int) =
    ImmutablePlaylist(-1, false, albumName, buildList {
        for (i in 0 until size) {
            val title = "Song $i - $albumName"
            val artistName = "Queen"
            val duration = Duration.ofSeconds((60 + i).toLong())
            add((AudioItemTestUtil.arbitraryAudioItem(
                title = title,
                artist = AudioItemTestUtil.arbitraryArtist(name = artistName).next(),
                path = tempDirectory.resolve("$title.mp3"), duration = duration
            ))
                .next())
        }
    })

internal fun assertPlaylistM3uFile(m3uFilePath: Path, playlist: AudioPlaylist<AudioItem>) {
    Scanner(m3uFilePath).use { scanner ->
        scanner.nextLine() shouldBe "#EXTM3U"

        if (playlist.isDirectory && playlist.playlists.isNotEmpty()) {
            playlist.playlists.forEach {
                val containedPlaylistPath = m3uFilePath.parent.resolve(playlist.name).resolve("${it.name}.m3u")
                scanner.nextLine() shouldBe m3uFilePath.parent.relativize(containedPlaylistPath).toString()
                assertPlaylistM3uFile(containedPlaylistPath, it)
            }
        }
        playlist.audioItems.forEach {
            scanner.nextLine() shouldBe "#EXTALB: ${it.album.name}"
            scanner.nextLine() shouldBe "#EXTART: ${it.artist.name}"
            scanner.nextLine() shouldBe "#EXTINF: ${it.duration.seconds},${it.title}"
            scanner.nextLine() shouldBe m3uFilePath.parent.relativize(it.path).toString()
        }
    }
}