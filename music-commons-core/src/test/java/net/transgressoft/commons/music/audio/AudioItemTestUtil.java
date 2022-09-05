package net.transgressoft.commons.music.audio;

import com.neovisionaries.i18n.CountryCode;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static net.transgressoft.commons.music.audio.AlbumAttribute.ALBUM;
import static net.transgressoft.commons.music.audio.ArtistAttribute.ARTIST;
import static net.transgressoft.commons.music.audio.AudioItemDurationAttribute.DURATION;
import static net.transgressoft.commons.music.audio.AudioItemLocalDateTimeAttribute.DATE_OF_CREATION;
import static net.transgressoft.commons.music.audio.AudioItemLocalDateTimeAttribute.LAST_DATE_MODIFIED;
import static net.transgressoft.commons.music.audio.AudioItemPathAttribute.PATH;
import static net.transgressoft.commons.music.audio.AudioItemStringAttribute.TITLE;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AudioItemTestUtil {

    AudioItemTestFactory audioItemTestFactory = new AudioItemTestFactory();

    protected AudioItem createTestAudioItem() {
        return audioItemTestFactory.createTestAudioItem();
    }

    protected AudioItem createTestAudioItem(String name) {
        return audioItemTestFactory.createTestAudioItem(name);
    }

    protected AudioItem createTestAudioItem(int id, Album album) {
        return audioItemTestFactory.createTestAudioItem(id, album);
    }

    protected AudioItem createTestAudioItem(Album album) {
        return audioItemTestFactory.createTestAudioItem(album);
    }

    protected AudioItem createTestAudioItem(String name, Duration duration) {
        return audioItemTestFactory.createTestAudioItem(name, duration);
    }

    protected List<AudioItem> createTestAudioItemsSet(int size) {
        return audioItemTestFactory.createTestAudioItemsList(size);
    }
}

class AudioItemTestFactory {

    private static final Path DEFAULT_PATH = Path.of("Music");
    private static final Duration DEFAULT_DURATION = Duration.ofSeconds(32);
    private int testCounter = 400;

    private final AudioItemAttributes attributes = SimpleAudioItemAttributesKt.getEmptyAttributes();
    private final Artist noArtist;
    private final Album noAlbum;
    private final Label noLabel;

    AudioItemTestFactory() {
        noArtist = mock(Artist.class);
        when(noArtist.name()).thenReturn("");
        when(noArtist.countryCode()).thenReturn(CountryCode.UNDEFINED.name());

        noLabel = mock(Label.class);
        when(noLabel.name()).thenReturn("");
        when(noLabel.countryCode()).thenReturn(CountryCode.UNDEFINED.name());

        noAlbum = mock(Album.class);
        when(noAlbum.name()).thenReturn("");
        when(noAlbum.albumArtist()).thenReturn(noArtist);
        when(noAlbum.year()).thenReturn((short) -1);
        when(noAlbum.label()).thenReturn(noLabel);
        when(noAlbum.audioItems()).thenReturn(Collections.emptySet());
        when(noAlbum.isCompilation()).thenReturn(false);
        when(noAlbum.coverImage()).thenReturn(Optional.empty());

        attributes.set(ARTIST, noArtist);
        attributes.set(ALBUM, noAlbum);
        attributes.set(DURATION, DEFAULT_DURATION);
    }

    public AudioItem createTestAudioItem() {
        int id = testCounter++;
        var now = LocalDateTime.now();
        attributes.set(PATH, DEFAULT_PATH.resolve(id + ".mp3"));
        attributes.set(DATE_OF_CREATION, now);
        attributes.set(LAST_DATE_MODIFIED, now);
        return new ImmutableAudioItem(id, attributes);
    }

    public AudioItem createTestAudioItem(String title) {
        int id = testCounter++;
        attributes.set(PATH, DEFAULT_PATH.resolve(id + ".mp3"));
        return new ImmutableAudioItem(id, attributes.modifiedCopy(TITLE, title));
    }

    public AudioItem createTestAudioItem(Album album) {
        int id = testCounter++;
        attributes.set(PATH, DEFAULT_PATH.resolve(id + ".mp3"));
        return new ImmutableAudioItem(id, attributes.modifiedCopy(ALBUM, album));
    }

    public AudioItem createTestAudioItem(int id, Album album) {
        attributes.set(PATH, DEFAULT_PATH.resolve(id + ".mp3"));
        return new ImmutableAudioItem(id, attributes.modifiedCopy(ALBUM, album));
    }

    public AudioItem createTestAudioItem(String title, Duration duration) {
        int id = testCounter++;
        attributes.set(PATH, DEFAULT_PATH.resolve(id + ".mp3"));
        var map = attributes.modifiedCopy(TITLE, title);
        map.set(DURATION, duration);
        return new ImmutableAudioItem(id, map);
    }

    public List<AudioItem> createTestAudioItemsList(int size) {
        var list = new ArrayList<AudioItem>();
        for (int i = 0; i < size; i++) {
            list.add(createTestAudioItem());
        }
        return list;
    }
}
