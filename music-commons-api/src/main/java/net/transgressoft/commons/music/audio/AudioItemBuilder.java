package net.transgressoft.commons.music.audio;

public interface AudioItemBuilder<E extends AudioItem> {

    E build();

    AudioItemBuilder<E> artist(Artist artist);

    AudioItemBuilder<E> album(Album album);

    AudioItemBuilder<E> genre(Genre genre);

    AudioItemBuilder<E> comments(String comments);

    AudioItemBuilder<E> trackNumber(short trackNumber);

    AudioItemBuilder<E> discNumber(short discNumber);

    AudioItemBuilder<E> bpm(float bpm);

    AudioItemBuilder<E> encoder(String encoder);

    AudioItemBuilder<E> encoding(String encoding);

    AudioItemBuilder<E> playCount(short playCount);
}
