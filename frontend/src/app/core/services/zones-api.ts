import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { EMPTY, expand, map, reduce } from 'rxjs';
import { API_URL } from './api-url';
import { PageResponse } from '../../shared/models/page.model';
import { ZoneResponse } from '../../shared/models/zone.model';

/** Réutilisé par la recherche et le détail ; aucun cache partagé entre deux sessions. */
@Injectable({ providedIn: 'root' })
export class ZonesApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_URL);

  list(page = 0) {
    return this.http.get<PageResponse<ZoneResponse>>(`${this.base}/zones`, {
      params: { page, size: 100 },
      timeout: 15000,
    });
  }

  all() {
    return this.list().pipe(
      expand((page) => (page.page + 1 < page.totalPages ? this.list(page.page + 1) : EMPTY), 1),
      map((page) => page.content),
      reduce((zones, content) => [...zones, ...content], [] as ZoneResponse[]),
    );
  }
}
