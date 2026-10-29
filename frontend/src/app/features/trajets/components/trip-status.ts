import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TripStatus } from '../models/trajet.model';

const labels: Record<TripStatus, string> = {
  PLANIFIE: 'Planifié',
  COMPLET: 'Complet',
  ANNULE: 'Annulé',
  REALISE: 'Réalisé',
};
@Component({
  selector: 'app-trip-status',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template:
    '<span class="trip-status" [class]="\'trip-status status-\' + status().toLowerCase()">{{ labels[status()] }}</span>',
})
export class TripStatusBadge {
  readonly status = input.required<TripStatus>();
  protected readonly labels = labels;
}
