import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { CovoiturageApi } from '../../../core/services/covoiturage-api';
import { Zone, Vehicule, TrajetRequest } from '../../../shared/models/covoiturage.model';
import { Notice } from '../../../shared/components/notice';

@Component({
  imports: [ReactiveFormsModule, RouterLink, Notice],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="dashboard-heading">
      <h1>{{ id ? 'Modifier mon trajet' : 'Créer un trajet' }}</h1>
      <p>L'organisateur réserve une place en attente. Chaque passager paie ensuite 20 €.</p>
    </div>
    @if (error()) {
      <app-notice tone="error" [message]="error()" />
    }
    <form class="business-card business-form" [formGroup]="form" (ngSubmit)="save()">
      <label
        >Zone
        <select
          formControlName="zoneId"
          (change)="loadVehicles()"
          [attr.aria-disabled]="id ? true : null"
        >
          <option [ngValue]="0">Choisir une zone</option>
          @for (zone of zones(); track zone.zoneId) {
            <option [ngValue]="zone.zoneId">{{ zone.nom }}</option>
          }
        </select>
      </label>
      <label
        >Véhicule
        <select formControlName="vehiculeId">
          <option [ngValue]="0">
            {{ vehiclesBusy() ? 'Chargement…' : 'Choisir un véhicule actif' }}
          </option>
          @for (vehicle of vehicles(); track vehicle.vehiculeId) {
            <option [ngValue]="vehicle.vehiculeId">
              Véhicule n° {{ vehicle.vehiculeId }} · {{ vehicle.zoneNom }}
            </option>
          }
        </select>
      </label>
      <label>Départ <input formControlName="origine" maxlength="255" required /></label>
      <label>Destination <input formControlName="destination" maxlength="255" required /></label>
      <p>Les horaires saisis utilisent le fuseau de cet appareil : {{ localZone }}.</p>
      <label
        >Date et heure de départ
        <input type="datetime-local" step="1" formControlName="depart" required
      /></label>
      <label
        >Date et heure d'arrivée
        <input type="datetime-local" step="1" formControlName="arrivee" required
      /></label>
      <label
        >Type de trajet
        <select formControlName="type">
          <option value="PUBLIC">Public</option>
          <option value="PRIVE">Privé</option>
        </select>
      </label>
      <p>
        4 passagers et 1 chauffeur. Un trajet privé incomplet à J-1 est converti en public, avec une
        dette de 80 € pour l'organisateur.
      </p>
      <div class="business-actions">
        <button
          class="btn btn-primary"
          type="submit"
          [disabled]="busy() || vehiclesBusy() || !ready()"
        >
          {{ busy() ? 'Enregistrement…' : 'Enregistrer le trajet' }}
        </button>
        <a routerLink="/tableau-de-bord/trajets">Retour aux trajets</a>
      </div>
    </form>
  `,
})
export class TrajetFormPage {
  private readonly api = inject(CovoiturageApi);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly routeId = inject(ActivatedRoute).snapshot.paramMap.get('id');
  protected readonly id = this.routeId === null ? undefined : Number(this.routeId);
  protected readonly localZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
  protected readonly zones = signal<Zone[]>([]);
  protected readonly vehicles = signal<Vehicule[]>([]);
  protected readonly busy = signal(false);
  protected readonly vehiclesBusy = signal(false);
  protected readonly ready = signal(false);
  protected readonly error = signal('');
  protected readonly form = inject(FormBuilder).nonNullable.group({
    zoneId: [0, [Validators.min(1)]],
    vehiculeId: [0, [Validators.min(1)]],
    origine: ['', [Validators.required, Validators.maxLength(255)]],
    destination: ['', [Validators.required, Validators.maxLength(255)]],
    depart: ['', Validators.required],
    arrivee: ['', Validators.required],
    type: ['PUBLIC' as TrajetRequest['type'], Validators.required],
  });
  constructor() {
    if (this.id !== undefined && (!Number.isSafeInteger(this.id) || this.id < 1)) {
      this.error.set('Identifiant de trajet invalide. Revenez à la liste des trajets.');
      return;
    }
    forkJoin({ zones: this.api.zones(), trip: this.id ? this.api.trip(this.id) : of(null) })
      .pipe(takeUntilDestroyed())
      .subscribe({
        next: ({ zones, trip }) => {
          this.zones.set(zones);
          if (trip) {
            this.form.patchValue({
              ...trip,
              depart: this.localTime(trip.depart),
              arrivee: this.localTime(trip.arrivee),
              origine: trip.origine ?? '',
              destination: trip.destination ?? '',
            });
            this.form.controls.zoneId.disable();
            this.form.controls.type.disable();
          }
          this.ready.set(true);
          if (trip) this.loadVehicles(trip.vehiculeId);
        },
        error: (error: Error) => this.error.set(error.message),
      });
  }
  private localTime(value: string): string {
    const date = new Date(value);
    return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 19);
  }
  protected loadVehicles(selected = 0): void {
    this.vehicles.set([]);
    this.form.controls.vehiculeId.setValue(0);
    const zone = this.form.getRawValue().zoneId;
    if (!zone) return;
    this.vehiclesBusy.set(true);
    this.form.controls.zoneId.disable();
    this.api
      .vehicles(zone)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (vehicles) => {
          this.vehicles.set(vehicles.filter((v) => v.actif));
          this.form.controls.vehiculeId.setValue(selected);
          this.vehiclesBusy.set(false);
          if (!this.id) this.form.controls.zoneId.enable();
        },
        error: (error: Error) => {
          this.error.set(error.message);
          this.vehiclesBusy.set(false);
          if (!this.id) this.form.controls.zoneId.enable();
        },
      });
  }
  protected save(): void {
    if (this.busy() || this.vehiclesBusy() || !this.ready()) return;
    this.error.set('');
    const value = this.form.getRawValue();
    if (
      this.form.invalid ||
      !value.origine.trim() ||
      !value.destination.trim() ||
      !Number.isFinite(Date.parse(value.depart)) ||
      !Number.isFinite(Date.parse(value.arrivee))
    ) {
      this.form.markAllAsTouched();
      this.error.set('Complétez tous les champs et choisissez une zone et un véhicule.');
      return;
    }
    if (Date.parse(value.arrivee) <= Date.parse(value.depart)) {
      this.error.set("L'arrivée doit être strictement postérieure au départ.");
      return;
    }
    this.busy.set(true);
    this.api
      .saveTrip(
        {
          ...value,
          depart: new Date(value.depart).toISOString(),
          arrivee: new Date(value.arrivee).toISOString(),
        },
        this.id,
      )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (trip) => void this.router.navigate(['/tableau-de-bord/trajets', trip.trajetId]),
        error: (error: Error) => {
          this.busy.set(false);
          this.error.set(error.message);
        },
      });
  }
}
