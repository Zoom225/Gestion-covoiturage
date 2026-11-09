import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { CovoiturageApi } from '../../../core/services/covoiturage-api';
import { Dette, Paiement, PageResult } from '../../../shared/models/covoiturage.model';
import { Notice } from '../../../shared/components/notice';
import { Pagination } from '../../../shared/components/pagination';

@Component({
  imports: [CurrencyPipe, DatePipe, FormsModule, Notice, Pagination],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="dashboard-heading">
      <h1>Paiements et dettes</h1>
      <p>Les paiements présentés sont simulés ; aucune carte bancaire n'est demandée.</p>
    </div>
    @if (error()) {
      <app-notice tone="error" [message]="error()" />
    }
    @if (message()) {
      <app-notice tone="success" [message]="message()" />
    }
    <button type="button" class="btn btn-secondary" [disabled]="busy()" (click)="load()">
      Actualiser
    </button>
    @if (busy()) {
      <p role="status">Chargement ou règlement en cours…</p>
    }
    <section aria-labelledby="debts-title">
      <h2 id="debts-title">Mes dettes</h2>
      <p>
        Un trajet privé incomplet à J-1 entraîne une dette de 80 € pour son organisateur. Les
        nouvelles réservations restent bloquées jusqu'au règlement intégral. Aucun remboursement
        n'est accordé lors d'une annulation avec pénalité active. Les versements effectués pour
        régler une pénalité ne sont pas remboursables.
      </p>
      <div class="business-grid">
        @for (debt of debts()?.content; track debt.detteId) {
          <article class="business-card">
            <h3>Dette n° {{ debt.detteId }} · Trajet n° {{ debt.trajetId }}</h3>
            <p>
              {{ debt.statut }} · Réglé : {{ debt.regle | currency: 'EUR' }} /
              {{ debt.montant | currency: 'EUR' }}
            </p>
            @if (debt.statut === 'ACTIVE') {
              <form class="business-form" (ngSubmit)="pay(debt)">
                <label
                  >Montant à régler pour la dette n° {{ debt.detteId }}
                  <input
                    type="number"
                    [name]="'amount-' + debt.detteId"
                    [(ngModel)]="amounts[debt.detteId]"
                    min="0.01"
                    step="0.01"
                    [max]="debt.montant - debt.regle"
                    required
                  />
                </label>
                <button class="btn btn-primary" [disabled]="busy()" type="submit">
                  Régler en simulation
                </button>
              </form>
            }
          </article>
        } @empty {
          @if (!busy() && !error()) {
            <p>Aucune dette.</p>
          }
        }
      </div>
      <app-pagination
        [page]="debts()"
        [busy]="busy()"
        (change)="load(payments()?.page ?? 0, $event)"
      />
    </section>
    <section aria-labelledby="payments-title">
      <h2 id="payments-title">Historique des paiements</h2>
      <div class="business-table">
        <table>
          <caption>
            Paiements et remboursements conservés dans votre historique
          </caption>
          <thead>
            <tr>
              <th>Date</th>
              <th>Nature</th>
              <th>Montant</th>
              <th>Remboursement</th>
            </tr>
          </thead>
          <tbody>
            @for (payment of payments()?.content; track payment.nature + ':' + payment.paiementId) {
              <tr>
                <td>{{ payment.valideLe | date: 'dd/MM/yyyy HH:mm' }}</td>
                <td>{{ payment.nature }} · n° {{ payment.ressourceId }}</td>
                <td>{{ payment.montant | currency: 'EUR' }} · {{ payment.mode }}</td>
                <td>
                  @if (payment.nature === 'DETTE') {
                    Non remboursable : règlement de pénalité.
                  } @else if (payment.remboursementRefusePourPenalite) {
                    Refusé : pénalité active à l'annulation.
                  } @else {
                    {{ payment.montantRembourse | currency: 'EUR' }}
                    @if (payment.rembourseLe) {
                      le {{ payment.rembourseLe | date: 'dd/MM/yyyy HH:mm' }}
                    }
                  }
                </td>
              </tr>
            } @empty {
              <tr>
                <td colspan="4">Aucun paiement à afficher.</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      <app-pagination
        [page]="payments()"
        [busy]="busy()"
        (change)="load($event, debts()?.page ?? 0)"
      />
    </section>
  `,
})
export class PaiementsPage {
  private readonly api = inject(CovoiturageApi);
  private readonly destroyRef = inject(DestroyRef);
  private readonly paymentKeys = new Map<string, string>();
  protected readonly debts = signal<PageResult<Dette> | null>(null);
  protected readonly payments = signal<PageResult<Paiement> | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly message = signal('');
  protected readonly amounts: Record<number, number> = {};
  constructor() {
    this.load();
  }
  protected load(
    paymentPage = this.payments()?.page ?? 0,
    debtPage = this.debts()?.page ?? 0,
  ): void {
    this.error.set('');
    this.busy.set(true);
    forkJoin({ payments: this.api.payments(paymentPage), debts: this.api.debts(debtPage) })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.payments.set(result.payments);
          this.debts.set(result.debts);
          for (const debt of result.debts.content) {
            this.amounts[debt.detteId] ??= Math.round((debt.montant - debt.regle) * 100) / 100;
          }
          this.busy.set(false);
        },
        error: (error: Error) => {
          this.busy.set(false);
          this.error.set(error.message);
        },
      });
  }
  protected pay(debt: Dette): void {
    if (this.busy()) return;
    const amount = this.amounts[debt.detteId];
    if (
      !Number.isFinite(amount) ||
      amount <= 0 ||
      amount > debt.montant - debt.regle + 1e-9 ||
      Math.abs(amount * 100 - Math.round(amount * 100)) > 1e-7
    ) {
      this.error.set('Le montant doit être positif, au centime et limité au solde restant.');
      return;
    }
    const operation = debt.detteId + ':' + Math.round(amount * 100);
    const key = this.paymentKeys.get(operation) ?? crypto.randomUUID();
    this.paymentKeys.set(operation, key);
    this.busy.set(true);
    this.error.set('');
    this.message.set('');
    this.api
      .payDebt(debt.detteId, amount, key)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.paymentKeys.delete(operation);
          delete this.amounts[debt.detteId];
          this.message.set(
            'Règlement simulé enregistré. La dette est régularisée lorsque le solde est intégralement payé.',
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
