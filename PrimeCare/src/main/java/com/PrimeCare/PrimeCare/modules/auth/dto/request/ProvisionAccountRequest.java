package com.PrimeCare.PrimeCare.modules.auth.dto.request;

import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProvisionAccountRequest {
    private String email;
    private String phone;
    private UserRole role;
    private String deliveryChannel;
}
