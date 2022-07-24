package net.transgressoft.commons.query;

import java.util.Map;

public interface AttributeSet<A extends EntityAttribute<?>> extends Iterable<Map.Entry<A, Object>> {

    <V> V get(A attribute, Class<V> valueClass);
}
