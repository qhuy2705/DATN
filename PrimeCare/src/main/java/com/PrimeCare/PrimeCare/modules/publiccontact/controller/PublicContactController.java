package com.PrimeCare.PrimeCare.modules.publiccontact.controller;

import com.PrimeCare.PrimeCare.modules.publiccontact.dto.request.PublicContactRequest;
import com.PrimeCare.PrimeCare.modules.publiccontact.dto.response.PublicContactResponse;
import com.PrimeCare.PrimeCare.modules.publiccontact.service.PublicContactService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/contact")
@RequiredArgsConstructor
public class PublicContactController {

    private final PublicContactService publicContactService;

    @PostMapping
    public ApiResponse<PublicContactResponse> submit(
            @Valid @RequestBody PublicContactRequest request,
            HttpServletRequest httpRequest
    ) {
        return ApiResponse.ok(
                "PrimeCare đã nhận yêu cầu hỗ trợ của bạn",
                publicContactService.submit(request, httpRequest)
        );
    }
}
