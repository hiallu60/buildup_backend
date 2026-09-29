package com.buildup.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank
        @Size(max = 50)
        String name,

        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @Size(max = 30)
        String phone,

        @NotBlank
        @Size(min = 10, max = 72)
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{10,72}$",
                message = "비밀번호는 대문자, 소문자, 숫자, 특수문자를 각각 1개 이상 포함하고 10자 이상이어야 합니다."
        )
        String password
) {
}
