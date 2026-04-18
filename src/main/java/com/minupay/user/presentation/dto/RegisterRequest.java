package com.minupay.user.presentation.dto;

import com.minupay.user.application.dto.RegisterCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 2, max = 20) String name,
        @NotBlank @Size(min = 8) String password
) {
    public RegisterCommand toCommand() {
        return new RegisterCommand(email, name, password);
    }
}
