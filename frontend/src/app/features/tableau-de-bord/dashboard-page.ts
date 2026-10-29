import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { AuthService } from '../../core/auth/auth-service';
import { Icon, IconName } from '../../shared/components/icon';
import { ClientCategory } from '../../shared/models/auth.models';
import { RouterLink } from '@angular/router';

const categoryNames: Record<ClientCategory, string> = {
  VIP: 'VIP',
  LOCAL: 'Local',
  OCCASIONNEL: 'Occasionnel',
};
@Component({
  imports: [Icon, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard-page.html',
})
export class DashboardPage {
  protected readonly auth = inject(AuthService);
  protected readonly categoryNames = categoryNames;
  protected readonly modules: { icon: IconName; title: string; text: string; path?: string }[] = [
    {
      icon: 'search',
      title: 'Rechercher un trajet',
      text: 'Explorer les trajets accessibles dans vos zones.',
      path: '/trajets',
    },
    {
      icon: 'ticket',
      title: 'Mes réservations',
      text: 'Retrouver vos places et suivre leur confirmation.',
    },
    {
      icon: 'calendar',
      title: 'Mes trajets',
      text: 'Créer et organiser vos prochains déplacements.',
    },
    {
      icon: 'wallet',
      title: 'Mes paiements',
      text: 'Consulter vos paiements et remboursements simulés.',
    },
  ];
}
