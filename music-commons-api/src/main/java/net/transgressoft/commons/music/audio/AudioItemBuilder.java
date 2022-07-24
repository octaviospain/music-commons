package net.transgressoft.commons.music.audio;

public interface AudioItemBuilder<E extends AudioItem> {

    E build();

    AudioItemBuilder<AudioItem> artist(Artist artist);

    AudioItemBuilder<AudioItem> album(Album album);

    AudioItemBuilder<AudioItem> genre(Genre genre);

    AudioItemBuilder<AudioItem> comments(String comments);

    AudioItemBuilder<AudioItem> trackNumber(short trackNumber);

    AudioItemBuilder<AudioItem> discNumber(short discNumber);

    AudioItemBuilder<AudioItem> bpm(float bpm);

    AudioItemBuilder<AudioItem> encoder(String encoder);

    AudioItemBuilder<AudioItem> encoding(String encoding);

    AudioItemBuilder<AudioItem> playCount(short playCount);
}
