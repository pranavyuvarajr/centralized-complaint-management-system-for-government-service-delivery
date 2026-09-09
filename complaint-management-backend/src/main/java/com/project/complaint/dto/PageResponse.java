package com.project.complaint.dto;

import lombok.*;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PageResponse<T> {
    private List<T> data;
    private PaginationMeta pagination;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PaginationMeta {
        private int page;
        private int limit;
        private long total;
        private int totalPages;
    }
}
