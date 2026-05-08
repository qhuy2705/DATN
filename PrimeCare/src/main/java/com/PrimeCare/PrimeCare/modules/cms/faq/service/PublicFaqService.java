package com.PrimeCare.PrimeCare.modules.cms.faq.service;

import com.PrimeCare.PrimeCare.modules.cms.faq.dto.response.PublicFaqResponse;
import com.PrimeCare.PrimeCare.modules.cms.faq.repository.FaqRepository;
import com.PrimeCare.PrimeCare.shared.enums.FaqStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PublicFaqService {
    private final FaqRepository faqRepository;

    @Transactional(readOnly = true)
    public List<PublicFaqResponse> listPublished() {
        return faqRepository.findTop12ByStatusOrderByCreatedAtDesc(FaqStatus.PUBLISHED)
                .stream()
                .map(faq -> PublicFaqResponse.builder()
                        .id(faq.getId())
                        .category(faq.getCategory())
                        .questionVn(faq.getQuestionVn())
                        .questionEn(faq.getQuestionEn())
                        .answerVn(faq.getAnswerVn())
                        .answerEn(faq.getAnswerEn())
                        .build())
                .toList();
    }
}
