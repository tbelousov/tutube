package com.tbelousov.tutube.repository;

import com.tbelousov.tutube.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsById(Long id);

    @Modifying
    @Query("UPDATE User u " +
        "SET u.name = :#{#user.name}, " +
            "u.timezone = :#{#user.timezone}, " +
            "u.toneProfile = :#{#user.toneProfile}, " +
            "u.mode = :#{#user.mode} " +
        "WHERE u.id = :id")
    int updateUser(Long id, User user);
}