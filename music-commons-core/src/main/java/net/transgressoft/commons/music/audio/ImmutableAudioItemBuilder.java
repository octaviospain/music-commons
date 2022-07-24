package net.transgressoft.commons.music.audio;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;

class ImmutableAudioItemBuilder extends AudioItemBuilderBase<AudioItem> {

    public ImmutableAudioItemBuilder(Path path, String title, Duration duration, int bitRate, LocalDateTime dateOfInclusion) {
        super(path, title, duration, bitRate, dateOfInclusion);
    }

    protected ImmutableAudioItemBuilder(Path path, String title, Duration duration, int bitRate, LocalDateTime dateOfInclusion, LocalDateTime lastDateModified) {
        super(path, title, duration, bitRate, dateOfInclusion, lastDateModified);
    }

    protected ImmutableAudioItemBuilder(Path path, String title, Duration duration, int bitRate, Artist artist, Album album, Genre genre,
                                        String comments, short trackNumber, short discNumber, float bpm, String encoder, String encoding,
                                        LocalDateTime dateOfInclusion, LocalDateTime lastDateModified) {
        super(path, title, duration, bitRate, artist, album, genre, comments, trackNumber, discNumber, bpm, encoder, encoding, dateOfInclusion, lastDateModified);
    }

    ImmutableAudioItemBuilder(AudioItem audioItem) {
        super(audioItem);
    }

    @Override
    public AudioItem build() {
        var artistsNamesInvolved = getArtistsNamesInvolved(title, artist.name(), album.albumArtist().name());
        return new ImmutableAudioItem(path,
                                      beautifyName(),
                                      artist,
                                      artistsNamesInvolved,
                                      album,
                                      genre,
                                      comments.replaceAll("\\s+", " "),
                                      trackNumber,
                                      discNumber,
                                      bpm,
                                      duration,
                                      bitRate,
                                      encoder,
                                      encoding,
                                      dateOfInclusion,
                                      lastDateModified);
    }
}
