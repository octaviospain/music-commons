package net.transgressoft.commons.query;

import net.transgressoft.commons.query.attribute.DurationAttribute;
import net.transgressoft.commons.query.attribute.EntityAttribute;
import net.transgressoft.commons.query.attribute.FloatAttribute;
import net.transgressoft.commons.query.attribute.IntegerAttribute;
import net.transgressoft.commons.query.attribute.LocalDateTimeAttribute;
import net.transgressoft.commons.query.attribute.PathAttribute;
import net.transgressoft.commons.query.attribute.ShortAttribute;
import net.transgressoft.commons.query.attribute.StringAttribute;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Objects;

public class PersonEntity implements QueryEntity {

    int id;
    String name;
    float height;
    short numberOfInterests;
    int money;
    LocalDateTime birthDate;
    Path userHome;
    Duration breathDuration;

    HashMap<EntityAttribute<?>, Object> attributes;

    public PersonEntity(int id, String name, float height, short numberOfInterests, int money, LocalDateTime birthDate,
                        Path userHome, Duration breathDuration) {
        this.id = id;
        this.name = name;
        this.height = height;
        this.numberOfInterests = numberOfInterests;
        this.money = money;
        this.birthDate = birthDate;
        this.userHome = userHome;
        this.breathDuration = breathDuration;

        attributes = new HashMap<>();
        attributes.put(PersonStringAttribute.NAME, name);
        attributes.put(PersonFloatAttribute.HEIGHT, height);
        attributes.put(PersonShortAttribute.INTERESTS, numberOfInterests);
        attributes.put(PersonIntegerAttribute.MONEY, money);
        attributes.put(PersonLocalDateTimeAttribute.BIRTH_DATE, birthDate);
        attributes.put(PersonPathAttribute.USER_HOME, userHome);
        attributes.put(PersonDurationAttribute.BREATH_DURATION, breathDuration);
    }

    @Override
    public int id() {
        return id;
    }

    @Override
    public String getUniqueId() {
        return id + "-" + name + "-" + height;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <A extends EntityAttribute<V>, V> V getAttribute(A attribute) {
        return (V) attributes.get(attribute);
    }

    @Override
    public String toString() {
        return "PersonEntity{" +
                "name='" + name + '\'' +
                ", numberOfInterests=" + numberOfInterests +
                ", money=" + money +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PersonEntity that = (PersonEntity) o;
        return id == that.id && Float.compare(that.height, height) == 0 && numberOfInterests == that.numberOfInterests && money == that.money
                && Objects.equals(name, that.name) && Objects.equals(birthDate, that.birthDate) && Objects.equals(userHome, that.userHome) &&
                Objects.equals(breathDuration, that.breathDuration) && Objects.equals(attributes, that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, height, numberOfInterests, money, birthDate, userHome, breathDuration, attributes);
    }

    @Override
    public int compareTo(@NotNull QueryEntity o) {
        return Comparator.comparing(QueryEntity::getUniqueId, String.CASE_INSENSITIVE_ORDER).compare(this, o);
    }
}

enum PersonStringAttribute implements StringAttribute {
    NAME
}

enum PersonFloatAttribute implements FloatAttribute {
    HEIGHT
}

enum PersonShortAttribute implements ShortAttribute {
    INTERESTS
}

enum PersonIntegerAttribute implements IntegerAttribute {
    MONEY
}

enum PersonLocalDateTimeAttribute implements LocalDateTimeAttribute {
    BIRTH_DATE
}

enum PersonPathAttribute implements PathAttribute {
    USER_HOME
}

enum PersonDurationAttribute implements DurationAttribute {
    BREATH_DURATION
}
