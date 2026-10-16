package com.buildup.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordServiceTest {

    private final PasswordService passwordService =
            new PasswordService(new BCryptPasswordEncoder());

    @Test
    void matchesValidPassword() {
        String password = "BuildUp!234";
        String passwordHash = passwordService.hash(password);

        assertThat(passwordService.matches(password, passwordHash)).isTrue();
    }

    @Test
    void rejectsLoginPasswordLongerThanBcryptByteLimit() {
        String passwordHash = passwordService.hash("BuildUp!234");
        String oversizedPassword = "가".repeat(25);

        assertThat(oversizedPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .hasSizeGreaterThan(72);
        assertThat(passwordService.matches(oversizedPassword, passwordHash)).isFalse();
    }
}
