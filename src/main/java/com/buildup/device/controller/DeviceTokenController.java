package com.buildup.device.controller;

import com.buildup.device.dto.DeviceTokenRequest;
import com.buildup.device.service.DeviceTokenService;
import com.buildup.user.entity.User;
import com.buildup.user.service.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/devices/tokens")
public class DeviceTokenController {

    private final CurrentUserService currentUserService;
    private final DeviceTokenService deviceTokenService;

    public DeviceTokenController(
            CurrentUserService currentUserService,
            DeviceTokenService deviceTokenService
    ) {
        this.currentUserService = currentUserService;
        this.deviceTokenService = deviceTokenService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody DeviceTokenRequest request
    ) {
        User user = currentUserService.require(jwt);
        deviceTokenService.register(user, request.token());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody DeviceTokenRequest request
    ) {
        User user = currentUserService.require(jwt);
        deviceTokenService.remove(user, request.token());
    }
}
