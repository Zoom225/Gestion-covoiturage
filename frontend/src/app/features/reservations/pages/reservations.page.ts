import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { CovoiturageApi } from '../../../core/services/covoiturage-api';
import { PageResult, Reservation } from '../../../shared/models/covoiturage.model';
import { Notice } from '../../../shared/components/notice';
import { Pagination } from '../../../shared/components/pagination';

@Component({
  imports: [RouterLink, Notice, Pagination],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="dashboard-heading">
      <h1>Mes réservations</h1>
      <p>
        Chaque place coûte 20 €. Le paiement confirme la place, dans la limite de quatre passagers.
      </p>
    </div>
    @if (error()) {
      <app-notice tone="error" [message]="error()" />
    }
    @if (message()) {
      <app-notice tone="success" [message]="message()" />
    }
    <div class="business-actions">
      <a routerLink="/tableau-de-bord/trajets">Rechercher un trajet</a>
      <button class="btn btn-secondary" type="button" [disabled]="busy()" (click)="load()">
        Actualiser
      </button>
    </div>
    @if (busy()) {
      <p role="status">Opération en cours…</p>
    }
    <div class="business-grid" [attr.aria-busy]="busy()">
      @for (booking of result()?.content; track booking.reservationId) {
        <article class="business-card">
          <h2>Réservation n° {{ booking.reservationId }}</h2>
          <p>{{ booking.statut }} {{ booking.organisateur ? '· Organisateur' : '' }}</p>
          @if (booking.annuleAvecPenalite) {
            <p>Aucun remboursement : une pénalité était active au moment de cette annulation.</p>
          }
          <a [routerLink]="['/tableau-de-bord/trajets', booking.trajetId]"
            >Trajet n° {{ booking.trajetId }}</a
          >
          <div class="business-actions">
            @if (booking.statut === 'EN_ATTENTE') {
              <button
                class="btn btn-primary"
                type="button"
                [disabled]="busy()"
                (click)="pay(booking.reservationId)"
              >
                Payer 20 € en simulation
              </button>
            }
            @if (booking.statut !== 'ANNULEE') {
              <button
                class="btn btn-secondary"
                type="button"
                [disabled]="busy()"
                (click)="cancelId.set(booking.reservationId)"
              >
                Annuler cette réservation
              </button>
            }
          </div>
          @if (cancelId() === booking.reservationId) {
            <div class="business-confirm">
              <p>
                Confirmer l'annulation ? Elle est possible avant le départ. Aucun remboursement
                n'est accordé si vous avez une pénalité active à l'annulation, même si vous réglez
                ensuite cette pénalité. Sans pénalité, une place payée est remboursée à H-24 ou
                avant ; à moins de 24 heures, le montant reste acquis.
              </p>
              <button
                class="btn btn-secondary"
                type="button"
                [disabled]="busy()"
                (click)="cancel(booking.reservationId)"
              >
                Confirmer l'annulation
              </button>
              <button
                class="text-link"
                type="button"
                [disabled]="busy()"
                (click)="cancelId.set(null)"
              >
                Conserver ma réservation
              </button>
            </div>
          }
        </article>
      } @empty {
        @if (!busy() && !error()) {
          <p>Vous n'avez aucune réservation.</p>
        }
      }
    </div>
    <app-pagination [page]="result()" [busy]="busy()" (change)="load($event)" />
  `,
})
export class ReservationsPage {
  private readonly api = inject(CovoiturageApi);
  private readonly destroyRef = inject(DestroyRef);
  // Conserver la clé après une erreur réseau permet de reprendre sans payer deux fois.
  private readonly paymentKeys = new Map<number, string>();
  protected readonly result = signal<PageResult<Reservation> | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly message = signal('');
  protected readonly cancelId = signal<number | null>(null);
  constructor() {
    this.load();
  }
  protected load(page = this.result()?.page ?? 0): void {
    this.busy.set(true);
    this.error.set('');
    this.api
      .reservations(page)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.result.set(result);
          this.busy.set(false);
        },
        error: (error: Error) => {
          this.result.set(null);
          this.busy.set(false);
          this.error.set(error.message);
        },
      });
  }
  protected pay(id: number): void {
    if (this.busy()) return;
    const key = this.paymentKeys.get(id) ?? crypto.randomUUID();
    this.paymentKeys.set(id, key);
    this.busy.set(true);
    this.error.set('');
    this.message.set('');
    this.api
      .paySeat(id, key)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.paymentKeys.delete(id);
          this.message.set('Paiement simulé validé : votre place est confirmée.');
          this.load();
        },
        error: (error: Error) => {
          this.busy.set(false);
          this.error.set(error.message);
        },
      });
  }
  protected cancel(id: number): void {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    this.message.set('');
    this.api
      .cancel(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (booking) => {
          this.cancelId.set(null);
          this.message.set(
            booking.annuleAvecPenalite
              ? 'Réservation annulée sans remboursement : une pénalité était active au moment de l’annulation.'
              : 'Réservation annulée. Consultez vos paiements pour le résultat du remboursement selon le délai de 24 heures.',
          );
          this.load();
        },
        error: (error: Error) => {
          this.busy.set(false);
          this.error.set(error.message);
        },
      });
  }
}
