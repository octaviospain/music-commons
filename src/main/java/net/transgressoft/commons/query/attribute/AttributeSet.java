package net.transgressoft.commons.query.attribute;

import java.util.HashMap;
import java.util.Map;

public final class AttributeSet {

    private final Map<EntityAttribute<?>, Object> attributes;

    public AttributeSet(Map<EntityAttribute<?>, Object> attributes) {
        this.attributes = attributes;
    }

    public static AttributeSet of(Map.Entry<EntityAttribute<?>, Object>... attributes) {
        return new AttributeSet(Map.ofEntries(attributes));
    }

    public static AttributeSet of(EntityAttribute<?> k1, Object v1) {
        return new AttributeSet(Map.of(k1, v1));
    }

    public static AttributeSet of(EntityAttribute<?> k1, Object v1, EntityAttribute<?> k2, Object v2) {
        return new AttributeSet(Map.of(k1, v1, k2, v2));
    }

    public Object get(EntityAttribute<?> key) {
        return attributes.get(key);
    }

    public AttributeSet put(EntityAttribute<?> key, Object value) {
        Map<EntityAttribute<?>, Object> map = new HashMap<>(attributes);
        map.put(key, value);
        return new AttributeSet(map);
    }
}
