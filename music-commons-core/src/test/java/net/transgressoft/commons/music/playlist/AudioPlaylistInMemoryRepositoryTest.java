package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.MusicLibraryTestBase;
import net.transgressoft.commons.music.audio.AudioItem;
import net.transgressoft.commons.query.QueryPredicate;
import net.transgressoft.commons.query.RepositoryException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.google.common.truth.Truth.assertThat;
import static net.transgressoft.commons.music.audio.DurationAudioItemAttribute.DURATION;
import static net.transgressoft.commons.music.audio.StringAudioItemAttribute.TITLE;
import static net.transgressoft.commons.music.playlist.PlaylistNodeAttribute.SELF;
import static net.transgressoft.commons.music.playlist.PlaylistStringAttribute.NAME;
import static net.transgressoft.commons.music.playlist.PlaylistStringAttribute.UNIQUE_ID;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AudioPlaylistInMemoryRepositoryTest extends MusicLibraryTestBase {

    AudioPlaylistRepository<AudioItem, AudioPlaylist<AudioItem>, AudioPlaylistDirectory<AudioItem>> audioPlaylistRepository;

    // ├──Best hits
    // │  ├──50s
    // │  │  ├──Rock
    // │  │  │  ├──:50s Rock hit 1
    // │  │  │  └──:50s Rock hit 2 my fav
    // │  │  ├──Pop
    // │  │  ├──:50s hit 1
    // │  │  └──:50s favorite song
    // │  └──60s favorites
    // └──This weeks' favorites songs
    @Test
    @DisplayName("Mixed playlists hierarchy structure and audio items search")
    void searchOperationsTest() throws Exception {
        audioPlaylistRepository = new AudioPlaylistInMemoryRepository();

        var rockAudioItems = List.of(
                createTestAudioItem("50s Rock hit 1", Duration.ofSeconds(60)),
                createTestAudioItem("50s Rock hit 2 my fav", Duration.ofSeconds(230)));
        var rock = audioPlaylistRepository.createPlaylist("Rock", rockAudioItems);
        assertThat(audioPlaylistRepository.findSinglePlaylistByName(rock.getName()).orElseThrow()).isEqualTo(rock);
        var playlistsThatContainsAllAudioItemsWith50sInTitle =
                audioPlaylistRepository.search(QueryPredicate.of(SELF.audioItemsAllMatch(TITLE.contains("50s"))));
        assertThat(playlistsThatContainsAllAudioItemsWith50sInTitle).containsExactly(rock);

        var pop = audioPlaylistRepository.createPlaylist("Pop");
        assertThat(audioPlaylistRepository.size()).isEqualTo(2);
        assertThat(audioPlaylistRepository.numberOfPlaylists()).isEqualTo(2);

        var fifties = audioPlaylistRepository.createPlaylistDirectory("50s");
        audioPlaylistRepository.addPlaylistsToDirectory(Set.of(pop, rock), fifties);
        assertThat(audioPlaylistRepository.findSingleDirectoryByName(fifties.getName()).orElseThrow().descendantPlaylists())
                .containsExactly(pop, rock);
        assertThat(audioPlaylistRepository.numberOfPlaylistDirectories()).isEqualTo(1);

        var sixties = audioPlaylistRepository.createPlaylistDirectory("60s");
        assertThat(sixties.descendantPlaylists()).isEmpty();
        assertThat(audioPlaylistRepository.numberOfPlaylistDirectories()).isEqualTo(2);
        assertThat(audioPlaylistRepository.findByUniqueId(sixties.getId() + "-D-" + sixties.getName())).isEqualTo(Optional.of(sixties));

        var bestHits = audioPlaylistRepository.createPlaylistDirectory("Best hits");
        audioPlaylistRepository.addPlaylistsToDirectory(Set.of(fifties, sixties), bestHits);
        assertThat(bestHits.descendantPlaylists()).isEmpty(); // Fails because bestHits is an old immutable copy
        assertThat(audioPlaylistRepository.findSingleDirectoryByName(bestHits.getName()).orElseThrow().descendantPlaylists())
                .containsExactly(fifties, sixties); // bestHits is an updated immutable copy

        var thisWeeksFavorites = audioPlaylistRepository.createPlaylist("This weeks' favorites songs");
        assertThat(audioPlaylistRepository.search(UNIQUE_ID.contains("favorites"))).containsExactly(thisWeeksFavorites);
        assertThat(audioPlaylistRepository.size()).isEqualTo(6);

        audioPlaylistRepository.addOrReplaceAll(Set.of(bestHits, thisWeeksFavorites));
        assertThat(audioPlaylistRepository.size()).isEqualTo(6);

        assertThat(audioPlaylistRepository.search(SELF.isNotDirectory())).containsExactly(rock, pop, thisWeeksFavorites);
        assertThat(audioPlaylistRepository.search(SELF.isDirectory())).containsExactly(fifties, sixties, bestHits);

        rock = audioPlaylistRepository.findById(rock.getId()).orElseThrow();
        fifties = audioPlaylistRepository.findSingleDirectoryByName(fifties.getName()).orElseThrow();
        var fiftiesItems =
                List.of(createTestAudioItem("50s hit", Duration.ofSeconds(30)),
                        createTestAudioItem("50s favorite song", Duration.ofSeconds(120)));
        audioPlaylistRepository.addAudioItemsToPlaylist(fiftiesItems, fifties);
        var playlistsThatContainsAnyAudioItemsWithHitInTitle =
                audioPlaylistRepository.search(QueryPredicate.of(SELF.audioItemsAnyMatch(TITLE.contains("hit"))));
        fifties = audioPlaylistRepository.findSingleDirectoryByName(fifties.getName()).orElseThrow();
        assertThat(playlistsThatContainsAnyAudioItemsWithHitInTitle).containsExactly(rock, fifties);

        var playlistsThatContainsAudioItemsWithDurationBelow60 =
                audioPlaylistRepository.search(QueryPredicate.of(SELF.audioItemsAnyMatch(DURATION.isShorterThan(Duration.ofSeconds(61)))));
        assertThat(playlistsThatContainsAudioItemsWithDurationBelow60).containsExactly(rock, fifties);

        audioPlaylistRepository.removeAudioItemsFromPlaylist(fiftiesItems, fifties);
        assertThat(audioPlaylistRepository.findById(fifties.getId()).get().audioItems()).isEmpty();

        audioPlaylistRepository.removeAudioItems(rockAudioItems);
        assertThat(audioPlaylistRepository.findById(rock.getId()).get().audioItems()).isEmpty();

        audioPlaylistRepository.clear();
        assertThat(audioPlaylistRepository).isEmpty();
    }

    // ├──Best hits
    // │  └──50s
    // │     ├──Rock
    // │     └──Pop
    // └──Selection of playlists
    @Test
    @DisplayName("Move playlists in the hierarchy")
    void movePlaylists() throws Exception {
        audioPlaylistRepository = new AudioPlaylistInMemoryRepository();

        var rock = audioPlaylistRepository.createPlaylist("Rock");
        assertThat(audioPlaylistRepository.findSingleByAttribute(NAME, rock.getName()).orElseThrow())
                .isEqualTo(rock);
        var pop = audioPlaylistRepository.createPlaylist("Pop");
        var fifties = audioPlaylistRepository.createPlaylistDirectory("50s");
        audioPlaylistRepository.addPlaylistsToDirectory(Set.of(rock, pop), fifties);
        var bestHits = audioPlaylistRepository.createPlaylistDirectory("Best hits");
        audioPlaylistRepository.addPlaylistsToDirectory(Set.of(fifties), bestHits);
        var selection = audioPlaylistRepository.createPlaylistDirectory("Selection of playlists");
        assertThat(audioPlaylistRepository.size()).isEqualTo(5);

        audioPlaylistRepository.movePlaylist(rock, selection);

        // ├──Best hits
        // │  └──50s
        // │     └──Pop
        // └──Selection of playlists
        //    └──Rock

        assertThat(audioPlaylistRepository.size()).isEqualTo(5);
        assertThat(audioPlaylistRepository.findSingleDirectoryByName(selection.getName()).orElseThrow().descendantPlaylists()).containsExactly(rock);
        assertThat(audioPlaylistRepository.findSingleDirectoryByName(fifties.getName()).orElseThrow().descendantPlaylists()).doesNotContain(rock);

        // --

        fifties = audioPlaylistRepository.findSingleDirectoryByName(fifties.getName()).orElseThrow();
        selection = audioPlaylistRepository.findSingleDirectoryByName(selection.getName()).orElseThrow();

        audioPlaylistRepository.movePlaylist(selection, fifties);

        // └──Best hits
        //    └──50s
        //       ├──Pop
        //       └──Selection of playlists
        //          └──Rock

        assertThat(audioPlaylistRepository.size()).isEqualTo(5);
        assertThat(audioPlaylistRepository.findSingleDirectoryByName(selection.getName()).orElseThrow().descendantPlaylists())
                .containsExactly(rock);
        assertThat(audioPlaylistRepository.findSingleDirectoryByName(fifties.getName()).orElseThrow().descendantPlaylists()).containsExactly(pop, selection);
    }

    @Test
    @DisplayName("Create playlists with existing name")
    void createPlaylistWitExistingNameTest() throws Exception {
        audioPlaylistRepository = new AudioPlaylistInMemoryRepository();

        var newPlaylistDirectory = audioPlaylistRepository.createPlaylistDirectory("New playlist");
        assertThat(assertThrows(RepositoryException.class, () -> audioPlaylistRepository.createPlaylistDirectory("New playlist")))
                .hasMessageThat().isEqualTo("Playlist with name 'New playlist' already exists");

        var newPlaylist = audioPlaylistRepository.createPlaylist("New playlist");
        assertThat(assertThrows(RepositoryException.class, () -> audioPlaylistRepository.createPlaylist("New playlist")))
                .hasMessageThat().isEqualTo("Playlist with name 'New playlist' already exists");

        assertThat(audioPlaylistRepository.size()).isEqualTo(2);
        assertThat(audioPlaylistRepository.findAllByName("New playlist")).containsExactly(newPlaylistDirectory, newPlaylist);

        audioPlaylistRepository.movePlaylist(newPlaylist, newPlaylistDirectory);

        assertThat(audioPlaylistRepository.size()).isEqualTo(2);
        assertThat(audioPlaylistRepository.findByUniqueId(newPlaylist.getId() + "-New playlist").orElseThrow()).isEqualTo(newPlaylist);
        assertThat(audioPlaylistRepository.findByAttribute(NAME, "New playlist")).containsExactly(newPlaylist, newPlaylistDirectory);
        assertThat(audioPlaylistRepository.findAllByName("New playlist")).containsExactly(newPlaylistDirectory, newPlaylist);

        assertThat(audioPlaylistRepository.removeAll(Set.of(newPlaylist, newPlaylistDirectory))).isTrue();

        assertThat(audioPlaylistRepository.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Add playlists not created with the repository")
    void addPlaylistsNotCreatedWithTheRepositoryTest() throws Exception {
        audioPlaylistRepository = new AudioPlaylistInMemoryRepository();
        audioPlaylistRepository.createPlaylist("Best hits");
        audioPlaylistRepository.createPlaylistDirectory("Nina Simone discography");
        assertThat(audioPlaylistRepository).hasSize(2);

        var bestHits = mock(ImmutablePlaylist.class);
        when(bestHits.getName()).thenReturn("Best hits");
        when(bestHits.isDirectory()).thenReturn(false);
        var rock = mock(ImmutablePlaylist.class);
        when(rock.getName()).thenReturn("Best hits - Rock");
        when(rock.isDirectory()).thenReturn(false);
        audioPlaylistRepository.addOrReplaceAll(Set.<AudioPlaylist<AudioItem>>of(bestHits, rock));
        assertThat(audioPlaylistRepository).hasSize(3);

        var ninaSimoneDiscography = mock(ImmutablePlaylistDirectory.class);
        when(ninaSimoneDiscography.getName()).thenReturn("Nina Simone discography");
        when(ninaSimoneDiscography.isDirectory()).thenReturn(true);
        var revolver = mock(ImmutablePlaylist.class);
        when(revolver.getName()).thenReturn("Revolver");
        when(revolver.isDirectory()).thenReturn(false);
        var beatlesDiscography = mock(ImmutablePlaylistDirectory.class);
        when(beatlesDiscography.getName()).thenReturn("The Beatles' discography");
        when(beatlesDiscography.isDirectory()).thenReturn(true);
        when(beatlesDiscography.descendantPlaylists()).thenReturn(Set.of(revolver));
        audioPlaylistRepository.addOrReplaceAll(Set.<AudioPlaylist<AudioItem>>of(ninaSimoneDiscography, beatlesDiscography));

        assertThat(audioPlaylistRepository).hasSize(5);
        assertThat(audioPlaylistRepository.findSingleDirectoryByName(beatlesDiscography.getName()).orElseThrow().descendantPlaylists())
                .isNotEmpty();
    }
}
