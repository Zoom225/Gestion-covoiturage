import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Icon } from '../../shared/components/icon';

interface StatusContent {
  code: string;
  title: string;
  text: string;
  action: string;
  link: string;
}
const content: Record<string, StatusContent> = {
  '404': {
    code: '404',
    title: 'Un petit détour ?',
    text: 'Cette page n’existe pas ou a changé d’adresse. Reprenons la route depuis l’accueil.',
    action: 'Retour à l’accueil',
    link: '/',
  },
  forbidden: {
    code: 'ACCÈS LIMITÉ',
    title: 'Vous n’avez pas accès à cet espace.',
    text: 'Votre compte ne dispose pas des droits nécessaires pour consulter cette page. Votre administrateur peut vous renseigner.',
    action: 'Retour à mon espace',
    link: '/tableau-de-bord',
  },
  unavailable: {
    code: 'CONNEXION AU SERVICE',
    title: 'La connexion prend un détour.',
    text: 'Le service DriveConnect n’est pas accessible pour le moment. Vérifiez votre connexion et réessayez. Si le problème persiste, contactez votre administrateur.',
    action: 'Réessayer la connexion',
    link: '/connexion',
  },
};
@Component({
  imports: [RouterLink, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<section class="status-page container">
    <div class="status-art" aria-hidden="true"><span></span><app-icon name="route" /></div>
    <p class="eyebrow">{{ page.code }}</p>
    <h1>{{ page.title }}</h1>
    <p class="status-description">{{ page.text }}</p>
    <a [routerLink]="page.link" class="btn btn-primary"
      >{{ page.action }}<app-icon name="arrow"
    /></a>
  </section>`,
})
export class StatusPage {
  protected readonly page =
    content[inject(ActivatedRoute).snapshot.data['kind'] as string] ?? content['404'];
}
