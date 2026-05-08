package com.PrimeCare.PrimeCare.shared.common;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageResponse<T> {

    private List<T> items;
    private Meta meta;

    @Getter @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Meta {
        private int page;
        private int size;
        private long totalItems;
        private int totalPages;
        private boolean hasNext;
        private boolean hasPrev;
        private String sort;
    }
}
