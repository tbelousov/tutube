package com.tbelousov.tutube.repository;

import com.tbelousov.tutube.entity.User;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

@Repository
public class UserRepository implements ClearableRepository {

    private final Map<Long, User> storage = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public User save(User user) {
        var id = user.getId();
        if (id == null) id = idGenerator.getAndIncrement();

        final var finalId = id;
        return storage.compute(finalId, (k, v) ->
                User.builder()
                        .id(finalId)
                        .name(user.getName())
                        .timezone(user.getTimezone())
                        .toneProfile(user.getToneProfile())
                        .mode(user.getMode())
                        .build()
        );
    }

    public Optional<User> findById(Long id) {
        return Optional.ofNullable(storage.get(id));
    }

    public Stream<User> streamAll() {
        return storage.values().stream();
    }

    public boolean existsById(Long id) {
        return storage.containsKey(id);
    }

    public int updateUser(Long id, User user) {
        var result = new AtomicInteger(0);
        storage.compute(id, (k, existing) -> {
            if (existing == null) return null;

            result.set(1);
            return User.builder()
                    .id(id)
                    .name(user.getName() != null ? user.getName() : existing.getName())
                    .timezone(user.getTimezone() != null ? user.getTimezone() : existing.getTimezone())
                    .toneProfile(user.getToneProfile() != null ? user.getToneProfile() : existing.getToneProfile())
                    .mode(user.getMode() != null ? user.getMode() : existing.getMode())
                    .build();
        });
        return result.get();
    }

    public void deleteById(Long id) {
        storage.remove(id);
    }

    @Override
    public void clear() {
        storage.clear();
        idGenerator.set(1);
    }

    public long count() {
        return storage.size();
    }
}