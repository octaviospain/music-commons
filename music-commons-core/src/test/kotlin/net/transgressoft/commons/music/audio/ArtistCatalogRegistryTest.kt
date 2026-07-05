package net.transgressoft.commons.music.audio

import net.transgressoft.commons.music.audio.MutableAudioItemTestBridge.createAudioItem
import net.transgressoft.commons.music.testing.reactiveScope
import net.transgressoft.lirp.event.CrudEvent.Type.CREATE
import net.transgressoft.lirp.event.CrudEvent.Type.DELETE
import net.transgressoft.lirp.event.CrudEvent.Type.UPDATE
import net.transgressoft.lirp.event.StandardCrudEvent
import net.transgressoft.lirp.persistence.VolatileRepository
import com.neovisionaries.i18n.CountryCode
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.StringSpec
import io.kotest.datatest.withData
import io.kotest.engine.names.WithDataTestName
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.optional.shouldBeEmpty
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.next
import kotlin.time.Duration.Companion.seconds

internal class ArtistCatalogRegistryTest : StringSpec({

    val reactive = reactiveScope()
    val files = virtualFiles()

    lateinit var repository: VolatileRepository<Int, AudioItem>
    lateinit var registry: DefaultArtistCatalogRegistry<AudioItem>

    beforeEach {
        repository = VolatileRepository("ArtistCatalogRegistryTest")
        registry = DefaultArtistCatalogRegistry(repository)
    }

    afterEach {
        registry.close()
    }

    "DefaultArtistCatalogRegistry creates catalog when item is added to repository" {
        val moby = Artist.of("Moby", CountryCode.US)
        val expectedAlbum = AlbumDetails("Play", moby)
        val audioItem = files.catalogItem(moby, expectedAlbum, "Porcelain")

        repository.add(audioItem)
        reactive.advance()

        eventually(2.seconds) {
            // clean title + matching albumArtist keep artistsInvolved == {Moby}, so exactly one bucket exists
            registry.size() shouldBe 1
            // Catalog is keyed on the involved-artist name (country-code-free), so query by name
            registry.findFirst("Moby") shouldBePresent { artistCatalog ->
                artistCatalog.artist should { it.name shouldBe "Moby" }
                artistCatalog.size shouldBe 1
                artistCatalog.albumAudioItems(expectedAlbum).shouldContainOnly(audioItem)
            }
        }
    }

    "DefaultArtistCatalogRegistry removes catalog when last item is removed from repository" {
        val expectedArtist = Artist.of("Moby", CountryCode.US)
        val expectedAlbum = AlbumDetails("Play", expectedArtist)
        val audioItem = files.catalogItem(expectedArtist, expectedAlbum, "Porcelain")
        repository.add(audioItem)
        reactive.advance()
        eventually(2.seconds) { registry.findFirst("Moby").isPresent shouldBe true }

        repository.remove(audioItem)
        reactive.advance()

        eventually(2.seconds) {
            registry.isEmpty shouldBe true
            registry.findFirst("Moby").shouldBeEmpty()
        }
    }

    "DefaultArtistCatalogRegistry updates catalog when item artist changes via repository UPDATE" {
        val oldArtist = Artist.of("Moby", CountryCode.US)
        val newArtist = Artist.of("Bjork", CountryCode.IS)
        // album albumArtist intentionally matches the track artist so oldArtist is fully dropped
        // when both artist and album.albumArtist are changed to newArtist
        val oldAlbum = AlbumDetails("Play", oldArtist)
        // deterministic title free of separator tokens to prevent spurious involved-artist entries
        val audioItem = files.catalogItem(oldArtist, oldAlbum, "Natural Blues")
        repository.add(audioItem)
        reactive.advance()
        eventually(2.seconds) { registry.findFirst("Moby").isPresent shouldBe true }

        // Change both track artist and album artist so Moby leaves artistsInvolved entirely
        val mutableItem = audioItem as MutableAudioItem
        mutableItem.artist = newArtist
        mutableItem.album = AlbumDetails("Homogenic", newArtist)
        repository.emitAsync(StandardCrudEvent.Update(audioItem, audioItem))
        reactive.advance()

        eventually(2.seconds) {
            registry.findFirst("Bjork").isPresent shouldBe true
            registry.findFirst("Moby").isEmpty shouldBe true
        }
    }

    "DefaultArtistCatalogRegistry builds catalog from items already in repository at construction" {
        // Use real MutableAudioItem instances (via virtualAudioFile + createAudioItem) instead of
        // mocked AudioItem objects from Arb.albumAudioItems — mocks return null for artistsInvolved
        // since the interface declares it without a default body, causing an NPE in the multi-key projection.
        val expectedArtist = Artist.of("Pixies")
        val expectedAlbum = AlbumDetails("Doolittle", expectedArtist)
        val itemPaths =
            files.virtualAlbumAudioFiles(expectedArtist, expectedAlbum, size = 3..5).next()
        val items = itemPaths.mapIndexed { idx, path -> createAudioItem(path, idx + 1, files.metadataIO) }

        items.forEach { repository.add(it) }
        // Re-create registry after items are pre-loaded
        registry.close()
        registry = DefaultArtistCatalogRegistry(repository)
        reactive.advance()

        eventually(2.seconds) {
            // virtualAlbumAudioFiles uses random titles; size() may be > 1 if titles parse extra artists.
            // Assert only on the artist bucket we care about.
            registry.findFirst("Pixies") shouldBePresent { artistCatalog ->
                artistCatalog.artist should { it.name shouldBe "Pixies" }
                artistCatalog.size shouldBe items.size
                artistCatalog.albumAudioItems(expectedAlbum.name).shouldContainOnly(items)
            }
        }
    }

    "DefaultArtistCatalogRegistry finds artist album set" {
        // Use a deterministic title and matching albumArtist to ensure artistsInvolved == {Radiohead}
        val expectedArtist = Artist.of("Radiohead")
        val expectedAlbum = AlbumDetails("OK Computer", expectedArtist)
        val itemPaths =
            files.virtualAlbumAudioFiles(expectedArtist, expectedAlbum, size = 3..5).next()
        val audioItems = itemPaths.mapIndexed { idx, path -> createAudioItem(path, idx + 1, files.metadataIO) }

        audioItems.forEach { repository.add(it) }
        reactive.advance()

        eventually(2.seconds) {
            // Catalog key is Artist("Radiohead", UNDEFINED) derived from the name via getArtistsNamesInvolved
            registry.findFirst("Radiohead") shouldBePresent { artistCatalog ->
                artistCatalog.artist should { it.name shouldBe "Radiohead" }
                artistCatalog.albums.size shouldBe 1
                artistCatalog.albums.forEach { albumSet ->
                    albumSet.albumName shouldBe expectedAlbum.name
                    albumSet.tracks shouldContainExactly audioItems
                }
            }
        }
    }

    "DefaultArtistCatalogRegistry emits CREATE event when first item for artist is added" {
        val receivedEvents = registry.artistCatalogPublisher.collect(CREATE)

        val expectedArtist = Artist.of("Moby", CountryCode.US)
        val expectedAlbum = AlbumDetails("Play", expectedArtist)
        // deterministic title and matching albumArtist keep artistsInvolved == {Moby}, so exactly one bucket
        val audioItem = files.catalogItem(expectedArtist, expectedAlbum, "Porcelain")
        repository.add(audioItem)
        reactive.advance()

        eventually(2.seconds) {
            receivedEvents.size shouldBe 1
            receivedEvents[0].entities.size shouldBe 1
            receivedEvents[0].entities.values.first() should { catalog ->
                catalog.artist.name shouldBe "Moby"
                catalog.size shouldBe 1
                catalog.albumAudioItems(expectedAlbum.name).shouldContainOnly(audioItem)
            }
        }
    }

    "DefaultArtistCatalogRegistry emits UPDATE event when second item for same artist is added" {
        val expectedArtist = Artist.of("Radiohead", CountryCode.UK)
        val expectedAlbum = AlbumDetails("OK Computer", expectedArtist)
        // deterministic titles and matching albumArtist keep artistsInvolved == {Radiohead} for both items,
        // so the second add recomputes exactly one bucket and fires exactly one UPDATE
        val firstItem = files.catalogItem(expectedArtist, expectedAlbum, "Airbag")
        repository.add(firstItem)
        reactive.advance()
        eventually(2.seconds) { registry.findFirst("Radiohead").isPresent shouldBe true }

        val updateEvents = registry.artistCatalogPublisher.collect(UPDATE)

        val secondItem = files.catalogItem(expectedArtist, expectedAlbum, "Paranoid Android", track = 2)
        repository.add(secondItem)
        reactive.advance()

        eventually(2.seconds) {
            updateEvents.size shouldBe 1
            updateEvents[0].entities.values.first() should { catalog ->
                catalog.artist.name shouldBe "Radiohead"
                catalog.size shouldBe 2
                catalog.albumAudioItems(expectedAlbum.name).size shouldBe 2
            }
        }
    }

    "DefaultArtistCatalogRegistry does not emit UPDATE event when non-artist field changes" {
        // Within-bucket property changes (trackNumber, title, etc.) do not change the projection
        // key (involved artists), so they do not trigger bucket-change notifications and no catalog
        // UPDATE event is emitted. Consumers needing within-bucket mutation events subscribe to
        // individual item mutation events directly.
        //
        // Deterministic title and matching albumArtist ensure artistsInvolved == {Radiohead},
        // so a trackNumber change cannot cause a bucket re-key and no UPDATE fires.
        val expectedArtist = Artist.of("Radiohead")
        val expectedAlbum = AlbumDetails("OK Computer", expectedArtist)
        val ukRadiohead = Artist.of("Radiohead", CountryCode.UK)
        val item1 = files.catalogItem(ukRadiohead, expectedAlbum, "Karma Police")
        val item2 = files.catalogItem(ukRadiohead, expectedAlbum, "No Surprises", track = 2)
        repository.add(item1)
        repository.add(item2)
        reactive.advance()
        // Wait for catalog to be populated
        eventually(2.seconds) { registry.findFirst("Radiohead").isPresent shouldBe true }

        val updateEvents = registry.artistCatalogPublisher.collect(UPDATE)

        (item1 as MutableAudioItem).title = "Karma Police (alternate)"
        repository.emitAsync(StandardCrudEvent.Update(item1, item1))
        reactive.advance()

        // No UPDATE event emitted for within-bucket mutations that do not affect artist or sort order.
        // advance() drains the test dispatcher to idle, so any bucket-change notification would already
        // have been delivered — the list staying empty is a deterministic negative.
        updateEvents.shouldBeEmpty()
        // Both items still in the catalog
        registry.findFirst("Radiohead") shouldBePresent { catalog ->
            catalog.size shouldBe 2
        }
    }

    "DefaultArtistCatalogRegistry emits DELETE event when last item of artist is removed" {
        val expectedArtist = Artist.of("Bjork", CountryCode.IS)
        val expectedAlbum = AlbumDetails("Homogenic", expectedArtist)
        val audioItem = files.catalogItem(expectedArtist, expectedAlbum, "Joga")
        repository.add(audioItem)
        reactive.advance()
        eventually(2.seconds) { registry.findFirst("Bjork").isPresent shouldBe true }

        val deleteEvents = registry.artistCatalogPublisher.collect(DELETE)

        repository.remove(audioItem)
        reactive.advance()

        eventually(2.seconds) {
            deleteEvents.size shouldBe 1
            deleteEvents[0].entities.values.first().artist.name shouldBe "Bjork"
            registry.findFirst("Bjork").shouldBeEmpty()
        }
    }

    "DefaultArtistCatalogRegistry emits CREATE events for multiple artists added at once" {
        val receivedEvents = registry.artistCatalogPublisher.collect(CREATE)

        val artist1 = Artist.of("Pink Floyd", CountryCode.UK)
        val artist2 = Artist.of("Led Zeppelin", CountryCode.UK)
        val album1 = AlbumDetails("The Wall", artist1)
        val album2 = AlbumDetails("IV", artist2)

        val item1 = files.catalogItem(artist1, album1, "Comfortably Numb")
        val item2 = files.catalogItem(artist2, album2, "Black Dog")

        repository.add(item1)
        repository.add(item2)
        reactive.advance()

        eventually(2.seconds) {
            // both items have single-artist clean titles, so exactly the two artist buckets are created
            val allCatalogArtistNames = receivedEvents.flatMap { it.entities.values }.map { it.artist.name }
            allCatalogArtistNames.shouldContainOnly("Pink Floyd", "Led Zeppelin")
        }
    }

    // Multi-membership: a single track surfaces in a catalog bucket for every involved artist. Both
    // rows add one item and assert two distinct buckets form, one per involved artist name.
    data class MultiMembershipCase(
        val scenarioName: String,
        val buildItem: (VirtualFiles) -> AudioItem,
        val expectedBuckets: Pair<String, String>
    ) : WithDataTestName {
        override fun dataTestName() = scenarioName
    }

    withData(
        // The feat pattern requires a space after "feat" (not "feat."), matching the hasFeat regex,
        // so "Vera Hall" is parsed from the title into its own involved-artist bucket.
        MultiMembershipCase(
            "featured artist in title appears in both primary and featured catalogs",
            buildItem = { files ->
                val artistA = Artist.of("Moby")
                files.catalogItem(artistA, AlbumDetails("Play", artistA), "Natural Blues feat Vera Hall")
            },
            expectedBuckets = "Moby" to "Vera Hall"
        ),
        // album.albumArtist = Moby, track artist = Bjork → both must appear in their own buckets.
        MultiMembershipCase(
            "differing track artist and album artist appear in both catalogs",
            buildItem = { files -> files.catalogItem(Artist.of("Bjork"), AlbumDetails("Collaboration", Artist.of("Moby")), "Joint Track") },
            expectedBuckets = "Bjork" to "Moby"
        )
    ) { case ->
        repository.add(case.buildItem(files))
        reactive.advance()

        eventually(2.seconds) {
            registry.findFirst(case.expectedBuckets.first) shouldBePresent { it.size shouldBe 1 }
            registry.findFirst(case.expectedBuckets.second) shouldBePresent { it.size shouldBe 1 }
            // two distinct buckets total (one per involved artist)
            registry.size() shouldBe 2
        }
    }

    "removing item removes it from all involved-artist catalogs" {
        val trackArtist = Artist.of("Bjork")
        val albumArtist = Artist.of("Moby")
        val album = AlbumDetails("Collaboration", albumArtist)
        val audioItem = files.catalogItem(trackArtist, album, "Joint Track")

        repository.add(audioItem)
        reactive.advance()
        eventually(2.seconds) { registry.size() shouldBe 2 }

        repository.remove(audioItem)
        reactive.advance()

        eventually(2.seconds) {
            registry.isEmpty shouldBe true
            registry.findFirst("Bjork").shouldBeEmpty()
            registry.findFirst("Moby").shouldBeEmpty()
        }
    }
})