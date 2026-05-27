package com.multibot.auth.domain.port.out;

import com.multibot.auth.domain.model.User;
import com.multibot.auth.domain.model.UserId;

import java.util.Optional;

public interface UserRepository {

    Optional<User> findByEmail(String email);

    Optional<User> findById(UserId id);

    User save(User user);

    boolean existsByEmail(String email);
}
