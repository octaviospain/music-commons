package net.transgressoft.commons.music.playlist;

import net.transgressoft.commons.music.audio.AudioItem;
import net.transgressoft.commons.query.BooleanQueryTerm;
import net.transgressoft.commons.query.QueryEntity;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface AudioPlaylist<I extends AudioItem> extends QueryEntity, Comparable<AudioPlaylist<I>>  {

    String getName();

    List<I> audioItems();

    boolean isDirectory();

    boolean audioItemsAllMatch(BooleanQueryTerm<AudioItem> queryPredicate);

    boolean audioItemsAnyMatch(BooleanQueryTerm<AudioItem> queryPredicate);

    void exportToM3uFile(Path destinationPath) throws IOException;
}
