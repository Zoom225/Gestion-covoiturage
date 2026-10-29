/** Contrat ReferencePageResponse<T> du backend ; pagination appliquée côté serveur. */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
