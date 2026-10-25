package com.plateforme.covoiturage.dto.response;

import org.springframework.data.domain.Page;
import java.util.List;

public record ReferencePageResponse<T>(List<T> content,int page,int size,long totalElements,int totalPages) {
    public static <T> ReferencePageResponse<T> from(Page<T> page) {
        return new ReferencePageResponse<>(page.getContent(),page.getNumber(),page.getSize(),
                page.getTotalElements(),page.getTotalPages());
    }
}
