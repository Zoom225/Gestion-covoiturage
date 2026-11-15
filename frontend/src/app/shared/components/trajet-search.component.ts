import { LanguageService } from '../../core/i18n/language.service';
import { Component, EventEmitter, Output, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TrajetsApiService } from '../../core/api/trajets-api.service';
import { RechercheTrajet, VilleOption } from '../models/trajet.model';
import { differentCitiesValidator } from '../validators/different-cities.validator';

@Component({
  selector: 'app-trajet-search',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatSelectModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <form
      [formGroup]="form"
      (ngSubmit)="search()"
      class="search-form"
      [attr.aria-busy]="chargementVilles()"
      aria-label="{{ language.t('transport.search') }}"
    >
      <mat-form-field appearance="outline"
        ><mat-label>{{ language.t('transport.departure') }}</mat-label>
        <svg matPrefix class="ui-icon search-icon" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="7"/><circle cx="12" cy="12" r="2"/></svg>
        <mat-select formControlName="depart"
          ><mat-option value="">{{ language.t('transport.allCities') }}</mat-option>
          @for (ville of villes(); track ville.code) {
            <mat-option [value]="ville.code" [disabled]="ville.code === form.controls.arrivee.value">{{ ville.libelle }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline"
        ><mat-label>{{ language.t('transport.destination') }}</mat-label>
        <svg matPrefix class="ui-icon search-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M19 10c0 5-7 11-7 11S5 15 5 10a7 7 0 1 1 14 0Z"/><circle cx="12" cy="10" r="2"/></svg>
        <mat-select formControlName="arrivee"
          ><mat-option value="">{{ language.t('transport.allCities') }}</mat-option>
          @for (ville of villes(); track ville.code) {
            <mat-option [value]="ville.code" [disabled]="ville.code === form.controls.depart.value">{{ ville.libelle }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline"
        ><mat-label>{{ language.t('transport.travelDate') }}</mat-label
        ><svg matPrefix class="ui-icon search-icon" viewBox="0 0 24 24" aria-hidden="true"><rect x="4" y="5" width="16" height="16" rx="3"/><path d="M8 3v5m8-5v5M4 11h16"/></svg><input matInput type="date" formControlName="date"
      /></mat-form-field>
      <button mat-flat-button type="submit" [disabled]="chargementVilles() || form.invalid">
        @if (chargementVilles()) { <mat-spinner diameter="18" [attr.aria-label]="language.t('transport.searching')" /> }
        {{ language.t('transport.search') }}
      </button>
    </form>
    @if (form.hasError('sameCity')) {
      <p role="alert" class="status-error">{{ language.t('transport.differentCities') }}</p>
    }
    @if (erreur()) {
      <p role="alert" class="status-error">{{ erreur() }}</p>
    }
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .search-form {
        display: grid;
        grid-template-columns: 1fr 1fr 1fr auto;
        gap: 1rem;
        align-items: start;
      }
      mat-form-field {
        min-width: 0;
      }
      .search-icon { width: 19px; height: 19px; color: var(--ui-teal-strong); margin-inline: .75rem .25rem; }
      button {
        min-height: 56px;
      }
      @media (max-width: 1000px) {
        .search-form {
          grid-template-columns: 1fr 1fr;
        }
      }
      @media (max-width: 560px) {
        .search-form {
          grid-template-columns: 1fr;
          gap: 0.25rem;
        }
        button {
          width: 100%;
        }
      }
    `,
  ],
})
export class TrajetSearchComponent {
  readonly language = inject(LanguageService);
  @Output() readonly recherche = new EventEmitter<RechercheTrajet>();
  readonly villes = signal<VilleOption[]>([]);
  readonly erreur = signal('');
  readonly chargementVilles = signal(true);
  readonly form = new FormGroup({
    depart: new FormControl('', { nonNullable: true }),
    arrivee: new FormControl('', { nonNullable: true }),
    date: new FormControl('', { nonNullable: true }),
  }, { validators: differentCitiesValidator('depart', 'arrivee') });
  search(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.chargementVilles()) return;
    this.recherche.emit(this.form.getRawValue());
  }
  constructor() {
    inject(TrajetsApiService)
      .getVilles()
      .subscribe({
        next: (villes) => {
          this.villes.set(villes);
          this.chargementVilles.set(false);
        },
        error: () => {
          this.chargementVilles.set(false);
          this.erreur.set(this.language.t('ui.loadCities'));
        },
      });
  }
}
