package net.transgressoft.commons.music.audio;



import net.transgressoft.commons.query.QueryEvent;
import net.transgressoft.commons.query.QueryEventDispatcherBase;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Flow.Subscriber;

public class DefaultAudioItemEventDispatcher extends QueryEventDispatcherBase<AudioItem> implements AudioItemEventDispatcher<AudioItem> {

    private final Set<Subscriber<QueryEvent<? extends AudioItem>>> audioItemSubscribers = new HashSet<>();

    public DefaultAudioItemEventDispatcher() {
    }
}
