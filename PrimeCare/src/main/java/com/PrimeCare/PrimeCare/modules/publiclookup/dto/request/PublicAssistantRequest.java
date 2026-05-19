package com.PrimeCare.PrimeCare.modules.publiclookup.dto.request;

import com.PrimeCare.PrimeCare.modules.publiclookup.dto.assistant.AiConversationContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class PublicAssistantRequest {
    @NotBlank(message = "Câu hỏi không được để trống")
    @Size(max = 2000, message = "Câu hỏi tối đa 2000 ký tự")
    private String question;
    private String conversationId;
    private String locale;

    @Size(max = 256, message = "Đường dẫn trang tối đa 256 ký tự")
    private String pagePath;

    @Size(max = 200, message = "Tiêu đề trang tối đa 200 ký tự")
    private String pageTitle;

    @Valid
    @Size(max = 20, message = "Lịch sử hỏi đáp tối đa 20 tin nhắn")
    private List<PublicAssistantMessageRequest> history;

    @Valid
    private AiConversationContext context;

    @Size(max = 64, message = "Loại hành động tối đa 64 ký tự")
    private String actionType;

    private Map<String, Object> actionPayload;
}
