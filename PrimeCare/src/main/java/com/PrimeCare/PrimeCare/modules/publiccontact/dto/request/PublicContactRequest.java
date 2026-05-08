package com.PrimeCare.PrimeCare.modules.publiccontact.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PublicContactRequest {

    @NotBlank(message = "Họ tên là bắt buộc")
    @Size(max = 255, message = "Họ tên không được vượt quá 255 ký tự")
    private String fullName;

    @Email(message = "Email không hợp lệ")
    @Size(max = 255, message = "Email không được vượt quá 255 ký tự")
    private String email;

    @Size(max = 32, message = "Số điện thoại không được vượt quá 32 ký tự")
    private String phone;

    @NotBlank(message = "Nội dung hỗ trợ là bắt buộc")
    @Size(max = 5000, message = "Nội dung không được vượt quá 5000 ký tự")
    private String message;

    @Size(max = 255, message = "Nguồn gửi không được vượt quá 255 ký tự")
    private String sourcePage;
}
