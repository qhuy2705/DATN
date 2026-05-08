package com.PrimeCare.PrimeCare.modules.cms.faq.controller;

import com.PrimeCare.PrimeCare.modules.cms.faq.dto.response.PublicFaqResponse;
import com.PrimeCare.PrimeCare.modules.cms.faq.service.PublicFaqService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public/faqs")
@RequiredArgsConstructor
public class PublicFaqController {
    private final PublicFaqService service;

    @GetMapping
    public ApiResponse<List<PublicFaqResponse>> list() {
        return ApiResponse.ok("OK", service.listPublished());
    }
}
