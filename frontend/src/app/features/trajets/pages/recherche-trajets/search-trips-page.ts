import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import {
  BehaviorSubject,
  catchError,
  combineLatest,
  defer,
  EMPTY,
  finalize,
  startWith,
  Subject,
  switchMap,
  tap,
} from 'rxjs';
import { ZonesApi } from '../../../../core/services/zones-api';
import { ApiFailure } from '../../../../core/services/api-error';
import { ZoneResponse } from '../../../../shared/models/zone.model';
import { PageResponse } from '../../../../shared/models/page.model';
import { Icon } from '../../../../shared/components/icon';
import { Notice } from '../../../../shared/components/notice';
import { TrajetResponse, TripCriteria } from '../../models/trajet.model';
import { readTripCriteria } from '../../models/trip-criteria';
import { TrajetsApi } from '../../services/trajets-api';
import { TripCard } from '../../components/carte-trajet/trip-card';

@Component({
  imports: [ReactiveFormsModule, Icon, Notice, TripCard],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './search-trips-page.html',
})
export class SearchTripsPage {
  private readonly zoneApi = inject(ZonesApi);
  private readonly tripsApi = inject(TrajetsApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroy = inject(DestroyRef);
  private readonly zoneReload = new Subject<void>();
  private readonly zonesReady = new BehaviorSubject(false);
  private readonly refresh = new Subject<void>();
  protected readonly zoneControl = new FormControl('', { nonNullable: true });
  protected readonly form = new FormGroup({ zoneId: this.zoneControl });
  protected readonly zones = signal<ZoneResponse[]>([]);
  protected readonly loadingZones = signal(true);
  protected readonly zonesError = signal('');
  protected readonly searching = signal(false);
  protected readonly searchError = signal('');
  protected readonly results = signal<PageResponse<TrajetResponse> | null>(null);
  protected readonly criteria = signal<TripCriteria>({ zoneId: null, page: 0 });

  constructor() {
    combineLatest([
      this.route.queryParamMap,
      this.zonesReady,
      this.refresh.pipe(startWith(undefined)),
    ])
      .pipe(
        switchMap(([params, ready]) => {
          const criteria = readTripCriteria(params);
          this.results.set(null);
          this.searchError.set('');
          this.zoneControl.setValue(criteria?.zoneId?.toString() ?? '');
          if (!ready || !this.zones().length) return EMPTY;
          if (!criteria) {
            this.searchError.set(
              'Les critères de recherche sont invalides. Choisissez une zone puis relancez la recherche.',
            );
            return EMPTY;
          }
          if (
            criteria.zoneId !== null &&
            !this.zones().some((zone) => zone.zoneId === criteria.zoneId)
          ) {
            this.searchError.set(
              'Cette zone n’est pas disponible pour votre compte. Choisissez une zone autorisée.',
            );
            this.zoneControl.setValue('');
            return EMPTY;
          }
          this.criteria.set(criteria);
          return defer(() => {
            this.searching.set(true);
            return this.tripsApi.search(criteria).pipe(
              tap((page) => {
                if (criteria.page > 0 && criteria.page >= page.totalPages) {
                  void this.router.navigate([], {
                    relativeTo: this.route,
                    queryParams: {
                      zoneId: criteria.zoneId,
                      page: Math.max(0, page.totalPages - 1),
                    },
                    replaceUrl: true,
                  });
                } else this.results.set(page);
              }),
              catchError((error: unknown) => {
                this.searchError.set(
                  error instanceof ApiFailure
                    ? error.message
                    : 'Impossible de charger les trajets. Réessayez.',
                );
                return EMPTY;
              }),
              finalize(() => this.searching.set(false)),
            );
          });
        }),
        takeUntilDestroyed(this.destroy),
      )
      .subscribe();

    this.zoneReload
      .pipe(
        startWith(undefined),
        switchMap(() => {
          this.zonesReady.next(false);
          this.zones.set([]);
          this.zonesError.set('');
          this.loadingZones.set(true);
          return this.zoneApi.all().pipe(
            tap((zones) => {
              this.zones.set(zones);
              this.zonesReady.next(true);
            }),
            catchError((error: unknown) => {
              this.zonesError.set(
                error instanceof ApiFailure
                  ? error.message
                  : 'Impossible de charger vos zones. Réessayez.',
              );
              return EMPTY;
            }),
            finalize(() => this.loadingZones.set(false)),
          );
        }),
        takeUntilDestroyed(this.destroy),
      )
      .subscribe();
  }
  protected retryZones(): void {
    this.zoneReload.next();
  }
  protected search(): void {
    const zoneId = this.zoneControl.value ? Number(this.zoneControl.value) : null;
    const current = readTripCriteria(this.route.snapshot.queryParamMap);
    if (current?.zoneId === zoneId && current.page === 0) this.refresh.next();
    else
      void this.router.navigate([], { relativeTo: this.route, queryParams: { zoneId, page: 0 } });
  }
  protected reload(): void {
    this.refresh.next();
  }
  protected goToPage(page: number): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { ...this.criteria(), page },
    });
  }
  protected zoneName(id: number): string {
    return this.zones().find((zone) => zone.zoneId === id)?.nom ?? `Zone n° ${id}`;
  }
}
