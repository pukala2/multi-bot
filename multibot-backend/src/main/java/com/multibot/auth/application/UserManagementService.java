package com.multibot.auth.application;

import com.multibot.auth.domain.model.Role;
import com.multibot.auth.domain.model.User;
import com.multibot.auth.domain.model.UserId;
import com.multibot.auth.domain.model.UserStatus;
import com.multibot.auth.domain.port.in.CreateUserUseCase;
import com.multibot.auth.domain.port.out.UserRepository;
import com.multibot.shared.exception.ConflictException;
import com.multibot.shared.organization.OrganizationId;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
class UserManagementService implements CreateUserUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    UserManagementService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public User createOwner(OrganizationId organizationId, String email, String rawPassword) {
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Użytkownik z tym emailem już istnieje: " + email);
        }
        User owner = User.newOrgUser(
                UserId.generate(),
                email,
                passwordEncoder.encode(rawPassword),
                Role.OWNER,
                organizationId,
                null,                       // createdBy — super admin, na razie pomijamy
                UserStatus.ACTIVE,
                Instant.now());
        return userRepository.save(owner);
    }
}
