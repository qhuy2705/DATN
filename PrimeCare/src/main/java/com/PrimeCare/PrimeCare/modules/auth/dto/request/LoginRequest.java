package com.PrimeCare.PrimeCare.modules.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {
    @NotBlank(message = "identifier là bắt buộc")
    private String identifier;

    @NotBlank(message = "password là bắt buộc")
    private String password;
}
