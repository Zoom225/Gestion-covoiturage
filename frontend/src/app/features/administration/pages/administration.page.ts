import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../core/auth/auth-service';
import { CovoiturageApi } from '../../../core/services/covoiturage-api';
import { ClientCategory } from '../../../shared/models/auth.model';
import { Compte, PageResult, Zone } from '../../../shared/models/covoiturage.model';
import { Notice } from '../../../shared/components/notice';
import { Pagination } from '../../../shared/components/pagination';

@Component({
  imports: [ReactiveFormsModule, Notice, Pagination],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="dashboard-heading">
      <h1>Administration des comptes</h1>
      <p>Seuls les comptes et zones de votre périmètre sont accessibles.</p>
    </div>
    @if (error()) {
      <app-notice tone="error" [message]="error()" />
    }
    @if (message()) {
      <app-notice tone="success" [message]="message()" />
    }
    <form class="business-card business-form" [formGroup]="form" (ngSubmit)="create()">
      <h2>Créer un client et préparer son invitation</h2>
      <label
        >Adresse e-mail du client
        <input type="email" formControlName="email" maxlength="254" required
      /></label>
      <label
        >Catégorie
        <select formControlName="categorie">
          <option value="LOCAL">Local</option>
          @if (auth.user()?.perimetreAdmin === 'GLOBAL') {
            <option value="VIP">VIP</option>
            <option value="OCCASIONNEL">Occasionnel</option>
          }
        </select>
      </label>
      @if (form.controls.categorie.value === 'LOCAL') {
        <label
          >Zone de rattachement
          <select formControlName="zoneClientId">
            <option [ngValue]="0">Choisir une zone</option>
            @for (zone of zones(); track zone.zoneId) {
              <option [ngValue]="zone.zoneId">{{ zone.nom }}</option>
            }
          </select>
        </label>
      }
      <button type="submit" class="btn btn-primary" [disabled]="busy() || !ready()">
        Créer le client
      </button>
      <p>
        Aucun mot de passe n'est choisi ici. Le client active son compte grâce à son invitation.
      </p>
    </form>
    <div class="business-actions">
      <h2>Comptes autorisés</h2>
      <button type="button" class="btn btn-secondary" [disabled]="busy()" (click)="load()">
        Actualiser
      </button>
    </div>
    <div class="business-table">
      <table>
        <caption>
          Comptes visibles dans votre périmètre administratif
        </caption>
        <thead>
          <tr>
            <th>Identifiant</th>
            <th>Rôle</th>
            <th>État</th>
            <th>Action</th>
          </tr>
        </thead>
        <tbody>
          @for (account of result()?.content; track account.compteId) {
            <tr>
              <td>{{ account.identifiant }}</td>
              <td>{{ account.role }} {{ account.categorie }}</td>
              <td>
                {{ account.etatIdentifiants }} ·
                {{ account.accesEffectif ? 'Accès actif' : 'Sans accès effectif' }}
              </td>
              <td>
                @if (account.etatIdentifiants === 'INVITE') {
                  <button
                    class="btn btn-secondary"
                    type="button"
                    [disabled]="busy()"
                    (click)="renew(account.compteId)"
                  >
                    Renouveler l'invitation
                  </button>
                }
              </td>
            </tr>
          } @empty {
            <tr>
              <td colspan="4">Aucun compte à afficher.</td>
            </tr>
          }
        </tbody>
      </table>
    </div>
    <app-pagination [page]="result()" [busy]="busy()" (change)="load($event)" />
  `,
})
export class AdministrationPage {
  private readonly api = inject(CovoiturageApi);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly auth = inject(AuthService);
  protected readonly zones = signal<Zone[]>([]);
  protected readonly result = signal<PageResult<Compte> | null>(null);
  protected readonly busy = signal(false);
  protected readonly ready = signal(false);
  protected readonly error = signal('');
  protected readonly message = signal('');
  protected readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    categorie: ['LOCAL' as ClientCategory, Validators.required],
    zoneClientId: [0],
  });
  constructor() {
    this.api
      .zones(true)
      .pipe(takeUntilDestroyed())
      .subscribe({
        next: (zones) => {
          this.zones.set(zones);
          this.ready.set(true);
          const localZone = this.auth.user()?.zoneAdminId;
          if (localZone) this.form.controls.zoneClientId.setValue(localZone);
        },
        error: (error: Error) => this.error.set(error.message),
      });
    this.load();
  }
  protected load(page = this.result()?.page ?? 0): void {
    this.busy.set(true);
    this.error.set('');
    this.api
      .accounts(page)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.result.set(result);
          this.busy.set(false);
        },
        error: (error: Error) => {
          this.busy.set(false);
          this.error.set(error.message);
        },
      });
  }
  protected create(): void {
    if (this.busy() || !this.ready()) return;
    const value = this.form.getRawValue();
    if (this.form.invalid || (value.categorie === 'LOCAL' && value.zoneClientId <= 0)) {
      this.form.markAllAsTouched();
      this.error.set('Saisissez un e-mail valide et la zone du client local.');
      return;
    }
    this.busy.set(true);
    this.error.set('');
    this.message.set('');
    this.api
      .createClient(
        value.email.trim(),
        value.categorie,
        value.categorie === 'LOCAL' ? value.zoneClientId : null,
      )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.form.controls.email.reset();
          this.message.set(
            "Client créé. L'invitation est mise en file ; sa livraison peut prendre un moment.",
          );
          this.load(0);
        },
        error: (error: Error) => {
          this.busy.set(false);
          this.error.set(error.message);
        },
      });
  }
  protected renew(id: number): void {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    this.message.set('');
    this.api
      .renewInvitation(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.message.set('Nouvelle invitation mise en file. La précédente est révoquée.');
          this.load();
        },
        error: (error: Error) => {
          this.busy.set(false);
          this.error.set(error.message);
        },
      });
  }
}
