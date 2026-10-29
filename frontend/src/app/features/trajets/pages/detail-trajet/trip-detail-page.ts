import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  catchError,
  combineLatest,
  defer,
  EMPTY,
  finalize,
  forkJoin,
  map,
  of,
  startWith,
  Subject,
  switchMap,
  tap,
} from 'rxjs';
import { ZonesApi } from '../../../../core/services/zones-api';
import { ApiFailure } from '../../../../core/services/api-error';
import { Notice } from '../../../../shared/components/notice';
import { Icon } from '../../../../shared/components/icon';
import { TrajetsApi } from '../../services/trajets-api';
import { TrajetResponse } from '../../models/trajet.model';
import { positiveId, readTripCriteria } from '../../models/trip-criteria';
import { TripDatePipe } from '../../components/trip-date.pipe';
import { TripStatusBadge } from '../../components/trip-status';

@Component({
  imports: [RouterLink, Notice, Icon, TripDatePipe, TripStatusBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './trip-detail-page.html',
})
export class TripDetailPage {
  private readonly api = inject(TrajetsApi);
  private readonly zoneApi = inject(ZonesApi);
  private readonly route = inject(ActivatedRoute);
  private readonly reload = new Subject<void>();
  protected readonly trip = signal<TrajetResponse | null>(null);
  protected readonly zoneName = signal('');
  protected readonly zoneWarning = signal(false);
  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly backCriteria = toSignal(
    this.route.queryParamMap.pipe(
      map((params) => readTripCriteria(params) ?? { zoneId: null, page: 0 }),
    ),
    { initialValue: { zoneId: null, page: 0 } },
  );
  protected readonly tripNotice = signal('');

  constructor() {
    combineLatest([this.route.paramMap, this.reload.pipe(startWith(undefined))])
      .pipe(
        switchMap(([params]) => {
          this.trip.set(null);
          this.zoneName.set('');
          this.zoneWarning.set(false);
          this.error.set('');
          const id = positiveId(params.get('id'));
          if (id === null) {
            this.loading.set(false);
            this.error.set('Ce trajet est introuvable ou n’est plus accessible.');
            return EMPTY;
          }
          return defer(() => {
            this.loading.set(true);
            return forkJoin({
              trip: this.api.detail(id),
              zones: this.zoneApi.all().pipe(
                catchError(() => {
                  this.zoneWarning.set(true);
                  return of(null);
                }),
              ),
            }).pipe(
              tap(({ trip, zones }) => {
                this.trip.set(trip);
                this.zoneName.set(
                  zones?.find((zone) => zone.zoneId === trip.zoneId)?.nom ??
                    `Zone n° ${trip.zoneId}`,
                );
                this.tripNotice.set(
                  trip.statut === 'ANNULE'
                    ? 'Ce trajet a été annulé.'
                    : trip.statut === 'REALISE'
                      ? 'Ce trajet est terminé.'
                      : new Date(trip.depart).getTime() <= Date.now()
                        ? 'Le départ de ce trajet a déjà eu lieu. Les réservations ne sont plus possibles.'
                        : trip.statut === 'COMPLET'
                          ? 'Ce trajet est complet.'
                          : 'La consultation d’un trajet ne garantit pas une place. Les conditions de réservation seront vérifiées lors d’une future demande.',
                );
              }),
              catchError((error: unknown) => {
                this.error.set(
                  error instanceof ApiFailure && error.status === 404
                    ? 'Ce trajet est introuvable ou n’est plus accessible.'
                    : error instanceof ApiFailure
                      ? error.message
                      : 'Impossible de charger ce trajet. Réessayez.',
                );
                return EMPTY;
              }),
              finalize(() => this.loading.set(false)),
            );
          });
        }),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe();
  }
  protected retry(): void {
    this.reload.next();
  }
}
