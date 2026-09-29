package com.buildup.device.service;

import com.buildup.device.entity.DeviceToken;
import com.buildup.device.repository.DeviceTokenRepository;
import com.buildup.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;

    public DeviceTokenService(DeviceTokenRepository deviceTokenRepository) {
        this.deviceTokenRepository = deviceTokenRepository;
    }

    @Transactional
    public void register(User user, String rawToken) {
        String token = rawToken.trim();
        DeviceToken deviceToken = deviceTokenRepository.findByToken(token)
                .orElseGet(() -> DeviceToken.register(token, user));
        deviceToken.assignTo(user);
        deviceTokenRepository.save(deviceToken);
    }

    @Transactional
    public void remove(User user, String rawToken) {
        deviceTokenRepository.deleteOwnedToken(rawToken.trim(), user.getId());
    }
}
