import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { CovoiturageApi } from '../../../core/services/covoiturage-api';
import { PageResult, Trajet, Zone } from '../../../shared/models/covoiturage.model';
import { Notice } from '../../../shared/components/notice';
import { Pagination } from '../../../shared/components/pagination';
import { TripDatePipe } from '../../../shared/pipes/trip-date.pipe';

@Component({
  imports: [FormsModule, RouterLink, Notice, Pagination, TripDatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="dashboard-heading">
      <h1>Trajets accessibles</h1>
      <p>4 passagers, organisateur compris, et un chauffeur fourni par la flotte.</p>
    </div>
    <div class="business-actions">
      <a class="btn btn-primary" routerLink="/tableau-de-bord/trajets/nouveau">Créer un trajet</a>
      <label
        >Filtrer par zone
        <select [ngModel]="zoneId" (ngModelChange)="zoneId = $event; load(0)" [disabled]="busy()">
          <option [ngValue]="undefined">Toutes mes zones</option>
          @for (zone of zones(); track zone.zoneId) {
            <option [ngValue]="zone.zoneId">{{ zone.nom }}</option>
          }
        </select>
      </label>
      <button type="button" class="btn btn-secondary" [disabled]="busy()" (click)="load()">
        Actualiser
      </button>
    </div>
    @if (error()) {
      <app-notice tone="error" [message]="error()" />
    }
    @if (busy()) {
      <p role="status">Chargement des trajets…</p>
    }
    <div class="business-grid" [attr.aria-busy]="busy()">
      @for (trip of result()?.content; track trip.trajetId) {
        <article class="business-card">
          <h2>
            {{ trip.origine || 'Départ non renseigné' }} →
            {{ trip.destination || 'Arrivée non renseignée' }}
          </h2>
          <p>{{ trip.depart | tripDate: trip.fuseauHoraire }} · {{ trip.fuseauHoraire }}</p>
          <p>
            {{ trip.type === 'PRIVE' ? 'Privé' : 'Public' }} · {{ trip.statut }} · 20 € par passager
          </p>
          <a [routerLink]="['/tableau-de-bord/trajets', trip.trajetId]"
            >Consulter le trajet n° {{ trip.trajetId }}</a
          >
        </article>
      } @empty {
        @if (!busy() && !error()) {
          <p>Aucun trajet accessible dans cette sélection.</p>
        }
      }
    </div>
    <app-pagination [page]="result()" [busy]="busy()" (change)="load($event)" />
  `,
})
export class TrajetsPage {
  private readonly api = inject(CovoiturageApi);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly zones = signal<Zone[]>([]);
  protected readonly result = signal<PageResult<Trajet> | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected zoneId: number | undefined;
  constructor() {
    this.api
      .zones()
      .pipe(takeUntilDestroyed())
      .subscribe({
        next: (zones) => this.zones.set(zones),
        error: (error: Error) => this.error.set(error.message),
      });
    this.load();
  }
  protected load(page = this.result()?.page ?? 0): void {
    this.error.set('');
    this.busy.set(true);
    this.api
      .trips(page, this.zoneId)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: (result) => this.result.set(result),
        error: (error: Error) => {
          this.result.set(null);
          this.error.set(error.message);
        },
      });
  }
}
