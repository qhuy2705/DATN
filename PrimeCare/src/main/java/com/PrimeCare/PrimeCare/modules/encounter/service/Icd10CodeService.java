package com.PrimeCare.PrimeCare.modules.encounter.service;

import com.PrimeCare.PrimeCare.modules.encounter.dto.response.Icd10CodeResponse;
import com.PrimeCare.PrimeCare.modules.encounter.entity.Icd10Code;
import com.PrimeCare.PrimeCare.modules.encounter.repository.Icd10CodeRepository;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class Icd10CodeService {

    private final Icd10CodeRepository icd10CodeRepository;

    @Transactional(readOnly = true)
    public PageResponse<Icd10CodeResponse> search(String q, Pageable pageable) {
        String keyword = (q == null || q.isBlank()) ? "" : q.trim();
        Page<Icd10Code> page = icd10CodeRepository.search(keyword, pageable);

        return PageResponse.<Icd10CodeResponse>builder()
                           .items(page.getContent().stream().map(this::toResponse).toList())
                           .meta(PageResponse.Meta.builder()
                                                  .page(page.getNumber())
                                                  .size(page.getSize())
                                                  .totalItems(page.getTotalElements())
                                                  .totalPages(page.getTotalPages())
                                                  .hasNext(page.hasNext())
                                                  .hasPrev(page.hasPrevious())
                                                  .sort(pageable.getSort().toString())
                                                  .build())
                           .build();
    }

    private Icd10CodeResponse toResponse(Icd10Code code) {
        return Icd10CodeResponse.builder()
                                .id(code.getId())
                                .code(code.getCode())
                                .nameVn(code.getNameVn())
                                .nameEn(code.getNameEn())
                                .category(code.getCategory())
                                .build();
    }
}
