package com.PrimeCare.PrimeCare.modules.publiclookup.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PublicAssistantMessageRequest {

    @NotBlank
    @Size(max = 16, message = "Vai trò tin nhắn tối đa 16 ký tự")
    private String role;

    @NotBlank
    @Size(max = 8000, message = "Nội dung lịch sử tối đa 8000 ký tự")
    private String text;
}
