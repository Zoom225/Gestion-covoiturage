import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth-service';
import { Icon, IconName } from '../../shared/components/icon';
@Component({
  imports: [RouterLink, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './home-page.html',
})
export class HomePage {
  protected readonly auth = inject(AuthService);
  protected readonly features: { number: string; icon: IconName; title: string; text: string }[] = [
    {
      number: '01',
      icon: 'search',
      title: 'Trouvez votre trajet',
      text: 'Consultez les trajets proposés dans les zones auxquelles vous avez accès.',
    },
    {
      number: '02',
      icon: 'ticket',
      title: 'Prenez votre place',
      text: 'Demandez une réservation, puis confirmez votre place par un paiement simulé.',
    },
    {
      number: '03',
      icon: 'calendar',
      title: 'Gardez le fil',
      text: 'Organisez vos trajets et retrouvez vos réservations et vos paiements dans votre espace.',
    },
  ];
}
