package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.audio.AudioItem;
import net.transgressoft.commons.music.audio.AudioItemTestFactory;
import net.transgressoft.commons.music.audio.ImmutableAudioItem;
import net.transgressoft.commons.query.QueryPredicate;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static com.google.common.truth.Truth.assertThat;
import static net.transgressoft.commons.music.audio.attribute.DurationAudioItemAttribute.DURATION;
import static net.transgressoft.commons.music.audio.attribute.StringAudioItemAttribute.TITLE;
import static net.transgressoft.commons.music.playlist.attribute.PlaylistNodeAttribute.SELF;

class AudioPlaylistRepositoryTest {

    AudioItemTestFactory audioItemTestFactory = new AudioItemTestFactory();

    @Test
    @DisplayName("Search operations")
    void searchOperationsTest() {
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

        var p1d = AudioPlaylistDirectory.builder("Playlist Directory 1")
                .descendantPlaylists(Set.of(p2))
                .audioItems(audioItemTestFactory.createTestAudioItemsSet(5))
                .build();

        var repository = new AudioPlaylistInMemoryRepository(List.of(p1, p2, p1d));

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
    @Disabled
    void movePlaylists() {
        // ROOT
        // ├──Best hits
        // │  ├──50s
        // │  │  ├──Rock
        // │  │  └──Pop
        // │  └──60s favorites
        // ├──Selection
        // └──This weeks' favorites

        //        rootPlaylistTree.movePlaylist(rock, selection);
        //
        //        assertFalse(fifties.audioPlaylists().contains(rock));
        //        assertEquals(Optional.of(selection), rootPlaylistTree.findParentPlaylist("Rock"));
        //        assertTrue(selection.audioPlaylists().contains(rock));
        //
        //        rootPlaylistTree.movePlaylistTree(selection, fifties);
        //
        //        assertFalse(rootPlaylistTree.subPlaylistTrees().contains(selection));
        //        assertEquals(Optional.of(fifties), rootPlaylistTree.findParentPlaylist("Selection"));
        //        assertTrue(fifties.subPlaylistTrees().contains(selection));

    }
}
