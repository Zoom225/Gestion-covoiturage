import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { PaiementsApiService } from '../../../core/api/paiements-api.service';
import { VoyageurSessionService } from '../../../core/auth/voyageur-session.service';
import { LanguageService } from '../../../core/i18n/language.service';
import { PaiementResponse } from '../../../shared/models/reservation.model';
import { extractApiErrorMessage } from '../../../shared/utils/api-error.util';

@Component({
  selector: 'app-voyageur-payments-page',
  standalone: true,
  imports: [CommonModule, RouterLink, MatProgressSpinnerModule],
  template: `
    <div class="pay-hero">
      <div class="pay-hero-inner">
        <div class="pay-hero-left">
          <span class="pay-hero-icon" aria-hidden="true">
            <svg class="ui-icon"><use href="images/ui-icons.svg#payment" /></svg>
          </span>
          <div>
            <h1 class="pay-hero-title">{{ language.t('voyageur.payments.title') }}</h1>
            <p class="pay-hero-sub">{{ language.t('voyageur.payments.subtitle') }}</p>
          </div>
        </div>
        <a routerLink="/voyageur/reservations" class="pay-hero-link">
          {{ language.t('voyageur.payments.reservations') }}
        </a>
      </div>
    </div>

    <section class="page-shell max-w-5xl">
      @if (loading()) {
        <div class="flex justify-center py-8">
          <mat-spinner
            [attr.aria-label]="language.t('transport.searching')"
            diameter="32"
          ></mat-spinner>
        </div>
      }
      @if (errorMessage()) {
        <p role="alert" class="status-error">{{ errorMessage() }}</p>
      }

      <div class="pay-kpi-card">
        <div class="pay-kpi-grid">
          <div class="pay-kpi-item">
            <p class="pay-kpi-label">{{ language.t('voyageur.payments.total') }}</p>
            <p class="pay-kpi-value">{{ payments().length }}</p>
          </div>
          <div class="pay-kpi-item">
            <p class="pay-kpi-label">{{ language.t('voyageur.payments.paid') }}</p>
            <p class="pay-kpi-value">{{ totalPaid() }} €</p>
          </div>
          <div class="pay-kpi-item">
            <p class="pay-kpi-label">{{ language.t('voyageur.payments.pending') }}</p>
            <p class="pay-kpi-value">{{ pendingCount() }}</p>
          </div>
          <div class="pay-kpi-item">
            <p class="pay-kpi-label">{{ language.t('voyageur.payments.refunded') }}</p>
            <p class="pay-kpi-value">{{ refundedCount() }}</p>
          </div>
        </div>
      </div>

      <div class="grid gap-4 md:grid-cols-2">
        @for (payment of payments(); track payment.id) {
          <div class="pay-card">
            <div class="pay-card-header">
              <div class="pay-card-id">
                {{ language.t('voyageur.payments.payment') }} #{{ payment.id }}
              </div>
              <div class="pay-card-date">
                {{ payment.datePaiement || language.t('voyageur.payments.notSet') }}
              </div>
            </div>
            <div class="pay-card-body">
              <span class="pay-amount">{{ payment.montant }} €</span>
              <span class="pay-badge" [class]="payBadgeClass(payment.statut)">{{
                paymentStatusLabel(payment.statut)
              }}</span>
            </div>
          </div>
        } @empty {
          @if (!loading()) {
            <div class="pay-empty md:col-span-2">
              <span class="pay-empty-icon" aria-hidden="true">
                <svg class="ui-icon"><use href="images/ui-icons.svg#payment" /></svg>
              </span>
              <p>{{ language.t('voyageur.payments.none') }}</p>
            </div>
          }
        }
      </div>
    </section>

    <style>
      .pay-hero {
        background: linear-gradient(120deg, var(--ui-navy), var(--ui-primary-strong));
        padding: 2rem 2.25rem;
        box-shadow: 0 4px 20px rgba(11, 31, 51, 0.25);
      }
      .pay-hero-inner {
        display: flex;
        align-items: center;
        justify-content: space-between;
        max-width: 1200px;
        margin: 0 auto;
        gap: 1.5rem;
        flex-wrap: wrap;
      }
      .pay-hero-left {
        display: flex;
        align-items: center;
        gap: 1.25rem;
      }
      .pay-hero-icon {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 3.5rem;
        height: 3.5rem;
        border-radius: 1rem;
        background: rgba(255, 255, 255, 0.15);
        border: 1px solid rgba(255, 255, 255, 0.25);
        color: #ffffff;
        flex-shrink: 0;
      }
      .pay-hero-icon .ui-icon {
        width: 2rem;
        height: 2rem;
        fill: currentColor;
      }
      .pay-hero-title {
        font-size: 1.85rem;
        font-weight: 800;
        color: #ffffff !important;
        margin: 0;
        letter-spacing: -0.02em;
        line-height: 1.2;
      }
      .pay-hero-sub {
        color: rgba(255, 255, 255, 0.88) !important;
        font-size: 0.95rem;
        margin: 0.35rem 0 0;
      }
      .pay-hero-link {
        display: inline-flex;
        align-items: center;
        gap: 0.5rem;
        background: rgba(255, 255, 255, 0.15);
        color: #ffffff !important;
        border: 1.5px solid rgba(255, 255, 255, 0.4);
        border-radius: 9999px;
        padding: 0.55rem 1.35rem;
        font-weight: 700;
        font-size: 0.875rem;
        text-decoration: none;
        transition: all 0.2s ease;
        white-space: nowrap;
      }
      .pay-hero-link:hover {
        background: rgba(255, 255, 255, 0.28);
        border-color: #ffffff;
        transform: translateY(-1px);
      }

      .pay-kpi-card {
        background: var(--ui-surface);
        border: 1.5px solid var(--ui-border);
        border-radius: 1.25rem;
        padding: 1.5rem;
        box-shadow: 0 4px 16px rgba(0, 0, 0, 0.05);
      }
      .pay-kpi-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
        gap: 1.5rem;
      }
      .pay-kpi-item {
        text-align: center;
      }
      .pay-kpi-label {
        font-size: 0.8rem;
        color: var(--ui-text-secondary);
        font-weight: 700;
        text-transform: uppercase;
        letter-spacing: 0.05em;
        margin: 0 0 0.35rem;
      }
      .pay-kpi-value {
        font-size: 1.85rem;
        font-weight: 800;
        color: var(--ui-navy);
        margin: 0;
      }

      .pay-card {
        background: var(--ui-surface);
        border-radius: 1.25rem;
        box-shadow: 0 4px 14px rgba(0, 0, 0, 0.06);
        overflow: hidden;
        border: 1px solid var(--ui-border);
        border-top: 4px solid var(--ui-primary);
        transition:
          transform 0.15s ease,
          box-shadow 0.15s ease;
      }
      .pay-card:hover {
        transform: translateY(-3px);
        box-shadow: 0 10px 24px rgba(0, 0, 0, 0.1);
      }
      .pay-card-header {
        padding: 1.25rem 1.25rem 0.35rem;
      }
      .pay-card-id {
        font-weight: 800;
        font-size: 1.05rem;
        color: var(--ui-navy);
      }
      .pay-card-date {
        font-size: 0.85rem;
        color: var(--ui-text-secondary);
        margin-top: 0.25rem;
      }
      .pay-card-body {
        padding: 0.5rem 1.25rem 1.25rem;
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 0.5rem;
      }
      .pay-amount {
        font-size: 1.45rem;
        font-weight: 800;
        color: var(--ui-navy);
      }
      .pay-badge {
        font-size: 0.75rem;
        font-weight: 800;
        padding: 0.3rem 0.85rem;
        border-radius: 9999px;
        white-space: nowrap;
      }
      .pay-badge-paye {
        background: var(--ui-teal-soft);
        color: var(--ui-teal-strong);
      }
      .pay-badge-attente {
        background: var(--ui-warning-soft);
        color: var(--ui-warning);
      }
      .pay-badge-rembourse {
        background: var(--ui-primary-soft);
        color: var(--ui-primary-strong);
      }
      .pay-badge-default {
        background: var(--ui-surface-subtle);
        color: var(--ui-text-secondary);
      }

      .pay-empty {
        text-align: center;
        padding: 3rem;
        color: var(--ui-text-secondary);
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 0.75rem;
        font-size: 1.1rem;
        background: var(--ui-surface);
        border-radius: 1.25rem;
        border: 2px dashed var(--ui-border);
      }
      .pay-empty-icon {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 3.5rem;
        height: 3.5rem;
        border-radius: 1rem;
        background: var(--ui-surface-subtle);
        color: var(--ui-text-secondary);
      }
      .pay-empty-icon .ui-icon {
        width: 2rem;
        height: 2rem;
        fill: currentColor;
      }
    </style>
  `,
})
export class VoyageurPaymentsPage {
  private readonly paiementsApi = inject(PaiementsApiService);
  private readonly voyageurSession = inject(VoyageurSessionService);
  readonly language = inject(LanguageService);

  readonly loading = signal(false);
  readonly errorMessage = signal('');
  readonly payments = signal<PaiementResponse[]>([]);
  readonly voyageurId = computed(() => this.voyageurSession.voyageurId());
  readonly totalPaid = computed(() =>
    this.payments()
      .filter((payment) => payment.statut === 'PAYE')
      .reduce((sum, payment) => sum + payment.montant, 0),
  );
  readonly pendingCount = computed(
    () => this.payments().filter((payment) => payment.statut === 'EN_ATTENTE').length,
  );
  readonly refundedCount = computed(
    () => this.payments().filter((payment) => payment.statut === 'REMBOURSE').length,
  );

  constructor() {
    this.loadPayments();
  }

  loadPayments(): void {
    const voyageurId = this.voyageurId();
    if (!voyageurId) {
      this.errorMessage.set(this.language.t('voyageur.payments.noVoyageur'));
      return;
    }

    this.loading.set(true);
    this.paiementsApi.getByVoyageur(voyageurId).subscribe({
      next: (payments) => {
        this.payments.set(payments);
        this.loading.set(false);
      },
      error: (error) => {
        this.loading.set(false);
        this.errorMessage.set(
          extractApiErrorMessage(error, this.language.t('voyageur.payments.error'), (key) =>
            this.language.t(key),
          ),
        );
      },
    });
  }

  payBadgeClass(statut: string): string {
    switch (statut) {
      case 'PAYE':
        return 'pay-badge pay-badge-paye';
      case 'EN_ATTENTE':
        return 'pay-badge pay-badge-attente';
      case 'REMBOURSE':
        return 'pay-badge pay-badge-rembourse';
      default:
        return 'pay-badge pay-badge-default';
    }
  }

  paymentStatusLabel(statut: string): string {
    switch (statut) {
      case 'PAYE':
        return this.language.t('common.paymentPaid');
      case 'EN_ATTENTE':
        return this.language.t('common.paymentPending');
      case 'REMBOURSE':
        return this.language.t('common.paymentRefunded');
      case 'ANNULE':
        return this.language.t('common.paymentCanceled');
      default:
        return this.language.t('common.none');
    }
  }
}
