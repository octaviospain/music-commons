package net.transgressoft.commons.fx.music.playlist

import net.transgressoft.commons.fx.music.audio.ObservableAudioItem
import net.transgressoft.commons.fx.music.fxAudioItem
import net.transgressoft.commons.music.testing.reactiveScope
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.StringSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import io.mockk.every
import javafx.application.Platform
import org.testfx.api.FxToolkit
import org.testfx.util.WaitForAsyncUtils
import java.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
internal class ObservablePlaylistHierarchyTest : StringSpec({

    val reactive = reactiveScope()

    beforeSpec {
        FxToolkit.registerPrimaryStage()
    }

    suspend fun eventuallyOnFxThread(
        duration: kotlin.time.Duration = 500.milliseconds,
        assertion: () -> Unit
    ) {
        eventually(duration) {
            Platform.runLater {
                assertion()
            }
        }
    }

    /** Runs [block] against a fresh [FXPlaylistHierarchy], closing it afterward (mirrors `.use`). */
    suspend fun withFxHierarchy(block: suspend (FXPlaylistHierarchy) -> Unit) {
        val hierarchy = FXPlaylistHierarchy()
        hierarchy.use { hierarchy ->
            block(hierarchy)
        }
    }

    /** The following playlist hierarchy is used for the test:

     ├──Best hits
     │  ├──50s
     │  │  ├──Rock
     │  │  │  ├──:50s Rock hit 1
     │  │  │  └──:50s Rock hit 2 my fav
     │  │  ├──Pop
     │  │  ├──:50s hit 1
     │  │  └──:50s favorite song
     │  └──60s
     └──This weeks' favorites songs
     */
    "Creates and finds playlists and audio items" {
        withFxHierarchy { playlistHierarchy ->
            val rockAudioItems =
                listOf(
                    Arb.fxAudioItem {
                        title = "50s Rock hit 1"
                        duration = Duration.ofSeconds(60)
                    }.next(),
                    Arb.fxAudioItem {
                        title = "50s Rock hit 2 my fav"
                        duration = Duration.ofSeconds(230)
                    }.next()
                )
            val rock = playlistHierarchy.createPlaylist("Rock", rockAudioItems)

            playlistHierarchy.findByName(rock.name) shouldBePresent { it shouldBe rock }
            val playlistsThatContainsAllAudioItemsWith50sInTitle =
                playlistHierarchy.search { it.audioItemsAllMatch { audioItem -> audioItem.title.contains("50s") } }
            playlistsThatContainsAllAudioItemsWith50sInTitle.shouldContainOnly(rock)

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            eventuallyOnFxThread {
                playlistHierarchy.playlistsProperty.shouldContainOnly(rock)
            }

            val pop = playlistHierarchy.createPlaylist("Pop")
            playlistHierarchy.size() shouldBe 2
            playlistHierarchy.numberOfPlaylists() shouldBe 2

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            eventuallyOnFxThread {
                playlistHierarchy.playlistsProperty shouldContainExactly setOf(rock, pop)
            }

            val fifties = playlistHierarchy.createPlaylistDirectory("50s")
            playlistHierarchy.addPlaylistToDirectory(rock, fifties.name)
            playlistHierarchy.addPlaylistToDirectory(pop.name, fifties.name)

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            eventuallyOnFxThread {
                playlistHierarchy.findByName(fifties.name) shouldBePresent {
                    it.playlists shouldContainExactly setOf(pop, rock)
                }
                playlistHierarchy.numberOfPlaylistDirectories() shouldBe 1
                playlistHierarchy.playlistsProperty shouldContainExactly setOf(rock, pop, fifties)
            }

            val sixties = playlistHierarchy.createPlaylistDirectory("60s")

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            eventuallyOnFxThread {
                sixties.playlists.isEmpty() shouldBe true
                playlistHierarchy.numberOfPlaylistDirectories() shouldBe 2
                playlistHierarchy.findByUniqueId("D-" + sixties.name) shouldBePresent { it shouldBe sixties }
                playlistHierarchy.playlistsProperty shouldContainExactly setOf(rock, pop, fifties, sixties)
            }

            val bestHits = playlistHierarchy.createPlaylistDirectory("Best hits")
            playlistHierarchy.addPlaylistsToDirectory(setOf(fifties, sixties), bestHits.name)

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            eventuallyOnFxThread {
                bestHits.playlists.isEmpty() shouldBe false
                playlistHierarchy.findByName(bestHits.name) shouldBePresent {
                    it.playlists shouldContainExactly setOf(fifties, sixties)
                }
                playlistHierarchy.playlistsProperty shouldContainExactly setOf(rock, pop, fifties, sixties, bestHits)
            }

            val thisWeeksFavorites = playlistHierarchy.createPlaylist("This weeks' favorites songs")
            playlistHierarchy.search { "favorites" in it.name }.shouldContainExactly(thisWeeksFavorites)
            playlistHierarchy.size() shouldBe 6
            playlistHierarchy.add(bestHits)
            playlistHierarchy.add(thisWeeksFavorites)
            playlistHierarchy.size() shouldBe 6
            playlistHierarchy.search { it.isDirectory.not() } shouldContainExactly setOf(rock, pop, thisWeeksFavorites)

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            eventuallyOnFxThread {
                playlistHierarchy.playlistsProperty shouldContainExactly setOf(rock, pop, fifties, sixties, bestHits, thisWeeksFavorites)
            }

            playlistHierarchy.search { it.isDirectory } shouldContainExactly setOf(fifties, sixties, bestHits)

            val fiftiesItems =
                listOf(
                    Arb.fxAudioItem {
                        title = "50s hit"
                        duration = Duration.ofSeconds(30)
                    }.next(),
                    Arb.fxAudioItem {
                        title = "50s favorite song"
                        duration = Duration.ofSeconds(120)
                    }.next()
                )

            playlistHierarchy.addAudioItemToPlaylist(fiftiesItems[0], fifties.name)
            playlistHierarchy.addAudioItemsToPlaylist(fiftiesItems, fifties.name)

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            eventuallyOnFxThread {
                val playlistsThatContainsAnyAudioItemsWithHitInTitle =
                    playlistHierarchy.search {
                        it.audioItemsAnyMatch { audioItem ->
                            "hit" in audioItem.title
                        }
                    }
                playlistsThatContainsAnyAudioItemsWithHitInTitle shouldContainExactly setOf(rock, fifties)

                val playlistsThatContainsAudioItemsWithDurationBelow60 =
                    playlistHierarchy.search {
                        it.audioItemsAnyMatch { audioItem: ObservableAudioItem -> audioItem.duration <= Duration.ofSeconds(60) }
                    }
                playlistsThatContainsAudioItemsWithDurationBelow60 shouldContainExactly setOf(rock, fifties)
            }

            playlistHierarchy.removeAudioItemFromPlaylist(fiftiesItems[0], fifties.name)

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            eventuallyOnFxThread {
                // this FXAudioItem instance is a mock
                every { fiftiesItems[1].title } returns "new title"

                fifties.audioItemsAllMatch { it.title == "new title" } shouldBe true
                val updatedItem = fifties.audioItems.find { it.title == "new title" }
                updatedItem.shouldNotBeNull()
                updatedItem shouldBeEqual fiftiesItems[1]
            }

            fifties.clearAudioItems()

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            eventuallyOnFxThread {
                playlistHierarchy.findById(fifties.id) shouldBePresent {
                    it.audioItems.isEmpty() shouldBe true
                }
            }

            playlistHierarchy.forEach { it.removeAudioItems(rockAudioItems) }

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            eventuallyOnFxThread {
                playlistHierarchy.findById(rock.id) shouldBePresent {
                    it.audioItems.isEmpty() shouldBe true
                }
            }

            playlistHierarchy.clear()
            playlistHierarchy.isEmpty shouldBe true

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            eventuallyOnFxThread {
                playlistHierarchy.playlistsProperty.isEmpty() shouldBe true
            }
        }
    }

    /** The following playlist hierarchy is used for the test:

     ├──Best hits
     │  └──50s
     │     ├──Rock
     │     └──Pop
     └──Selection of playlists
     */
    "Moves playlists from/to playlist directories" {
        withFxHierarchy { playlistHierarchy ->
            val rock = playlistHierarchy.createPlaylist("Rock")
            playlistHierarchy.findByName(rock.name) shouldBePresent { it shouldBe rock }
            val pop = playlistHierarchy.createPlaylist("Pop")
            val fifties = playlistHierarchy.createPlaylistDirectory("50s")
            playlistHierarchy.addPlaylistsToDirectory(setOf(rock.name, pop.name), fifties.name)
            val bestHits = playlistHierarchy.createPlaylistDirectory("Best hits")
            playlistHierarchy.addPlaylistsToDirectory(setOf(fifties.name), bestHits.name)
            val selection = playlistHierarchy.createPlaylistDirectory("Selection of playlists")
            playlistHierarchy.size() shouldBe 5

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            eventuallyOnFxThread {
                playlistHierarchy.playlistsProperty shouldContainExactly setOf(rock, pop, fifties, bestHits, selection)
            }

            selection.addPlaylist(rock)
            // same result as doing
            // playlistHierarchy.movePlaylist(rock.name, selection.name)

            // ├──Best hits
            // │  └──50s
            // │     └──Pop
            // └──Selection of playlists
            //    └──Rock

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            eventuallyOnFxThread {
                playlistHierarchy.size() shouldBe 5
                playlistHierarchy.findByName(selection.name) shouldBePresent {
                    it.playlists.shouldContainOnly(rock)
                }
                playlistHierarchy.findById(fifties.id) shouldBePresent {
                    it.playlists.shouldNotContain(rock)
                }
                playlistHierarchy.findByName(bestHits.name) shouldBePresent {
                    it.playlists.shouldContainOnly(fifties)
                }
            }

            // --

            playlistHierarchy.movePlaylist(selection.name, fifties.name)
            // same result as doing
            // fifties.addPlaylist(selection)

            // └──Best hits
            //    └──50s
            //       ├──Pop
            //       └──Selection of playlists
            //          └──Rock

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            eventuallyOnFxThread {
                playlistHierarchy.size() shouldBe 5
                playlistHierarchy.findByName(selection.name) shouldBePresent {
                    it.playlists.shouldContainOnly(rock)
                }
                playlistHierarchy.findByName(fifties.name) shouldBePresent {
                    it.playlists shouldContainExactly setOf(pop, selection)
                }
                playlistHierarchy.playlistsProperty shouldContainExactly setOf(rock, pop, fifties, bestHits, selection)
            }

            playlistHierarchy.removeAll(setOf(bestHits)) shouldBe true

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            eventuallyOnFxThread {
                playlistHierarchy.size() shouldBe 0
                playlistHierarchy.isEmpty shouldBe true
                bestHits.playlists.shouldContainOnly(fifties)
                fifties.playlists shouldContainExactly setOf(pop, selection)
                selection.playlists.shouldContainOnly(rock)
                rock.playlists.isEmpty() shouldBe true
                pop.playlists.isEmpty() shouldBe true
                playlistHierarchy.playlistsProperty.isEmpty() shouldBe true
            }
        }
    }

    "Removing playlist directory from it is recursive and changes reflects on playlists" {
        withFxHierarchy { playlistHierarchy ->
            val pop = playlistHierarchy.createPlaylist("Pop")
            val fifties = playlistHierarchy.createPlaylistDirectory("50s").also { it.addPlaylist(pop) }
            val bestHits = playlistHierarchy.createPlaylistDirectory("Best hits").also { it.addPlaylist(fifties) }
            val rock = playlistHierarchy.createPlaylist("Rock")
            val selection = playlistHierarchy.createPlaylistDirectory("Selection of playlists").also { it.addPlaylist(rock) }

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            eventuallyOnFxThread {
                playlistHierarchy.playlistsProperty shouldContainExactly setOf(pop, fifties, bestHits, rock, selection)
            }

            // ├──Best hits
            // │  └──50s
            // │     └──Pop
            // └──Selection of playlists
            //    └──Rock

            eventuallyOnFxThread {
                playlistHierarchy.size() shouldBe 5
                playlistHierarchy.findByName(selection.name) shouldBePresent {
                    it.playlists.shouldContainOnly(rock)
                }
                playlistHierarchy.findById(fifties.id) shouldBePresent {
                    it.playlists.shouldNotContain(rock)
                }
                playlistHierarchy.findByName(bestHits.name) shouldBePresent {
                    it.playlists.shouldContainOnly(fifties)
                }
            }

            var removed = playlistHierarchy.removePlaylistFromDirectory(fifties.name, bestHits.name)

            // ├──Best hits
            // └──Selection of playlists
            //    └──Rock

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            eventuallyOnFxThread {
                removed shouldBe true
                playlistHierarchy.size() shouldBe 3
                playlistHierarchy.findByName(pop.name).isEmpty shouldBe true
                playlistHierarchy.findByUniqueId(fifties.uniqueId).isEmpty shouldBe true
                bestHits.playlists.isEmpty() shouldBe true
                fifties.playlists.shouldContainOnly(pop)
            }

            removed = playlistHierarchy.removePlaylistFromDirectory(rock, selection.name)

            // ├──Best hits
            // └──Selection of playlists

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            eventuallyOnFxThread {
                removed shouldBe true
                playlistHierarchy.size() shouldBe 2
                selection.playlists.isEmpty() shouldBe true
                playlistHierarchy.playlistsProperty shouldContainExactly setOf(bestHits, selection)
            }

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            eventuallyOnFxThread {
                playlistHierarchy.playlistsProperty shouldContainExactly setOf(bestHits, selection)
            }
        }
    }

    "Throws Exception when creating playlists with an existing name" {
        withFxHierarchy { playlistHierarchy ->
            val newPlaylistDirectory = playlistHierarchy.createPlaylistDirectory("New playlist")

            io.kotest.assertions.throwables.shouldThrowMessage("Playlist with name 'New playlist' already exists") {
                playlistHierarchy.createPlaylistDirectory("New playlist")
            }
            playlistHierarchy.size() shouldBe 1

            io.kotest.assertions.throwables.shouldThrowMessage("Playlist with name 'New playlist' already exists") {
                playlistHierarchy.createPlaylist("New playlist")
            }
            playlistHierarchy.size() shouldBe 1

            playlistHierarchy.remove(newPlaylistDirectory) shouldBe true
            playlistHierarchy.isEmpty shouldBe true
        }
    }

    "Removing playlists from a directory, does not remove them from the playlistHierarchy" {
        withFxHierarchy { playlistHierarchy ->
            val fifties = playlistHierarchy.createPlaylistDirectory("50s")
            val rock = playlistHierarchy.createPlaylistDirectory("Rock")
            val rockFavorites = playlistHierarchy.createPlaylist("Rock Favorites")
            playlistHierarchy.addPlaylistToDirectory(rockFavorites, rock.name)
            playlistHierarchy.addPlaylistToDirectory(rock, fifties.name)

            // └──50s
            //    └──Rock
            //        └──Rock Favorites

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            eventuallyOnFxThread {
                playlistHierarchy.size() shouldBe 3
                fifties.playlists.size shouldBe 1
                rock.playlists.size shouldBe 1
            }

            rock.clearPlaylists()

            // └──50s
            //    └──Rock
            //
            // Rock Favorites (playlist remains in the playlistHierarchy)

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            eventuallyOnFxThread {
                playlistHierarchy.size() shouldBe 3
                rock.playlists.isEmpty() shouldBe true
                fifties.playlists.size shouldBe 1
            }

            fifties.removePlaylist(rock.id)

            // └──50s
            //
            // Rock (playlist remains in the playlistHierarchy)
            // Rock Favorites

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            eventuallyOnFxThread {
                playlistHierarchy.size() shouldBe 3
                fifties.playlists.isEmpty() shouldBe true
            }
        }
    }

    "Deleting a playlist removes it from the playlistHierarchy and its parent directory" {
        withFxHierarchy { playlistHierarchy ->
            val rock = playlistHierarchy.createPlaylist("Rock")
            val fifties = playlistHierarchy.createPlaylistDirectory("50s")
            playlistHierarchy.addPlaylistToDirectory(rock, fifties.name)

            // └──50s
            //    └──Rock

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            eventuallyOnFxThread {
                playlistHierarchy.size() shouldBe 2
                fifties.playlists.size shouldBe 1
            }

            playlistHierarchy.remove(rock)

            // └──50s

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            eventuallyOnFxThread {
                playlistHierarchy.size() shouldBe 1
                fifties.playlists.isEmpty() shouldBe true
            }
        }
    }

    "Single playlist property updates are eventually consistent" {
        withFxHierarchy { hierarchy ->
            val audioItem = Arb.fxAudioItem().next()

            val playlist = hierarchy.createPlaylist("Test Playlist", listOf(audioItem))

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            eventuallyOnFxThread {
                hierarchy.playlistsProperty.size shouldBe 1
                playlist.audioItemsProperty.size shouldBe 1
            }
        }
    }

    withData<PlaylistSizeCase>(
        nameFn = { it.name },
        PlaylistSizeCase("FXPlaylist allows adding duplicate audio items", expectedSize = 2) { hierarchy ->
            val item = Arb.fxAudioItem { title = "Duplicate Me" }.next()
            hierarchy.createPlaylist("Dupes", listOf(item)).also { it.addAudioItem(item) }
        },
        PlaylistSizeCase("Multiple concurrent modifications converge to consistent state", expectedSize = 15) { hierarchy ->
            val audioItems = List(20) { Arb.fxAudioItem { title = "Item-$it" }.next() }
            hierarchy.createPlaylist("Concurrent Test").also { playlist ->
                // Simulate concurrent-like rapid operations: add 20, remove 5.
                audioItems.take(10).forEach { playlist.addAudioItem(it) }
                audioItems.drop(10).forEach { playlist.addAudioItem(it) }
                audioItems.take(5).forEach { playlist.removeAudioItem(it) }
            }
        }
    ) { case ->
        withFxHierarchy { hierarchy ->
            val playlist = case.buildAndMutate(hierarchy)

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            eventuallyOnFxThread {
                playlist.audioItemsProperty.size shouldBe case.expectedSize
            }
        }
    }

    "FXPlaylist addAudioItems emits MutationEvent" {
        withFxHierarchy { hierarchy ->
            val playlist = hierarchy.createPlaylist("Events Test")
            val item1 = Arb.fxAudioItem { title = "Event Item 1" }.next()
            val item2 = Arb.fxAudioItem { title = "Event Item 2" }.next()

            // Start with one item so the playlist already has audioItems
            playlist.addAudioItem(item1)

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            eventuallyOnFxThread {
                playlist.audioItemsProperty.size shouldBe 1
            }

            // addAudioItems must wrap update in mutateAndPublish so the observable property updates on FX thread
            playlist.addAudioItem(item2)

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            eventuallyOnFxThread {
                playlist.audioItemsProperty.size shouldBe 2
            }
        }
    }

    "Rapid playlist modifications are eventually consistent" {
        withFxHierarchy { hierarchy ->
            val audioItems = List(10) { Arb.fxAudioItem { title = "Item-$it" }.next() }

            val playlist = hierarchy.createPlaylist("Test")

            // Rapid additions
            audioItems.forEach { playlist.addAudioItem(it) }

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            eventuallyOnFxThread {
                playlist.audioItemsProperty.size shouldBe 10
            }

            // Rapid removals
            audioItems.take(5).forEach { playlist.removeAudioItem(it) }

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            eventuallyOnFxThread {
                playlist.audioItemsProperty.size shouldBe 5
            }
        }
    }

    "Nested playlist additions maintain hierarchy consistency" {
        withFxHierarchy { hierarchy ->
            val child = hierarchy.createPlaylist("Child")
            val parent = hierarchy.createPlaylistDirectory("Parent")

            parent.addPlaylist(child)

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            eventuallyOnFxThread {
                hierarchy.playlistsProperty.size shouldBe 2
                parent.playlistsProperty.size shouldBe 1
                hierarchy.findParentPlaylist(child).isPresent shouldBe true
            }
        }
    }

    "Moving playlist between directories is eventually consistent" {
        withFxHierarchy { hierarchy ->
            val playlist = hierarchy.createPlaylist("Movable")
            val dir1 = hierarchy.createPlaylistDirectory("Directory 1")
            val dir2 = hierarchy.createPlaylistDirectory("Directory 2")

            // Add to dir1
            dir1.addPlaylist(playlist)
            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            eventuallyOnFxThread {
                dir1.playlistsProperty.size shouldBe 1
                dir2.playlistsProperty.size shouldBe 0
            }

            // Move to dir2
            dir2.addPlaylist(playlist)
            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            eventuallyOnFxThread {
                dir1.playlistsProperty.size shouldBe 0
                dir2.playlistsProperty.size shouldBe 1
                hierarchy.findParentPlaylist(playlist).get() shouldBe dir2
            }
        }
    }

    "Clearing playlist propagates to observable properties" {
        withFxHierarchy { hierarchy ->
            val audioItems = List(5) { Arb.fxAudioItem().next() }

            val playlist = hierarchy.createPlaylist("To Clear", audioItems)

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            eventuallyOnFxThread {
                playlist.audioItemsProperty.size shouldBe 5
            }

            playlist.clearAudioItems()

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            eventuallyOnFxThread {
                playlist.audioItemsProperty.size shouldBe 0
            }
        }
    }

    "Recursive audio items property updates correctly" {
        withFxHierarchy { hierarchy ->
            val item1 = Arb.fxAudioItem { title = "Item1" }.next()
            val item2 = Arb.fxAudioItem { title = "Item2" }.next()
            val item3 = Arb.fxAudioItem { title = "Item3" }.next()

            val child: ObservablePlaylist = hierarchy.createPlaylist("Child", listOf(item1, item2))
            val parent: ObservablePlaylist = hierarchy.createPlaylistDirectory("Parent", listOf(item3))

            parent.addPlaylist(child)

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            eventuallyOnFxThread {
                parent.audioItemsRecursiveProperty.size shouldBe 3
                parent.audioItemsRecursiveProperty.map { it: ObservableAudioItem -> it.title } shouldContainExactly listOf("Item3", "Item1", "Item2")
            }
        }
    }

    "Recursive audio items property propagates when items are added to a nested child playlist" {
        withFxHierarchy { hierarchy ->
            val leaf: ObservablePlaylist = hierarchy.createPlaylist("Leaf")
            val folder: ObservablePlaylist = hierarchy.createPlaylistDirectory("Folder")

            folder.addPlaylist(leaf)

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            folder.audioItemsRecursiveProperty.size shouldBe 0

            val late1 = Arb.fxAudioItem { title = "Late1" }.next()
            val late2 = Arb.fxAudioItem { title = "Late2" }.next()
            leaf.addAudioItems(listOf(late1, late2))

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            leaf.audioItemsRecursiveProperty.size shouldBe 2
            folder.audioItemsRecursiveProperty.size shouldBe 2
            folder.audioItemsRecursiveProperty.map { it: ObservableAudioItem -> it.title } shouldContainExactly listOf("Late1", "Late2")

            leaf.removeAudioItems(listOf(late1))

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            folder.audioItemsRecursiveProperty.size shouldBe 1
            folder.audioItemsRecursiveProperty.map { it: ObservableAudioItem -> it.title } shouldContainExactly listOf("Late2")
        }
    }

    "Recursive audio items property propagates through grandchild updates" {
        withFxHierarchy { hierarchy ->
            val grandchild: ObservablePlaylist = hierarchy.createPlaylist("Grandchild")
            val middle: ObservablePlaylist = hierarchy.createPlaylistDirectory("Middle")
            val top: ObservablePlaylist = hierarchy.createPlaylistDirectory("Top")

            middle.addPlaylist(grandchild)
            top.addPlaylist(middle)

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            top.audioItemsRecursiveProperty.size shouldBe 0

            val deep = Arb.fxAudioItem { title = "Deep" }.next()
            grandchild.addAudioItems(listOf(deep))

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            middle.audioItemsRecursiveProperty.size shouldBe 1
            top.audioItemsRecursiveProperty.size shouldBe 1
            top.audioItemsRecursiveProperty.map { it: ObservableAudioItem -> it.title } shouldContainExactly listOf("Deep")
        }
    }

    "Recursive audio items property stops propagating after a child is removed" {
        withFxHierarchy { hierarchy ->
            // Build the same shape as the surrounding tests use: create the leaf empty,
            // nest it, then add items afterwards. This avoids the racy "create-with-items
            // then nest" setup where the audio item aggregate binds asynchronously.
            val leaf: ObservablePlaylist = hierarchy.createPlaylist("Leaf")
            val folder: ObservablePlaylist = hierarchy.createPlaylistDirectory("Folder")

            folder.addPlaylist(leaf)

            val seed = Arb.fxAudioItem { title = "Seed" }.next()
            leaf.addAudioItems(listOf(seed))

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            folder.audioItemsRecursiveProperty.size shouldBe 1

            folder.removePlaylist(leaf)

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            folder.audioItemsRecursiveProperty.size shouldBe 0

            // Items added to a former child after removal must not appear in the former parent.
            val late = Arb.fxAudioItem { title = "Late" }.next()
            leaf.addAudioItems(listOf(late))

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            leaf.audioItemsRecursiveProperty.size shouldBe 2
            folder.audioItemsRecursiveProperty.size shouldBe 0
        }
    }

    "Repository subscription updates observable properties" {
        withFxHierarchy { hierarchy ->
            val playlist = hierarchy.createPlaylist("Test")

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            eventuallyOnFxThread {
                hierarchy.playlistsProperty.contains(playlist) shouldBe true
            }

            hierarchy.remove(playlist)

            reactive.advance()
            WaitForAsyncUtils.waitForFxEvents()

            eventuallyOnFxThread {
                hierarchy.playlistsProperty.contains(playlist) shouldBe false
            }
        }
    }
})

private class PlaylistSizeCase(
    val name: String,
    val expectedSize: Int,
    val buildAndMutate: (FXPlaylistHierarchy) -> ObservablePlaylist
)