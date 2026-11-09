import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map, Observable, of, switchMap } from 'rxjs';
import { API_URL } from './api-url';
import {
  Compte,
  Dette,
  PageResult,
  Paiement,
  Reservation,
  Trajet,
  TrajetRequest,
  Vehicule,
  Zone,
} from '../../shared/models/covoiturage.model';
import { ClientCategory } from '../../shared/models/auth.model';

@Injectable({ providedIn: 'root' })
export class CovoiturageApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_URL);
  private page<T>(path: string, page: number, params: Record<string, string | number> = {}) {
    return this.http.get<PageResult<T>>(`${this.base}/${path}`, {
      params: { page, size: 10, ...params },
      timeout: 15000,
    });
  }
  // Les sélecteurs parcourent toutes les pages : aucune zone autorisée n'est masquée.
  private references<T>(
    path: string,
    params: Record<string, number> = {},
    page = 0,
  ): Observable<T[]> {
    return this.page<T>(path, page, { ...params, size: 100 }).pipe(
      switchMap((result) =>
        result.page + 1 < result.totalPages
          ? this.references<T>(path, params, page + 1).pipe(
              map((rest) => [...result.content, ...rest]),
            )
          : of(result.content),
      ),
    );
  }
  zones(admin = false) {
    return this.references<Zone>(admin ? 'admin/zones' : 'zones');
  }
  vehicles(zoneId: number) {
    return this.references<Vehicule>('vehicules', { zoneId });
  }
  trips(page = 0, zoneId?: number) {
    return this.page<Trajet>('trajets', page, zoneId ? { zoneId } : {});
  }
  trip(id: number) {
    return this.http.get<Trajet>(`${this.base}/trajets/${id}`, { timeout: 15000 });
  }
  saveTrip(body: TrajetRequest, id?: number) {
    if (id) {
      const { zoneId, type, ...update } = body;
      return this.http.put<Trajet>(`${this.base}/trajets/${id}`, update, { timeout: 15000 });
    }
    return this.http.post<Trajet>(`${this.base}/trajets`, body, { timeout: 15000 });
  }
  reserve(id: number) {
    return this.http.post<Reservation>(
      `${this.base}/trajets/${id}/reservations`,
      {},
      { timeout: 15000 },
    );
  }
  addParticipant(id: number, clientId: number) {
    return this.http.post<Reservation>(
      `${this.base}/trajets/${id}/participants`,
      { clientId },
      { timeout: 15000 },
    );
  }
  reservations(page = 0) {
    return this.page<Reservation>('reservations/me', page);
  }
  cancel(id: number) {
    return this.http.post<Reservation>(
      `${this.base}/reservations/${id}/annulation`,
      {},
      { timeout: 15000 },
    );
  }
  paySeat(id: number, key: string) {
    return this.http.post<Paiement>(
      `${this.base}/reservations/${id}/paiements`,
      {},
      {
        headers: { 'Idempotency-Key': key },
        timeout: 15000,
      },
    );
  }
  payments(page = 0) {
    return this.page<Paiement>('paiements/me', page);
  }
  debts(page = 0) {
    return this.page<Dette>('dettes/me', page);
  }
  payDebt(id: number, montant: number, key: string) {
    return this.http.post<Paiement>(
      `${this.base}/dettes/${id}/paiements`,
      { montant },
      {
        headers: { 'Idempotency-Key': key },
        timeout: 15000,
      },
    );
  }
  accounts(page = 0) {
    return this.page<Compte>('admin/comptes', page);
  }
  createClient(email: string, categorie: ClientCategory, zoneClientId: number | null) {
    return this.http.post<unknown>(
      `${this.base}/admin/clients`,
      { email, categorie, zoneClientId },
      { timeout: 15000 },
    );
  }
  renewInvitation(id: number) {
    return this.http.post<unknown>(
      `${this.base}/admin/comptes/${id}/invitations`,
      {},
      { timeout: 15000 },
    );
  }
}
