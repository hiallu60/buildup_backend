package com.buildup.device.service;

import com.buildup.device.repository.DeviceTokenRepository;
import com.buildup.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
public class DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;

    public DeviceTokenService(DeviceTokenRepository deviceTokenRepository) {
        this.deviceTokenRepository = deviceTokenRepository;
    }

    @Transactional
    public void register(User user, String rawToken) {
        String token = rawToken.trim();
        deviceTokenRepository.upsert(token, user.getId(), databaseTimestamp());
    }

    @Transactional
    public void remove(User user, String rawToken) {
        deviceTokenRepository.deleteOwnedToken(rawToken.trim(), user.getId());
    }

    private LocalDateTime databaseTimestamp() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }
}
