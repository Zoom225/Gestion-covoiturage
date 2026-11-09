import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth-service';
import { CovoiturageApi } from '../../../core/services/covoiturage-api';
import { Trajet } from '../../../shared/models/covoiturage.model';
import { Notice } from '../../../shared/components/notice';
import { TripDatePipe } from '../../../shared/pipes/trip-date.pipe';

@Component({
  imports: [FormsModule, RouterLink, Notice, TripDatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="dashboard-heading"><h1>Détail du trajet</h1></div>
    @if (error()) {
      <app-notice tone="error" [message]="error()" />
    }
    @if (message()) {
      <app-notice tone="success" [message]="message()" />
    }
    @if (trip(); as item) {
      <article class="business-card">
        <h2>
          {{ item.origine || 'Départ non renseigné' }} →
          {{ item.destination || 'Arrivée non renseignée' }}
        </h2>
        <dl class="business-details">
          <div>
            <dt>Départ</dt>
            <dd>{{ item.depart | tripDate: item.fuseauHoraire }}</dd>
          </div>
          <div>
            <dt>Arrivée</dt>
            <dd>{{ item.arrivee | tripDate: item.fuseauHoraire }}</dd>
          </div>
          <div>
            <dt>Fuseau horaire</dt>
            <dd>{{ item.fuseauHoraire }}</dd>
          </div>
          <div>
            <dt>État</dt>
            <dd>{{ item.statut }} · {{ item.type }}</dd>
          </div>
          <div>
            <dt>Véhicule</dt>
            <dd>N° {{ item.vehiculeId }}</dd>
          </div>
        </dl>
        <p>20 € par passager · 4 places passagers, chauffeur en plus.</p>
        <p>
          Une demande en attente ne garantit pas une place. La confirmation intervient après
          paiement.
        </p>
        @if (item.verificationJ1Requise) {
          <app-notice
            message="Une vérification administrative de l'historique J-1 est nécessaire."
          />
        }
        <div class="business-actions">
          @if (
            item.type === 'PUBLIC' &&
            item.statut === 'PLANIFIE' &&
            item.organisateurId !== auth.user()?.clientId
          ) {
            <button class="btn btn-primary" type="button" [disabled]="busy()" (click)="reserve()">
              Demander ma réservation
            </button>
          }
          @if (item.organisateurId === auth.user()?.clientId && item.statut === 'PLANIFIE') {
            <a [routerLink]="['/tableau-de-bord/trajets', item.trajetId, 'modifier']"
              >Modifier mon trajet</a
            >
          }
          <a routerLink="/tableau-de-bord/reservations">Mes réservations</a>
          <a routerLink="/tableau-de-bord/trajets">Retour aux trajets</a>
        </div>
        @if (
          item.type === 'PRIVE' &&
          item.statut === 'PLANIFIE' &&
          item.organisateurId === auth.user()?.clientId
        ) {
          <form class="business-form" (ngSubmit)="addParticipant()">
            <h3>Ajouter un passager au trajet privé</h3>
            <label
              >Identifiant client du participant
              <input
                type="number"
                name="clientId"
                [(ngModel)]="clientId"
                min="1"
                step="1"
                required
              />
            </label>
            <p>
              Utilisez l'identifiant client communiqué par le participant. Son paiement reste
              personnel.
            </p>
            <button class="btn btn-secondary" [disabled]="busy()" type="submit">
              Ajouter le participant
            </button>
          </form>
        }
      </article>
    } @else if (!error()) {
      <p role="status">Chargement du trajet…</p>
    }
  `,
})
export class TrajetDetailPage {
  private readonly api = inject(CovoiturageApi);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly id = Number(inject(ActivatedRoute).snapshot.paramMap.get('id'));
  protected readonly auth = inject(AuthService);
  protected readonly trip = signal<Trajet | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly message = signal('');
  protected clientId: number | null = null;
  constructor() {
    this.api
      .trip(this.id)
      .pipe(takeUntilDestroyed())
      .subscribe({
        next: (trip) => this.trip.set(trip),
        error: (error: Error) => this.error.set(error.message),
      });
  }
  protected reserve(): void {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    this.api
      .reserve(this.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => void this.router.navigateByUrl('/tableau-de-bord/reservations'),
        error: (error: Error) => {
          this.busy.set(false);
          this.error.set(error.message);
        },
      });
  }
  protected addParticipant(): void {
    if (this.busy()) return;
    if (!this.clientId || !Number.isSafeInteger(this.clientId) || this.clientId < 1) {
      this.error.set('Saisissez un identifiant client entier positif.');
      return;
    }
    this.busy.set(true);
    this.error.set('');
    this.message.set('');
    this.api
      .addParticipant(this.id, this.clientId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.clientId = null;
          this.message.set(
            'Participant ajouté en attente. Il peut payer sa place depuis son compte.',
          );
        },
        error: (error: Error) => {
          this.busy.set(false);
          this.error.set(error.message);
        },
      });
  }
}
