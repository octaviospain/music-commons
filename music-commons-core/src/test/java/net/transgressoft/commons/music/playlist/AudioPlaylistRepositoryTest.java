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
import static org.junit.jupiter.api.Assertions.assertThrows;

class AudioPlaylistRepositoryTest extends MusicLibraryTestBase {

    @Test
    @DisplayName("Search operations")
    void searchOperationsTest() throws RepositoryException {
        var p1 = createPlaylist("Playlist 1", List.of(
                        createTestAudioItem("Music", "One", Duration.ofSeconds(60)),
                        createTestAudioItem("Music", "One-Two", Duration.ofSeconds(60))));

        var p2 = createPlaylist("Playlist 2", List.of(
                        createTestAudioItem("Music", "Hey Jude", Duration.ofSeconds(30)),
                        createTestAudioItem("Music", "One-Two", Duration.ofSeconds(60))));

        var p1d = createPlaylistDirectory("Playlist Directory 1", Set.of(p2), createTestAudioItemsSet(5));

        var repository = new AudioPlaylistInMemoryRepository();
        repository.add(p1, p2, p1d);

        var notDirectories = repository.search(
                QueryPredicate.of(
                        SELF.isNotDirectory()));

        assertThat(notDirectories).containsExactly(p1, p2);

        var containsOneInTitle = repository.search(
                QueryPredicate.of(SELF.audioItemsAllMatch(TITLE.contains("One"))));

        assertThat(containsOneInTitle).containsExactly(p1);

        var hasNoAncestor = repository.search(
                QueryPredicate.of(SELF.hasNoAncestor()));

        assertThat(hasNoAncestor).containsExactly(p1, p1d);

        var durationIsBelow60 = repository.search(
                QueryPredicate.of(SELF.audioItemsAnyMatch(DURATION.isShorterThan(Duration.ofSeconds(60)))));

        assertThat(durationIsBelow60).containsExactly(p2, p1d);

        var directories = repository.search(
                QueryPredicate.of(SELF.isDirectory()));

        assertThat(directories).containsExactly(p1d);
    }

    @Test
    @DisplayName("Move playlists")
    void movePlaylists() throws Exception  {
        // ROOT
        // ├──Best hits
        // │  ├──50s
        // │  │  ├──Rock
        // │  │  └──Pop
        // │  └──60s favorites
        // └──Selection of playlists

        var bestHits = createPlaylistDirectory(
                "Best hits",
                Set.of(createPlaylistDirectory(
                                "50s", Set.of(
                                        createPlaylist("Rock"),
                                        createPlaylist("Pop"))),
                       createPlaylistDirectory(
                               "60s favorites")
                ));

        var selection = createPlaylistDirectory("Selection of playlists");

        AudioPlaylistInMemoryRepository repository = new AudioPlaylistInMemoryRepository();
        repository.add(bestHits, selection);

        MutableAudioPlaylist<AudioItem> rock = repository.findSinglePlaylistByName("Rock").orElseThrow();
        MutablePlaylistDirectory<AudioItem> fifties = repository.findSingleDirectoryByName("50s").orElseThrow();

        assertThat(repository.size()).isEqualTo(6);

        var thisWeeksFavorites = createPlaylist("This weeks' favorites songs");

        repository.add(thisWeeksFavorites);

        assertThat(repository.size()).isEqualTo(7);
        assertThat(thisWeeksFavorites.getAncestor()).isEqualTo(RootAudioPlaylistNode.INSTANCE);
        assertThat(repository.findSinglePlaylistByName("This weeks' favorites songs").get()).isEqualTo(thisWeeksFavorites);

        // ROOT
        // ├──Best hits
        // │  ├──50s
        // │  │  ├──Rock
        // │  │  └──Pop
        // │  └──60s favorites
        // ├──Selection of playlistsaudioItemTestFactory
        // └──This weeks' favorites songs

        assertThat(repository.size()).isEqualTo(7);
        assertThat(selection.getAncestor()).isEqualTo(RootAudioPlaylistNode.INSTANCE);
        assertThat(selection.isEmptyOfPlaylists()).isTrue();
        assertThat(bestHits.containsPlaylist(fifties)).isTrue();
        assertThat(fifties.containsPlaylist(rock)).isTrue();

        repository.movePlaylist(rock, selection);

        // ROOT
        // ├──Best hits
        // │  ├──50s
        // │  │  └──Pop
        // │  └──60s favorites
        // ├──Selection of playlists
        // |  └──Rock
        // └──This weeks' favorites songs

        assertThat(repository.size()).isEqualTo(7);
        assertThat(selection.isEmptyOfPlaylists()).isFalse();
        assertThat(selection.containsPlaylist(rock)).isTrue();
        assertThat(fifties.containsPlaylist(rock)).isFalse();
        assertThat(rock.getAncestor()).isEqualTo(selection);
        assertThat(bestHits.containsPlaylist(fifties)).isTrue();
        assertThat(selection.getAncestor()).isEqualTo(RootAudioPlaylistNode.INSTANCE);

        repository.movePlaylist(selection, fifties);

        // ROOT
        // ├──Best hits
        // │  ├──50s
        // |  |  ├──Selection of playlists
        // |  |  |   └──Rock
        // │  │  └──Pop
        // │  └──60s favorites
        // └──This weeks' favorites songs

        assertThat(repository.size()).isEqualTo(7);
        assertThat(bestHits.containsPlaylist(fifties)).isTrue();
        assertThat(fifties.containsPlaylist(selection)).isTrue();
        assertThat(selection.getAncestor()).isEqualTo(fifties);
        assertThat(repository.findSingleDirectoryByName("Selection of playlists").isPresent()).isTrue();
        assertThat(repository.search(QueryPredicate.of(PlaylistStringAttribute.NAME.contains("Selection"))))
                .containsExactly(selection);

        assertThat(repository.findSinglePlaylistByName("New playlist")).isEqualTo(Optional.empty());
        assertThat(repository.findSingleDirectoryByName("New playlist")).isEqualTo(Optional.empty());

        var newDirectory = createPlaylistDirectory("New playlist");
        var newPlaylist = createPlaylist("New playlist");
        var newPlaylist2 = createPlaylist("New playlist");

        repository.add(newDirectory, newPlaylist);

        // ROOT
        // ├──Best hits
        // │  ├──50s
        // |  |  ├──Selection of playlists
        // |  |  |   └──Rock
        // │  │  └──Pop
        // │  └──60s favorites
        // ├──This weeks' favorites songs
        // ├──New playlist // (directory)
        // └──New Playlist

        assertThat(repository.size()).isEqualTo(9);
        assertThat(newDirectory.getAncestor()).isEqualTo(RootAudioPlaylistNode.INSTANCE);
        assertThat(newPlaylist.getAncestor()).isEqualTo(RootAudioPlaylistNode.INSTANCE);
        assertThat(repository.findAllByName("New playlist")).containsExactly(newDirectory, newPlaylist);
        assertThat(assertThrows(RepositoryException.class, () -> repository.findSingleDirectoryByName("New playlist")))
                .hasMessageThat().contains("Found several playlists when searching single by name New playlist");
        assertThat(assertThrows(RepositoryException.class, () -> repository.findSinglePlaylistByName("New playlist")))
                .hasMessageThat().isEqualTo("Found several playlists when searching single by name New playlist");

        repository.movePlaylist(newPlaylist2, newDirectory);

        // ROOT
        // ├──Best hits
        // │  ├──50s
        // |  |  ├──Selection of playlists
        // |  |  |   └──Rock
        // │  │  └──Pop
        // │  └──60s favorites
        // ├──This weeks' favorites songs
        // ├──New playlist (directory)
        // |  └──New Playlist // (2)
        // ├──New Playlist // (1)

        assertThat(repository.size()).isEqualTo(10);
        assertThat(newPlaylist2.getAncestor()).isEqualTo(newDirectory);
        assertThat(repository.findByUniqueId(newPlaylist2.id() + "-New playlist").get()).isEqualTo(newPlaylist2);
        assertThat(repository.findByUniqueId(newPlaylist2.id() + "-New playlist").get().getAncestor()).isEqualTo(newDirectory);
        assertThat(repository.findByUniqueId(newPlaylist.id() + "-New playlist").get()).isEqualTo(newPlaylist);
        assertThat(repository.findByUniqueId(newPlaylist.id() + "-New playlist").get().getAncestor()).isEqualTo(RootAudioPlaylistNode.INSTANCE);
        assertThat(repository.findAllByName("New playlist")).containsExactly(newDirectory, newPlaylist, newPlaylist2);

        repository.remove(newDirectory);

        // ROOT
        // ├──Best hits
        // │  ├──50s
        // |  |  ├──Selection of playlists
        // |  |  |   └──Rock
        // │  │  └──Pop
        // │  └──60s favorites
        // ├──This weeks' favorites songs
        // ├──New Playlist // (1)

        assertThat(repository.size()).isEqualTo(8);
        assertThat(newDirectory.getAncestor()).isNull();
        assertThat(newPlaylist2.getAncestor()).isNull();
        assertThat(newDirectory.containsPlaylist(newPlaylist2)).isFalse();
        assertThat(repository.findSingleDirectoryByName("New playlist")).isEqualTo(Optional.empty());
        assertThat(repository.findById(newDirectory.id())).isEqualTo(Optional.empty());
        assertThat(repository.findSinglePlaylistByName("New playlist").get()).isEqualTo(newPlaylist);
        assertThat(repository.findById(newPlaylist.id()).get()).isEqualTo(newPlaylist);
        assertThat(repository.findAllByName("New playlist")).containsExactly(newPlaylist);
    }
}
