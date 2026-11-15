import { CommonModule } from '@angular/common';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of } from 'rxjs';
import { VoyageursApiService } from '../../../core/api/voyageurs-api.service';
import { AgencesApiService } from '../../../core/api/agences-api.service';
import { AdminSessionService } from '../../../core/auth/admin-session.service';
import { VoyageurRequest, VoyageurResponse } from '../../../shared/models/voyageur.model';
import { TypeVoyageur } from '../../../shared/models/enums.model';
import { AgenceResponse } from '../../../shared/models/agence-vehicule.model';
import { extractApiErrorMessage } from '../../../shared/utils/api-error.util';
import { LanguageService } from '../../../core/i18n/language.service';

@Component({
  selector: 'app-admin-voyageurs-page',
  standalone: true,
  imports: [
    CommonModule,
    MatCheckboxModule,
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <!-- En-tête gestiontransport admin -->
    <div class="adm-header">
      <div class="adm-header-content">
        <div class="adm-header-left">
          <span class="adm-icon" aria-hidden="true"
            ><svg class="ui-icon"><use href="images/ui-icons.svg#car" /></svg
          ></span>
          <div>
            <h1 class="adm-title ds-section-title">{{ language.t('admin.voyageurs.title') }}</h1>
            <p class="adm-sub ds-subtitle">{{ language.t('admin.voyageurs.subtitle') }}</p>
          </div>
        </div>
        <a mat-stroked-button routerLink="/admin" class="adm-back-btn">{{
          language.t('common.back')
        }}</a>
      </div>
    </div>

    <section class="page-shell">
      <!-- Formulaire création/édition -->
      <div class="adm-form-card">
        <div class="adm-form-header">
          <span class="adm-form-icon">{{ editingId() ? '✏️' : '➕' }}</span>
          <h2 class="adm-form-title">
            {{ editingId() ? language.t('common.edit') : language.t('common.create') }}
          </h2>
        </div>

        <form [formGroup]="form" class="grid gap-4 pt-4 md:grid-cols-2" (ngSubmit)="save()">
          <mat-checkbox formControlName="peutProposerTrajet" class="md:col-span-2"
            >Autoriser ce voyageur à proposer des trajets comme chauffeur</mat-checkbox
          >
          @if (!editingId()) {
            <mat-form-field appearance="outline" subscriptSizing="dynamic"
              ><mat-label>Mot de passe initial personnel</mat-label>
              <input
                matInput
                type="password"
                formControlName="password"
                autocomplete="new-password"
                minlength="12"
                maxlength="128"
              />
              <mat-hint
                >Entre 12 et 128 caractères. Les mots de passe existants ne sont pas
                modifiés.</mat-hint
              >
            </mat-form-field>
          }
          <mat-form-field appearance="outline" subscriptSizing="dynamic">
            <mat-label>{{ language.t('admin.voyageurs.matricule') }}</mat-label>
            <input
              matInput
              formControlName="matricule"
              [readonly]="editingId() !== null"
              [placeholder]="editingId() ? '' : 'V1234 / L12345 / F12345'"
              class="uppercase font-semibold tracking-wider"
            />
            @if (!editingId()) {
              <mat-hint>{{ language.t('admin.voyageurs.matriculeHint') }}</mat-hint>
            }
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>{{ language.t('admin.voyageurs.type') }}</mat-label>
            <mat-select formControlName="typeVoyageur" [disabled]="editingId() !== null">
              <mat-option value="VIP">{{ language.t('admin.voyageurs.typeGlobal') }}</mat-option>
              <mat-option value="LOCAL">{{ language.t('admin.voyageurs.typeAgence') }}</mat-option>
              <mat-option value="LIBRE">{{ language.t('admin.voyageurs.typeLibre') }}</mat-option>
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>{{ language.t('admin.voyageurs.nom') }}</mat-label>
            <input matInput formControlName="nom" placeholder="Dupont" />
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>{{ language.t('admin.voyageurs.prenom') }}</mat-label>
            <input matInput formControlName="prenom" placeholder="Jean" />
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>{{ language.t('admin.voyageurs.email') }}</mat-label>
            <input
              matInput
              type="email"
              formControlName="email"
              placeholder="jean.dupont@email.com"
            />
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>{{ language.t('admin.voyageurs.agence') }}</mat-label>
            <mat-select formControlName="agenceId">
              <mat-option [value]="null">{{ language.t('admin.voyageurs.agenceNone') }}</mat-option>
              @for (agence of agences(); track agence.id) {
                <mat-option [value]="agence.id"> {{ agence.nom }}</mat-option>
              }
            </mat-select>
          </mat-form-field>

          @if (message()) {
            <div class="status-success md:col-span-2">{{ message() }}</div>
          }
          @if (errorMessage()) {
            <div class="status-error md:col-span-2">{{ errorMessage() }}</div>
          }

          <div class="flex items-center gap-3 md:col-span-2">
            <button class="adm-btn-primary" type="submit" [disabled]="loading() || form.invalid">
              {{ editingId() ? language.t('common.save') : language.t('common.create') }}
            </button>
            <button class="adm-btn-secondary" type="button" (click)="resetForm()">
              {{ language.t('common.reset') }}
            </button>
            @if (loading()) {
              <mat-spinner
                [attr.aria-label]="language.t('transport.searching')"
                diameter="24"
              ></mat-spinner>
            }
          </div>
        </form>
      </div>

      <!-- Recherche par matricule -->
      <div class="adm-search-bar">
        <span class="adm-search-icon"></span>
        <input
          class="adm-search-input"
          [attr.aria-label]="language.t('admin.voyageurs.searchPlaceholder')"
          type="text"
          [placeholder]="language.t('admin.voyageurs.searchPlaceholder')"
          [value]="searchQuery()"
          (input)="searchQuery.set($any($event.target).value)"
        />
        @if (searchQuery()) {
          <button class="adm-search-clear" (click)="searchQuery.set('')">✕</button>
        }
        <span class="adm-search-count"
          >{{ displayedVoyageurs().length }} {{ language.t('admin.voyageurs.count') }}</span
        >
      </div>

      <!-- Filtres par type -->
      <div class="adm-type-filters">
        <button
          class="adm-filter-btn"
          [class.active]="typeFilter() === ''"
          (click)="typeFilter.set('')"
        >
          {{ language.t('admin.voyageurs.filterAll') }} ({{ filteredVoyageurs().length }})
        </button>
        <button
          class="adm-filter-btn adm-filter-global"
          [class.active]="typeFilter() === 'VIP'"
          (click)="typeFilter.set('VIP')"
        >
          {{ language.t('admin.voyageurs.filterGlobal') }} ({{ countByType('VIP') }})
        </button>
        <button
          class="adm-filter-btn adm-filter-agence"
          [class.active]="typeFilter() === 'LOCAL'"
          (click)="typeFilter.set('LOCAL')"
        >
          {{ language.t('admin.voyageurs.filterAgence') }} ({{ countByType('LOCAL') }})
        </button>
        <button
          class="adm-filter-btn adm-filter-libre"
          [class.active]="typeFilter() === 'LIBRE'"
          (click)="typeFilter.set('LIBRE')"
        >
          {{ language.t('admin.voyageurs.filterLibre') }} ({{ countByType('LIBRE') }})
        </button>
      </div>

      <!-- Liste des voyageurs -->
      @if (displayedVoyageurs().length === 0) {
        <div class="adm-empty">
          <span class="adm-empty-icon" aria-hidden="true"
            ><svg class="ui-icon"><use href="images/ui-icons.svg#car" /></svg
          ></span>
          <p class="adm-empty-title">{{ language.t('admin.voyageurs.empty') }}</p>
          <p class="adm-empty-sub">
            {{
              searchQuery()
                ? language.t('admin.voyageurs.searchNoResult', { query: searchQuery() })
                : language.t('admin.voyageurs.emptySub')
            }}
          </p>
        </div>
      }

      <div class="grid gap-4 md:grid-cols-2">
        @for (voyageur of displayedVoyageurs(); track voyageur.id) {
          <div
            class="adm-voyageur-card"
            [class]="'adm-voyageur-' + voyageur.typeVoyageur.toLowerCase()"
          >
            <div class="adm-voyageur-top">
              <div class="adm-voyageur-avatar">
                <svg class="ui-icon" aria-hidden="true">
                  <use
                    [attr.href]="
                      voyageur.typeVoyageur === 'LOCAL'
                        ? 'images/ui-icons.svg#agency'
                        : 'images/ui-icons.svg#user'
                    "
                  />
                </svg>
              </div>
              <div class="adm-voyageur-info">
                <div class="adm-voyageur-name">{{ voyageur.prenom }} {{ voyageur.nom }}</div>
                <div class="adm-voyageur-matricule">{{ voyageur.matricule }}</div>
              </div>
              <span class="ds-badge" [class]="typeBadgeClass(voyageur.typeVoyageur)">
                {{ voyageur.typeVoyageur }}
              </span>
              @if (hasActiveRestriction(voyageur.id)) {
                <span class="adm-restriction-badge">{{
                  language.t('admin.voyageurs.restriction')
                }}</span>
              }
            </div>

            <div class="adm-voyageur-details ds-data-list">
              <div class="adm-detail-row ds-data-row">
                <span class="ds-data-key">{{ language.t('admin.voyageurs.email') }}</span>
                <span class="ds-data-value">{{
                  voyageur.email || language.t('admin.voyageurs.emailMissing')
                }}</span>
              </div>
              <div class="adm-detail-row ds-data-row">
                <span class="ds-data-key">{{ language.t('admin.voyageurs.agence') }}</span>
                <span class="ds-data-value">{{
                  voyageur.agenceNom || language.t('admin.voyageurs.agenceAll')
                }}</span>
              </div>
              <div class="adm-detail-row ds-data-row">
                <span class="ds-data-key">{{ language.t('admin.voyageurs.balance') }}</span>
                <span class="ds-data-value">{{ voyageur.solde }} EUR</span>
              </div>
            </div>

            <div class="adm-voyageur-actions">
              <button mat-button type="button" (click)="voirRestrictions(voyageur.id)">
                {{ language.t('ui.restrictionHistory') }}
              </button>
              <button class="adm-action-edit" (click)="edit(voyageur)">
                {{ language.t('admin.voyageurs.edit') }}
              </button>
              <button class="adm-action-delete" (click)="remove(voyageur.id)">
                {{ language.t('admin.voyageurs.delete') }}
              </button>
            </div>
            @if (historiqueVoyageurId() === voyageur.id) {
              <div class="p-4" aria-live="polite">
                @for (restriction of historiqueRestrictions(); track restriction.id) {
                  <p class="break-words">
                    {{ restriction.motif }} —
                    {{
                      restriction.dateDebut
                        ? (restriction.dateDebut | date: 'dd/MM/yyyy')
                        : language.t('ui.unknownStart')
                    }}
                    → {{ restriction.dateFin | date: 'dd/MM/yyyy' }} :
                    {{ restriction.active ? language.t('ui.active') : language.t('ui.inactive') }}
                  </p>
                } @empty {
                  <p>{{ language.t('ui.noRestrictions') }}</p>
                }
              </div>
            }
          </div>
        }
      </div>
    </section>

    <style>
      .adm-header {
        background: linear-gradient(120deg, var(--ui-navy), var(--ui-primary-strong));
        padding: 1.5rem clamp(1rem, 3vw, 2rem);
      }
      .adm-header-content {
        display: flex;
        flex-wrap: wrap;
        gap: 1rem;
        align-items: center;
        justify-content: space-between;
        max-width: 1200px;
        margin: 0 auto;
      }
      .adm-header-left {
        display: flex;
        min-width: 0;
        flex: 1 1 220px;
        align-items: center;
        gap: 1rem;
      }
      .adm-icon {
        font-size: 2.5rem;
      }
      .adm-title {
        font-size: 1.6rem;
        font-weight: 800;
        color: var(--ui-surface);
        margin: 0;
      }
      .adm-sub {
        color: color-mix(in srgb, var(--ui-surface) 75%, transparent);
        font-size: 0.85rem;
        margin: 0;
      }
      .adm-back-btn {
        color: var(--ui-surface) !important;
        border-color: color-mix(in srgb, var(--ui-surface) 40%, transparent) !important;
      }

      .adm-form-card {
        background: var(--ui-surface);
        border-radius: 1.25rem;
        box-shadow: var(--ui-shadow);
        padding: 1.5rem;
        border-left: 5px solid var(--ui-teal-strong);
      }
      .adm-header-left > div { min-width: 0; }
      .adm-search-input { min-width: 0; }
      .adm-form-header {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        margin-bottom: 0.5rem;
      }
      .adm-form-icon {
        font-size: 1.5rem;
      }
      .adm-form-title {
        font-size: 1.2rem;
        font-weight: 700;
        color: var(--ui-navy);
        margin: 0;
      }

      .adm-success {
        background: var(--ui-surface-subtle);
        border: 1px solid var(--ui-teal-strong);
        border-radius: 0.75rem;
        padding: 0.75rem 1rem;
        color: var(--ui-navy);
        font-size: 0.9rem;
      }
      .adm-error {
        background: var(--ui-surface);
        border: 1px solid var(--ui-danger);
        border-radius: 0.75rem;
        padding: 0.75rem 1rem;
        color: var(--ui-danger);
        font-size: 0.9rem;
      }

      .adm-btn-primary {
        background: var(--ui-primary);
        color: var(--ui-surface);
        border: none;
        border-radius: 0.6rem;
        padding: 0.65rem 1.5rem;
        font-weight: 700;
        cursor: pointer;
        box-shadow: var(--ui-shadow);
        transition: transform 0.15s;
      }
      .adm-btn-primary:hover:not(:disabled) {
        transform: translateY(-1px);
      }
      .adm-btn-primary:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }
      .adm-btn-secondary {
        background: var(--ui-surface);
        color: var(--ui-text-secondary);
        border: 1px solid var(--ui-border);
        border-radius: 0.6rem;
        padding: 0.65rem 1.25rem;
        font-weight: 600;
        cursor: pointer;
        transition: background 0.15s;
      }
      .adm-btn-secondary:hover {
        background: var(--ui-surface-subtle);
      }

      .adm-search-bar {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        background: var(--ui-surface);
        border: 2px solid var(--ui-teal-strong);
        border-radius: 1rem;
        padding: 0.6rem 1rem;
        box-shadow: var(--ui-shadow);
      }
      .adm-search-icon {
        font-size: 1.1rem;
      }
      .adm-search-input {
        flex: 1;
        border: none;
        outline: none;
        font-size: 0.95rem;
        background: transparent;
        color: var(--ui-navy);
      }
      .adm-search-clear {
        background: var(--ui-surface-subtle);
        border: none;
        border-radius: 50%;
        width: 1.5rem;
        height: 1.5rem;
        cursor: pointer;
        font-size: 0.75rem;
        display: flex;
        align-items: center;
        justify-content: center;
      }
      .adm-search-count {
        font-size: 0.8rem;
        color: var(--ui-text-secondary);
        white-space: nowrap;
        background: var(--ui-surface-subtle);
        padding: 0.2rem 0.6rem;
        border-radius: 9999px;
      }

      .adm-type-filters {
        display: flex;
        flex-wrap: wrap;
        gap: 0.5rem;
      }
      .adm-filter-btn {
        padding: 0.4rem 1rem;
        border-radius: 9999px;
        border: 2px solid var(--ui-surface-subtle);
        background: var(--ui-surface);
        font-size: 0.82rem;
        font-weight: 600;
        cursor: pointer;
        transition: all 0.15s;
        color: var(--ui-text-secondary);
      }
      .adm-filter-btn.active,
      .adm-filter-btn:hover {
        border-color: var(--ui-teal-strong);
        background: var(--ui-surface-subtle);
        color: var(--ui-navy);
      }
      .adm-filter-global.active {
        border-color: var(--ui-primary-strong);
        background: var(--ui-surface-subtle);
        color: var(--ui-primary-strong);
      }
      .adm-filter-agence.active {
        border-color: var(--ui-teal-strong);
        background: var(--ui-surface-subtle);
        color: var(--ui-navy);
      }
      .adm-filter-libre.active {
        border-color: var(--ui-warning);
        background: var(--ui-surface-subtle);
        color: var(--ui-warning);
      }

      .adm-empty {
        text-align: center;
        padding: 3rem 1rem;
      }
      .adm-empty-icon {
        font-size: 3rem;
        display: block;
        margin-bottom: 0.75rem;
      }
      .adm-empty-title {
        font-size: 1.1rem;
        font-weight: 700;
        color: var(--ui-text-secondary);
        margin: 0 0 0.35rem;
      }
      .adm-empty-sub {
        color: var(--ui-text-secondary);
        font-size: 0.9rem;
        margin: 0;
      }

      .adm-voyageur-card {
        background: var(--ui-surface);
        border-radius: 1.25rem;
        box-shadow: var(--ui-shadow);
        overflow: hidden;
        transition:
          transform 0.15s,
          box-shadow 0.15s;
        border-top: 4px solid var(--ui-surface-subtle);
      }
      .adm-voyageur-card:hover {
        transform: translateY(-3px);
        box-shadow: var(--ui-shadow);
      }
      .adm-voyageur-global {
        border-top-color: var(--ui-primary-strong);
      }
      .adm-voyageur-agence {
        border-top-color: var(--ui-teal-strong);
      }
      .adm-voyageur-libre {
        border-top-color: var(--ui-warning);
      }

      .adm-voyageur-top {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        padding: 1rem 1.25rem 0.5rem;
      }
      .adm-voyageur-avatar {
        font-size: 2rem;
      }
      .adm-voyageur-info {
        flex: 1;
      }
      .adm-voyageur-name {
        font-size: 1rem;
        font-weight: 700;
        color: var(--ui-navy);
      }
      .adm-voyageur-matricule {
        font-size: 0.85rem;
        color: var(--ui-text-secondary);
        font-family: monospace;
        letter-spacing: 0.05em;
      }

      .adm-voyageur-badge {
        display: inline-block;
        padding: 0.2rem 0.7rem;
        border-radius: 9999px;
        font-size: 0.72rem;
        font-weight: 800;
        letter-spacing: 0.06em;
      }
      .adm-badge-global {
        background: var(--ui-primary-soft);
        color: var(--ui-primary-strong);
      }
      .adm-badge-agence {
        background: var(--ui-teal-soft);
        color: var(--ui-teal-strong);
      }
      .adm-badge-libre {
        background: var(--ui-warning-soft);
        color: var(--ui-warning);
      }
      .adm-restriction-badge {
        display: inline-flex;
        align-items: center;
        gap: 0.25rem;
        background: var(--ui-surface-subtle);
        color: var(--ui-danger);
        border: 1px solid var(--ui-danger-soft);
        padding: 0.2rem 0.65rem;
        border-radius: 9999px;
        font-size: 0.72rem;
        font-weight: 800;
        white-space: nowrap;
      }

      .adm-voyageur-details {
        padding: 0.5rem 1.25rem 0.75rem;
        display: flex;
        flex-direction: column;
        gap: 0.3rem;
      }
      .adm-detail-row {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        font-size: 0.85rem;
        color: var(--ui-text-secondary);
      }

      .adm-voyageur-actions {
        display: flex;
        gap: 0.5rem;
        padding: 0.75rem 1.25rem;
        border-top: 1px solid var(--ui-surface-subtle);
      }
      .adm-action-edit {
        flex: 1;
        background: var(--ui-surface-subtle);
        color: var(--ui-primary-strong);
        border: 1px solid var(--ui-primary-soft);
        border-radius: 0.5rem;
        padding: 0.4rem;
        font-size: 0.82rem;
        font-weight: 600;
        cursor: pointer;
        transition: background 0.15s;
      }
      .adm-action-edit:hover {
        background: var(--ui-primary-soft);
      }
      .adm-action-delete {
        flex: 1;
        background: var(--ui-surface);
        color: var(--ui-danger);
        border: 1px solid var(--ui-danger-soft);
        border-radius: 0.5rem;
        padding: 0.4rem;
        font-size: 0.82rem;
        font-weight: 600;
        cursor: pointer;
        transition: background 0.15s;
      }
      .adm-action-delete:hover {
        background: var(--ui-surface-subtle);
      }
    </style>
  `,
})
export class AdminVoyageursPage {
  readonly historiqueVoyageurId = signal<number | null>(null);
  readonly historiqueRestrictions = signal<
    import('../../../shared/models/voyageur.model').RestrictionResponse[]
  >([]);

  voirRestrictions(id: number): void {
    this.historiqueVoyageurId.set(null);
    this.voyageursApi.getRestrictions(id).subscribe({
      next: (rows) => {
        this.historiqueRestrictions.set(rows);
        this.historiqueVoyageurId.set(id);
      },
      error: (error) =>
        this.errorMessage.set(
          extractApiErrorMessage(error, "L'historique est indisponible.", (key) =>
            this.language.t(key),
          ),
        ),
    });
  }
  private readonly voyageursApi = inject(VoyageursApiService);
  private readonly agencesApi = inject(AgencesApiService);
  private readonly adminSession = inject(AdminSessionService);
  readonly language = inject(LanguageService);

  readonly loading = signal(false);
  readonly message = signal('');
  readonly errorMessage = signal('');
  readonly editingId = signal<number | null>(null);
  readonly voyageurs = signal<VoyageurResponse[]>([]);
  readonly agences = signal<AgenceResponse[]>([]);
  readonly searchQuery = signal('');
  readonly typeFilter = signal('');
  readonly restrictedVoyageurIds = signal<Set<number>>(new Set());

  readonly filteredVoyageurs = computed(() => {
    const agenceId = this.adminSession.agenceId();
    if (this.adminSession.isGlobalAdmin() || !agenceId) {
      return this.voyageurs();
    }
    return this.voyageurs().filter((m) => m.agenceId === agenceId || m.agenceId === null);
  });

  readonly displayedVoyageurs = computed(() => {
    let list = this.filteredVoyageurs();
    const q = this.searchQuery().trim().toUpperCase();
    const t = this.typeFilter();
    if (q) {
      list = list.filter(
        (m) =>
          m.matricule.toUpperCase().includes(q) ||
          m.nom.toUpperCase().includes(q) ||
          m.prenom.toUpperCase().includes(q),
      );
    }
    if (t) {
      list = list.filter((m) => m.typeVoyageur === t);
    }
    return list;
  });

  countByType(type: string): number {
    return this.filteredVoyageurs().filter((m) => m.typeVoyageur === type).length;
  }

  typeBadgeClass(type: TypeVoyageur): string {
    if (type === 'VIP') {
      return 'ds-badge-info';
    }
    if (type === 'LOCAL') {
      return 'ds-badge-success';
    }
    return 'ds-badge-warning';
  }

  hasActiveRestriction(voyageurId: number): boolean {
    return this.restrictedVoyageurIds().has(voyageurId);
  }

  readonly form = new FormGroup({
    password: new FormControl('', {
      nonNullable: true,
      validators: [Validators.minLength(12), Validators.maxLength(128)],
    }),
    peutProposerTrajet: new FormControl(false, { nonNullable: true }),
    matricule: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    nom: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    prenom: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    email: new FormControl('', { nonNullable: true, validators: [Validators.email] }),
    typeVoyageur: new FormControl<TypeVoyageur>('VIP', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    agenceId: new FormControl<number | null>(null),
  });

  constructor() {
    this.loadData();
  }

  loadData(): void {
    this.loading.set(true);
    this.restrictedVoyageurIds.set(new Set());
    forkJoin({
      voyageurs: this.voyageursApi.getAll(),
      agences: this.agencesApi.getAll(),
    }).subscribe({
      next: ({ voyageurs, agences }) => {
        this.voyageurs.set(voyageurs);
        this.agences.set(
          this.adminSession.isGlobalAdmin()
            ? agences
            : agences.filter((s) => s.id === this.adminSession.agenceId()),
        );
        this.loadRestrictionStatuses(voyageurs);
        this.loading.set(false);
      },
      error: (error) => {
        this.loading.set(false);
        this.restrictedVoyageurIds.set(new Set());
        this.errorMessage.set(
          extractApiErrorMessage(error, this.language.t('admin.voyageurs.loadError'), (key) =>
            this.language.t(key),
          ),
        );
      },
    });
  }

  private loadRestrictionStatuses(voyageurs: VoyageurResponse[]): void {
    if (voyageurs.length === 0) {
      this.restrictedVoyageurIds.set(new Set());
      return;
    }

    forkJoin(
      voyageurs.map((voyageur) =>
        this.voyageursApi.hasRestriction(voyageur.id).pipe(
          map((hasRestriction) => ({ id: voyageur.id, hasRestriction })),
          catchError(() => of({ id: voyageur.id, hasRestriction: false })),
        ),
      ),
    ).subscribe((statuses) => {
      this.restrictedVoyageurIds.set(
        new Set(statuses.filter((status) => status.hasRestriction).map((status) => status.id)),
      );
    });
  }

  edit(voyageur: VoyageurResponse): void {
    this.editingId.set(voyageur.id);
    this.form.patchValue({
      peutProposerTrajet: !!voyageur.peutProposerTrajet,
      matricule: voyageur.matricule,
      nom: voyageur.nom,
      prenom: voyageur.prenom,
      email: voyageur.email,
      typeVoyageur: voyageur.typeVoyageur,
      agenceId: voyageur.agenceId,
    });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  resetForm(): void {
    this.editingId.set(null);
    this.message.set('');
    this.errorMessage.set('');
    this.form.reset({
      password: '',
      peutProposerTrajet: false,
      matricule: '',
      nom: '',
      prenom: '',
      email: '',
      typeVoyageur: this.adminSession.isAgenceAdmin() ? 'LOCAL' : 'VIP',
      agenceId: this.adminSession.isAgenceAdmin() ? this.adminSession.agenceId() : null,
    });
  }

  save(): void {
    if (this.form.invalid || this.loading()) return;

    this.loading.set(true);
    this.message.set('');
    this.errorMessage.set('');

    const payload = this.form.getRawValue() as VoyageurRequest;
    if (this.editingId()) delete payload.password;
    else if (!payload.password || payload.password.length < 12) {
      this.loading.set(false);
      this.errorMessage.set('Un mot de passe initial de 12 caractères minimum est obligatoire.');
      return;
    }
    if (this.adminSession.isAgenceAdmin()) {
      payload.agenceId = this.adminSession.agenceId() ?? undefined;
      payload.typeVoyageur = 'LOCAL';
    }

    const request$ = this.editingId()
      ? this.voyageursApi.update(this.editingId()!, payload)
      : this.voyageursApi.create(payload);

    request$.subscribe({
      next: () => {
        const wasEditing = this.editingId() !== null;
        this.loading.set(false);
        this.resetForm();
        this.message.set(
          wasEditing
            ? this.language.t('admin.voyageurs.updateSuccess')
            : this.language.t('admin.voyageurs.createSuccess'),
        );
        this.loadData();
      },
      error: (error) => {
        this.loading.set(false);
        this.errorMessage.set(
          extractApiErrorMessage(error, this.language.t('admin.voyageurs.saveError'), (key) =>
            this.language.t(key),
          ),
        );
      },
    });
  }

  remove(id: number): void {
    if (!confirm(this.language.t('admin.voyageurs.deleteConfirm'))) return;
    this.loading.set(true);
    this.message.set('');
    this.errorMessage.set('');

    this.voyageursApi.delete(id).subscribe({
      next: () => {
        this.loading.set(false);
        this.message.set(this.language.t('admin.voyageurs.deleteSuccess'));
        this.loadData();
      },
      error: (error) => {
        this.loading.set(false);
        this.errorMessage.set(
          extractApiErrorMessage(error, this.language.t('admin.voyageurs.deleteError'), (key) =>
            this.language.t(key),
          ),
        );
      },
    });
  }
}
