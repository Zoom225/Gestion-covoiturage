import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Icon } from '../../../../shared/components/icon';
import { TrajetResponse, TripCriteria } from '../../models/trajet.model';
import { TripDatePipe } from '../trip-date.pipe';
import { TripStatusBadge } from '../trip-status';

@Component({
  selector: 'app-trip-card',
  imports: [RouterLink, Icon, TripDatePipe, TripStatusBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<article class="trip-card" [attr.aria-labelledby]="'trip-title-' + trip().trajetId">
    <div class="trip-card-top">
      <span class="trip-zone"><app-icon name="route" />{{ zoneName() }}</span
      ><app-trip-status [status]="trip().statut" />
    </div>
    <h3 [id]="'trip-title-' + trip().trajetId">
      {{ trip().origine || 'Départ non renseigné' }} <span aria-hidden="true">→</span>
      {{ trip().destination || 'Destination non renseignée' }}
    </h3>
    <p class="trip-type">
      {{ trip().type === 'PUBLIC' ? 'Trajet public' : 'Trajet privé' }} · Horaires :
      {{ trip().fuseauHoraire }}
    </p>
    <dl class="trip-times">
      <div>
        <dt>Départ</dt>
        <dd>
          <time [attr.datetime]="trip().depart">{{
            trip().depart | tripDate: trip().fuseauHoraire : 'time'
          }}</time
          ><span>{{ trip().depart | tripDate: trip().fuseauHoraire : 'date' }}</span>
        </dd>
      </div>
      <div>
        <dt>Arrivée</dt>
        <dd>
          <time [attr.datetime]="trip().arrivee">{{
            trip().arrivee | tripDate: trip().fuseauHoraire : 'time'
          }}</time
          ><span>{{ trip().arrivee | tripDate: trip().fuseauHoraire : 'date' }}</span>
        </dd>
      </div>
    </dl>
    <div class="trip-card-footer">
      <span>Places restantes non communiquées</span
      ><a
        class="text-link"
        [routerLink]="['/trajets', trip().trajetId]"
        [queryParams]="criteria()"
        [attr.aria-label]="
          'Voir le détail du trajet de ' +
          (trip().origine || 'départ non renseigné') +
          ' vers ' +
          (trip().destination || 'destination non renseignée')
        "
        >Voir le détail <app-icon name="arrow"
      /></a>
    </div>
  </article>`,
})
export class TripCard {
  readonly trip = input.required<TrajetResponse>();
  readonly zoneName = input.required<string>();
  readonly criteria = input.required<TripCriteria>();
}
