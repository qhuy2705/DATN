package com.PrimeCare.PrimeCare.modules.publiclookup.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class PublicAssistantRequest {
    @NotBlank(message = "Câu hỏi không được để trống")
    @Size(max = 1000, message = "Câu hỏi tối đa 1000 ký tự")
    private String question;
    private String locale;

    @Size(max = 256, message = "Đường dẫn trang tối đa 256 ký tự")
    private String pagePath;

    @Size(max = 200, message = "Tiêu đề trang tối đa 200 ký tự")
    private String pageTitle;

    @Valid
    @Size(max = 8, message = "Lịch sử hỏi đáp tối đa 8 tin nhắn")
    private List<PublicAssistantMessageRequest> history;
}
