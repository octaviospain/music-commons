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
import static net.transgressoft.commons.music.playlist.PlaylistStringAttribute.UNIQUE_ID;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class AudioPlaylistInMemoryRepositoryTest extends MusicLibraryTestBase {

    AudioPlaylistRepository<AudioItem, AudioPlaylist<AudioItem>, AudioPlaylistDirectory<AudioItem>> audioPlaylistRepository;

    /**
     * ├──Best hits
     * │  ├──50s
     * │  │  ├──Rock
     * │  │  │  ├──*50s Rock hit 1
     * │  │  │  └──*50s Rock hit 2 my fav
     * │  │  ├──Pop
     * │  │  ├──*50s hit 1
     * │  │  └──*50s favorite song
     * │  └──60s favorites
     * └──This weeks' favorites songs
     */
    @Test
    @DisplayName("Mixed playlists hierarchy structure and audio items search")
    void searchOperationsTest() {
        audioPlaylistRepository = new AudioPlaylistInMemoryRepository<>();

        var rock = audioPlaylistRepository.createPlaylist("Rock", List.of(
                createTestAudioItem("50s Rock hit 1", Duration.ofSeconds(60)),
                createTestAudioItem("50s Rock hit 2 my fav", Duration.ofSeconds(230))));
        assertThat(audioPlaylistRepository.findSinglePlaylistByName(rock.getName()).orElseThrow()).isEqualTo(rock);
        var playlistsThatContainsAllAudioItemsWith50sInTitle =
                audioPlaylistRepository.search(QueryPredicate.of(SELF.audioItemsAllMatch(TITLE.contains("50s"))));
        assertThat(playlistsThatContainsAllAudioItemsWith50sInTitle).containsExactly(rock);

        var pop = audioPlaylistRepository.createPlaylist("Pop");
        assertThat(audioPlaylistRepository.size()).isEqualTo(2);
        assertThat(audioPlaylistRepository.numberOfPlaylists()).isEqualTo(2);

        var fifties = audioPlaylistRepository.createPlaylistDirectory("50s", List.of(
                createTestAudioItem("50s hit", Duration.ofSeconds(30)),
                createTestAudioItem("50s favorite song", Duration.ofSeconds(120))));
        audioPlaylistRepository.addPlaylistsToDirectory(Set.of(pop, rock), fifties);
        assertThat(audioPlaylistRepository.findSingleDirectoryByName(fifties.getName()).orElseThrow()
                           .descendantPlaylists())
                .containsExactly(pop, rock);
        assertThat(audioPlaylistRepository.numberOfPlaylistDirectories()).isEqualTo(1);
        var playlistsThatContainsAnyAudioItemsWithHitInTitle =
                audioPlaylistRepository.search(QueryPredicate.of(SELF.audioItemsAnyMatch(TITLE.contains("hit"))));
        assertThat(playlistsThatContainsAnyAudioItemsWithHitInTitle).containsExactly(rock, fifties);

        var sixties = audioPlaylistRepository.createPlaylistDirectory("60s");
        assertThat(sixties.descendantPlaylists()).isEmpty();
        assertThat(audioPlaylistRepository.numberOfPlaylistDirectories()).isEqualTo(2);
        assertThat(audioPlaylistRepository.findByUniqueId(sixties.id() + "-D-" + sixties.getName())).isEqualTo(Optional.of(sixties));

        var bestHits = audioPlaylistRepository.createPlaylistDirectory("Best hits");
        audioPlaylistRepository.addPlaylistsToDirectory(Set.of(fifties, sixties), bestHits);
        assertThat(bestHits.descendantPlaylists()).containsExactly(fifties, sixties);   // bestHits is update although, immutable

        var thisWeeksFavorites = audioPlaylistRepository.createPlaylist("This weeks' favorites songs");
        assertThat(audioPlaylistRepository.search(UNIQUE_ID.contains("favorites"))).containsExactly(thisWeeksFavorites);
        assertThat(audioPlaylistRepository.size()).isEqualTo(6);

        audioPlaylistRepository.add(bestHits, thisWeeksFavorites);
        assertThat(audioPlaylistRepository.size()).isEqualTo(6);

        assertThat(audioPlaylistRepository.search(SELF.isNotDirectory())).containsExactly(rock, pop, thisWeeksFavorites);
        assertThat(audioPlaylistRepository.search(SELF.isDirectory())).containsExactly(fifties, sixties, bestHits);

        var playlistsThatContainsAudioItemsWithDurationBelow60 =
                audioPlaylistRepository.search(QueryPredicate.of(SELF.audioItemsAnyMatch(DURATION.isShorterThan(Duration.ofSeconds(61)))));
        assertThat(playlistsThatContainsAudioItemsWithDurationBelow60).containsExactly(rock, fifties);
    }

    /**
     * ├──Best hits
     * │  └──50s
     * │     ├──Rock
     * │     └──Pop
     * └──Selection of playlists
     */
    @Test
    @DisplayName("Move playlists")
    void movePlaylists() {
        audioPlaylistRepository = new AudioPlaylistInMemoryRepository<>();

        var rock = audioPlaylistRepository.createPlaylist("Rock");
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
        assertThat(audioPlaylistRepository.findSingleDirectoryByName(selection.getName()).orElseThrow().descendantPlaylists()).containsExactly(rock);
        assertThat(audioPlaylistRepository.findSingleDirectoryByName(fifties.getName()).orElseThrow().descendantPlaylists()).containsExactly(pop, selection);

        // --

        assertThat(audioPlaylistRepository.findSinglePlaylistByName("New playlist")).isEqualTo(Optional.empty());
        assertThat(audioPlaylistRepository.findSingleDirectoryByName("New playlist")).isEqualTo(Optional.empty());

        var newPlaylistDirectory = audioPlaylistRepository.createPlaylistDirectory("New playlist");
        var newPlaylist = audioPlaylistRepository.createPlaylist("New playlist");
        var newPlaylist2 = audioPlaylistRepository.createPlaylist("New playlist");  // Creation of playlist with same name makes no action

        // ├──Best hits
        // │ └──50s
        // │     ├──Pop
        // │     └──Selection of playlists
        // │        └──Rock
        // ├──New playlist // (directory)
        // └──New playlist

        assertThat(audioPlaylistRepository.size()).isEqualTo(7);
        assertThat(audioPlaylistRepository.findAllByName("New playlist")).containsExactly(newPlaylistDirectory, newPlaylist);
        assertThat(assertThrows(IllegalStateException.class, () -> audioPlaylistRepository.findSingleDirectoryByName("New playlist")))
                .hasMessageThat().contains("Found several playlists when searching single by name 'New playlist':");
        assertThat(assertThrows(IllegalStateException.class, () -> audioPlaylistRepository.findSinglePlaylistByName("New playlist")))
                .hasMessageThat().contains("Found several playlists when searching single by name 'New playlist': [" + newPlaylist + ", " + newPlaylistDirectory + "]");
        assertThat(newPlaylist).isEqualTo(newPlaylist2);

        audioPlaylistRepository.movePlaylist(newPlaylist, newPlaylistDirectory);

        // ├──Best hits
        // │ └──50s
        // │     ├──Pop
        // │     └──Selection of playlists
        // │        └──Rock
        // ├──New playlist // (directory)
        //    └──New Playlist

        assertThat(audioPlaylistRepository.size()).isEqualTo(7);
        assertThat(audioPlaylistRepository.findByUniqueId(newPlaylist.id() + "-New playlist").orElseThrow()).isEqualTo(newPlaylist);
        assertThat(audioPlaylistRepository.findByUniqueId(newPlaylistDirectory.id() + "-D-New playlist").orElseThrow()).isEqualTo(newPlaylistDirectory);
        assertThat(audioPlaylistRepository.findAllByName("New playlist")).containsExactly(newPlaylistDirectory, newPlaylist);

        audioPlaylistRepository.removeAll(Set.of(newPlaylistDirectory, pop));

        // ├──Best hits
        // │ └──50s
        // │     ├──Pop
        // │     └──Selection of playlists
        // │        └──Rock

        assertThat(audioPlaylistRepository.size()).isEqualTo(4);
        assertThat(audioPlaylistRepository.findSingleDirectoryByName("New playlist")).isEqualTo(Optional.empty());
        assertThat(audioPlaylistRepository.findById(newPlaylistDirectory.id())).isEqualTo(Optional.empty());
        assertThat(audioPlaylistRepository.findSinglePlaylistByName("New playlist")).isEqualTo(Optional.empty());
        assertThat(audioPlaylistRepository.findById(newPlaylist.id())).isEqualTo(Optional.empty());
        assertThat(audioPlaylistRepository.findAllByName("New playlist")).isEmpty();
    }
}
