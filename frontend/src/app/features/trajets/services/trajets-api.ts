import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { API_URL } from '../../../core/services/api-url';
import { PageResponse } from '../../../shared/models/page.model';
import { TrajetResponse, TripCriteria, TRIPS_PAGE_SIZE } from '../models/trajet.model';

@Injectable({ providedIn: 'root' })
export class TrajetsApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_URL);
  search(criteria: TripCriteria) {
    let params = new HttpParams().set('page', criteria.page).set('size', TRIPS_PAGE_SIZE);
    if (criteria.zoneId !== null) params = params.set('zoneId', criteria.zoneId);
    return this.http.get<PageResponse<TrajetResponse>>(`${this.base}/trajets`, {
      params,
      timeout: 15000,
    });
  }
  detail(id: number) {
    return this.http.get<TrajetResponse>(`${this.base}/trajets/${id}`, { timeout: 15000 });
  }
}
