package com.PrimeCare.PrimeCare.modules.cms.faq.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PublicFaqResponse {
    private Long id;
    private String category;
    private String questionVn;
    private String questionEn;
    private String answerVn;
    private String answerEn;
}
