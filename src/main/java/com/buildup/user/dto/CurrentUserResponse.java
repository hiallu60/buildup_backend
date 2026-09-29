package com.buildup.user.dto;

import com.buildup.user.entity.User;

public record CurrentUserResponse(
        Long id,
        String name,
        String email,
        String phone,
        String role
) {
    public static CurrentUserResponse from(User user) {
        return new CurrentUserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole()
        );
    }
}
