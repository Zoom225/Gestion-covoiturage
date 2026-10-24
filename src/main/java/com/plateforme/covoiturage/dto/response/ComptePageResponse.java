package com.plateforme.covoiturage.dto.response;

import java.util.List;

public record ComptePageResponse(List<CompteAdministrationResponse> content,
                                 int page, int size, long totalElements, int totalPages) {
}
