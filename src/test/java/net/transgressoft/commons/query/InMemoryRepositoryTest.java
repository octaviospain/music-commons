package net.transgressoft.commons.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static com.google.common.truth.Truth.assertThat;
import static net.transgressoft.commons.query.PersonDurationAttribute.BREATH_DURATION;
import static net.transgressoft.commons.query.PersonFloatAttribute.HEIGHT;
import static net.transgressoft.commons.query.PersonIntegerAttribute.MONEY;
import static net.transgressoft.commons.query.PersonLocalDateTimeAttribute.BIRTH_DATE;
import static net.transgressoft.commons.query.PersonPathAttribute.USER_HOME;
import static net.transgressoft.commons.query.PersonShortAttribute.INTERESTS;
import static net.transgressoft.commons.query.PersonStringAttribute.NAME;

class InMemoryRepositoryTest {

    int id = 23;
    String name = "Obi";
    float height = 1.75f;
    short numberOfInterests = 2;
    int money = 50_000;
    LocalDateTime birthDate = LocalDateTime.of(1992, Month.JANUARY, 24, 2, 30);
    Path userHome = Path.of("home", "Obi");
    Duration breathDuration = Duration.ofSeconds(80);
    String uniqueId = "23-Obi-1.75";

    PersonEntity entity = new PersonEntity(id, name, height, numberOfInterests, money, birthDate, userHome, breathDuration);

    @Test
    @DisplayName("Basic operations")
    void basicOperationsTest() {
        var repository = new PersonRepository();
        assertThat(repository.isEmpty()).isTrue();

        repository.add(entity);
        assertThat(repository.isEmpty()).isFalse();
        assertThat(repository.findById(23)).isEqualTo(Optional.of(entity));
        assertThat(repository.findByUniqueId(uniqueId)).isEqualTo(Optional.of(entity));
        assertThat(repository.findByAttribute(NAME, name)).containsExactly(entity);
        assertThat(repository.findByAttribute(HEIGHT, height)).containsExactly(entity);
        assertThat(repository.findByAttribute(INTERESTS, numberOfInterests)).containsExactly(entity);
        assertThat(repository.findByAttribute(MONEY, money)).containsExactly(entity);
        assertThat(repository.findByAttribute(BIRTH_DATE, birthDate)).containsExactly(entity);
        assertThat(repository.findByAttribute(USER_HOME, userHome)).containsExactly(entity);
        assertThat(repository.findByAttribute(BREATH_DURATION, breathDuration)).containsExactly(entity);

        repository.remove(entity);
        assertThat(repository.isEmpty()).isTrue();

        var repository2 = new PersonRepository();
        assertThat(repository).isEqualTo(repository2);

        var entity2 = new PersonEntity(24,
                                       "Leia",
                                       1.80f,
                                       (short) 5,
                                       1_000,
                                       LocalDateTime.of(2000, Month.APRIL, 1, 0, 0),
                                       Path.of("opt"),
                                       Duration.ofSeconds(40));

        repository2 = new PersonRepository(List.of(entity, entity2));
        assertThat(repository2.size()).isEqualTo(2);
        Stream.of(repository2.iterator()).map(Iterator::next)
                .forEach(personEntity -> {
                    assertThat(personEntity).isNotNull();
                    assertThat(personEntity.height).isNotNaN();
                    assertThat(personEntity.getUniqueId()).isNotEmpty();
                });

        repository2.removeAll(Set.of(entity, entity2));
        assertThat(repository2.isEmpty()).isTrue();

        entity2.name = "Obi";
        entity2.attributes.put(NAME, "Obi");
        repository2.addAll(List.of(entity, entity2));
        var obiPersons = repository2.findByAttribute(NAME, "Obi");
        assertThat(obiPersons).containsExactly(entity, entity2);
    }

    @Test
    @DisplayName("Search operations")
    void searchOperationsTest() {
        var repository = new PersonRepository(Collections.singleton(entity));
        QueryPredicate query = QueryPredicate.of(BREATH_DURATION.isShorterThan(Duration.ofSeconds(500)));
        var list = repository.search(query);
        assertThat(list).containsExactly(entity);

        query = query.and(HEIGHT.isGreaterThan(1.70f));
        list = repository.search(query);
        assertThat(list).containsExactly(entity);

        query = query.and(NAME.contains("Kenobi"));
        list = repository.search(query);
        assertThat(list).isEmpty();
    }

    @Test
    @DisplayName("Iterator operations")
    void iteratorOperationsTest() {
        // TODO concurrent operations on the repository iterator
    }

    private static class PersonRepository extends InMemoryRepository<PersonEntity> {

        public PersonRepository() {}

        public PersonRepository(Collection<PersonEntity> entities) {
            super(entities);
        }
    }
}
