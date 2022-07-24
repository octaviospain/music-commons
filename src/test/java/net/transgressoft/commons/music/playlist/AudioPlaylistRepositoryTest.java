package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.audio.AudioItemTestFactory;
import net.transgressoft.commons.music.audio.ImmutableAudioItem;
import net.transgressoft.commons.music.playlist.attribute.PlaylistNodeAttribute;
import net.transgressoft.commons.music.playlist.attribute.PlaylistStringAttribute;
import net.transgressoft.commons.query.QueryPredicate;
import net.transgressoft.commons.query.Repository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.google.common.truth.Truth.assertThat;
import static net.transgressoft.commons.music.audio.attribute.DurationAudioItemAttribute.DURATION;
import static net.transgressoft.commons.music.audio.attribute.StringAudioItemAttribute.TITLE;
import static net.transgressoft.commons.music.playlist.AudioPlaylistDirectory.ROOT;
import static net.transgressoft.commons.music.playlist.AudioPlaylistDirectory.builder;
import static net.transgressoft.commons.music.playlist.attribute.PlaylistNodeAttribute.SELF;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AudioPlaylistRepositoryTest {

    AudioItemTestFactory audioItemTestFactory = new AudioItemTestFactory();

    @Test
    @DisplayName("Search operations")
    void searchOperationsTest() throws RepositoryException {
        var p1 = AudioPlaylist.builder("Playlist 1")
                .audioItems(List.of(
                        ImmutableAudioItem.builder(Path.of("Music"), "One", Duration.ofSeconds(60), 320).build(),
                        ImmutableAudioItem.builder(Path.of("Music"), "One-Two", Duration.ofSeconds(60), 320).build()))
                .build();

        var p2 = AudioPlaylist.builder("Playlist 2")
                .audioItems(List.of(
                        ImmutableAudioItem.builder(Path.of("Music"), "Hey Jude", Duration.ofSeconds(30), 320).build(),
                        ImmutableAudioItem.builder(Path.of("Music"), "One-Two", Duration.ofSeconds(60), 320).build()))
                .build();

        var p1d = builder("Playlist Directory 1")
                .descendantPlaylists(Set.of(p2))
                .audioItems(audioItemTestFactory.createTestAudioItemsSet(5))
                .build();

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
    void movePlaylists() throws Exception {
        // ROOT
        // ├──Best hits
        // │  ├──50s
        // │  │  ├──Rock
        // │  │  └──Pop
        // │  └──60s favorites
        // └──Selection of playlists

        AudioPlaylistDirectory bestHits = builder("Best hits").descendantPlaylists(
                builder("50s").descendantPlaylists(
                        AudioPlaylist.builder("Rock").build(),
                        AudioPlaylist.builder("Pop").build())
                        .build(),
                builder("60s favorites")
                        .build())
                .build();
        AudioPlaylistDirectory selection = builder("Selection of playlists").build();

        AudioPlaylistInMemoryRepository repository = new AudioPlaylistInMemoryRepository();
        repository.add(bestHits, selection);

        AudioPlaylist rock = repository.findSinglePlaylistByName("Rock").orElseThrow();
        AudioPlaylistDirectory fifties = repository.findSingleDirectoryByName("50s").orElseThrow();

        assertThat(repository.size()).isEqualTo(6);

        AudioPlaylist thisWeeksFavorites = AudioPlaylist.builder("This weeks' favorites songs").build();

        repository.add(thisWeeksFavorites);

        assertThat(repository.size()).isEqualTo(7);
        assertThat(thisWeeksFavorites.getAncestor()).isEqualTo(ROOT);
        assertThat(repository.findSinglePlaylistByName("This weeks' favorites songs").get()).isEqualTo(thisWeeksFavorites);

        // ROOT
        // ├──Best hits
        // │  ├──50s
        // │  │  ├──Rock
        // │  │  └──Pop
        // │  └──60s favorites
        // ├──Selection of playlists
        // └──This weeks' favorites songs

        assertThat(repository.size()).isEqualTo(7);
        assertThat(selection.getAncestor()).isEqualTo(ROOT);
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
        assertThat(selection.getAncestor()).isEqualTo(ROOT);

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

        var newDirectory = builder("New playlist").build();
        var newPlaylist = AudioPlaylist.builder("New playlist").build();
        var newPlaylist2 = AudioPlaylist.builder("New playlist").build();

        repository.add(newDirectory, newPlaylist);

        // ROOT
        // ├──Best hits
        // │  ├──50s
        // |  |  ├──Selection of playlists
        // |  |  |   └──Rock
        // │  │  └──Pop
        // │  └──60s favorites
        // ├──This weeks' favorites songs
        // ├──New playlist (directory)
        // └──New Playlist (1)

        assertThat(repository.size()).isEqualTo(9);
        assertThat(newDirectory.getAncestor()).isEqualTo(ROOT);
        assertThat(newPlaylist.getAncestor()).isEqualTo(ROOT);
        assertThat(repository.findAllByName("New playlist")).containsExactly(newDirectory, newPlaylist);
        assertThat(assertThrows(RepositoryException.class, () -> repository.findSingleDirectoryByName("New playlist")))
                .hasMessageThat().isEqualTo("Found several playlists when searching single by name New playlist");
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
        // |  └──New Playlist (2)
        // ├──New Playlist (1)

        assertThat(repository.size()).isEqualTo(10);
        assertThat(newPlaylist2.getAncestor()).isEqualTo(newDirectory);
        assertThat(repository.findByUniqueId(newPlaylist2.id() + "-New playlist").get()).isEqualTo(newPlaylist2);
        assertThat(repository.findByUniqueId(newPlaylist2.id() + "-New playlist").get().getAncestor()).isEqualTo(newDirectory);
        assertThat(repository.findByUniqueId(newPlaylist.id() + "-New playlist").get()).isEqualTo(newPlaylist);
        assertThat(repository.findByUniqueId(newPlaylist.id() + "-New playlist").get().getAncestor()).isEqualTo(ROOT);
        assertThat(repository.findAllByName("New playlist")).containsExactly(newDirectory, newPlaylist, newPlaylist2);
    }
}
