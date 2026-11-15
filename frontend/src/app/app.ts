import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';
import { Router, RouterModule } from '@angular/router';
import { AdminSessionService } from './core/auth/admin-session.service';
import { VoyageurSessionService } from './core/auth/voyageur-session.service';
import { LanguageService } from './core/i18n/language.service';
import { LanguageSwitcherComponent } from './shared/components/language-switcher.component';

@Component({
  selector: 'app-root',
  imports: [
    CommonModule,
    RouterModule,
    MatToolbarModule,
    MatButtonModule,
    LanguageSwitcherComponent,
  ],
  template: `
    <a class="skip-link" href="#main-content">{{ language.t('ui.skip') }}</a>
    <header class="app-header" (keydown.escape)="menuOuvert.set(false); menuButton.focus()">
      <div class="app-header-inner">
        <a routerLink="/" class="app-logo">
          <span class="app-logo-badge" aria-hidden="true">↗</span>
          <span class="app-logo-text">GestionTransport-Multiple</span>
        </a>

        <button
          #menuButton
          type="button"
          class="menu-toggle"
          (click)="menuOuvert.set(!menuOuvert())"
          [attr.aria-expanded]="menuOuvert()"
          aria-controls="navigation"
        >
          {{ language.t('ui.menu') }}
        </button>
        <nav
          id="navigation"
          [attr.aria-label]="language.t('ui.mainNav')"
          class="app-nav"
          [class.open]="menuOuvert()"
          (click)="menuOuvert.set(false)"
        >
          <a
            routerLink="/"
            routerLinkActive="nav-active"
            ariaCurrentWhenActive="page"
            [routerLinkActiveOptions]="{ exact: true }"
            class="nav-link"
          >
            {{ navText().home }}
          </a>

          <a
            [routerLink]="voyageurSession.isAuthenticated() ? '/voyageur/trajets' : '/'"
            [fragment]="voyageurSession.isAuthenticated() ? undefined : 'trajets'"
            class="nav-link"
            >{{ language.t('ui.searchNav') }}</a
          >
          @if (!voyageurSession.isAuthenticated() && !adminSession.isAuthenticated()) {
            <a routerLink="/admin/login" class="nav-link">{{ language.t('premium.adminAccess') }}</a>
            <a routerLink="/voyageur" class="nav-btn-outline">{{ navText().voyageur }}</a>
          }

          @if (voyageurSession.isAuthenticated()) {
            <div class="nav-divider"></div>
            <a
              routerLink="/voyageur/profile"
              routerLinkActive="nav-active"
              ariaCurrentWhenActive="page"
              class="nav-link"
              >{{ navText().profile }}</a
            >
            <a
              routerLink="/voyageur/trajets"
              routerLinkActive="nav-active"
              ariaCurrentWhenActive="page"
              class="nav-link"
              >{{ navText().trajets }}</a
            >
            <a
              routerLink="/voyageur/reservations"
              routerLinkActive="nav-active"
              ariaCurrentWhenActive="page"
              class="nav-link"
              >{{ navText().reservations }}</a
            >
            <a
              routerLink="/voyageur/payments"
              routerLinkActive="nav-active"
              ariaCurrentWhenActive="page"
              class="nav-link"
              >{{ navText().payments }}</a
            >
            <div class="nav-divider"></div>
            @if (voyageurSession.voyageur()?.peutProposerTrajet) {
              <a routerLink="/voyageur/trajets/new" class="nav-btn-create">{{
                navText().create
              }}</a>
            }
            <div class="nav-divider"></div>
            <button class="nav-btn-logout" type="button" (click)="logoutVoyageur()">
              {{ navText().logout }}
            </button>
          }

          @if (adminSession.isAuthenticated()) {
            <div class="nav-divider"></div>
            <a
              routerLink="/admin"
              routerLinkActive="nav-active"
              ariaCurrentWhenActive="page"
              [routerLinkActiveOptions]="{ exact: true }"
              class="nav-link"
              >{{ language.t(adminSession.isGlobalAdmin() ? 'premium.global' : 'premium.local') }}</a
            >
            <a
              routerLink="/admin/voyageurs"
              routerLinkActive="nav-active"
              ariaCurrentWhenActive="page"
              class="nav-link"
              >{{ navText().voyageurs }}</a
            >
            <a
              routerLink="/admin/trajets"
              routerLinkActive="nav-active"
              ariaCurrentWhenActive="page"
              class="nav-link"
              >{{ navText().trajets }}</a
            >
            <a
              routerLink="/admin/agences"
              routerLinkActive="nav-active"
              ariaCurrentWhenActive="page"
              class="nav-link"
              >{{ navText().agences }}</a
            >
            <a
              routerLink="/admin/vehicules"
              routerLinkActive="nav-active"
              ariaCurrentWhenActive="page"
              class="nav-link"
              >{{ navText().vehicules }}</a
            >
            <a
              routerLink="/admin/indisponibilites"
              routerLinkActive="nav-active"
              ariaCurrentWhenActive="page"
              class="nav-link"
              >{{ navText().indisponibilites }}</a
            >
            <div class="nav-divider"></div>
            <button class="nav-btn-logout" type="button" (click)="logoutAdmin()">
              {{ navText().logout }}
            </button>
          }
        </nav>

        <app-language-switcher class="app-lang-switcher"></app-language-switcher>
      </div>
    </header>

    @if (showJourneyBanner) {
      <section class="journey-banner" [class.journey-banner-compact]="adminSession.isAuthenticated() || router.url.startsWith('/admin/login') || router.url === '/voyageur'" [attr.aria-label]="language.t('design.bannerLabel')">
        <div class="journey-banner-copy">
          <p class="journey-eyebrow">GestionTransport-Multiple</p>
          <p class="journey-banner-title">{{ adminSession.isAuthenticated() ? language.t(adminSession.isGlobalAdmin() ? 'premium.global' : 'premium.local') : language.t('design.bannerTitle') }}</p>
          <p>{{ language.t(adminSession.isAuthenticated() ? 'design.adminSubtitle' : 'design.bannerSubtitle') }}</p>
          <a [routerLink]="adminSession.isAuthenticated() ? '/admin/trajets' : voyageurSession.isAuthenticated() ? '/voyageur/trajets' : '/'"
             [fragment]="!adminSession.isAuthenticated() && !voyageurSession.isAuthenticated() ? 'trajets' : undefined"
             class="journey-action">{{ language.t(adminSession.isAuthenticated() ? 'nav.trajets' : 'transport.search') }} <span aria-hidden="true">→</span></a>
        </div>
        <img src="images/car-bruxelles-640.webp" width="640" height="360" alt="" aria-hidden="true" loading="lazy" decoding="async" />
      </section>
    }
    <main id="main-content" tabindex="-1" class="app-main-shell">
      @if (adminSession.isAuthenticated() && router.url.startsWith('/admin') && !router.url.startsWith('/admin/login')) {
        <aside class="admin-navigation" [attr.aria-label]="language.t('premium.adminAccess')">
          <strong>{{ language.t(adminSession.isGlobalAdmin() ? 'premium.global' : 'premium.local') }}</strong>
          <button #adminMenuButton class="admin-menu-toggle" type="button" (click)="adminMenuOpen.set(!adminMenuOpen())" [attr.aria-expanded]="adminMenuOpen()" aria-controls="admin-navigation-links">{{ language.t('design.adminMenu') }}</button>
          <nav id="admin-navigation-links" [class.open]="adminMenuOpen()" (click)="adminMenuOpen.set(false)" (keydown.escape)="adminMenuOpen.set(false); adminMenuButton.offsetParent ? adminMenuButton.focus() : null">
            <a routerLink="/admin" routerLinkActive="selected" [routerLinkActiveOptions]="{exact:true}" ariaCurrentWhenActive="page">{{ navText().dashboard }}</a>
            <a routerLink="/admin/agences" routerLinkActive="selected" ariaCurrentWhenActive="page">{{ navText().agences }}</a>
            <a routerLink="/admin/vehicules" routerLinkActive="selected" ariaCurrentWhenActive="page">{{ navText().vehicules }}</a>
            <a routerLink="/admin/voyageurs" routerLinkActive="selected" ariaCurrentWhenActive="page">{{ language.t('premium.peopleRestrictions') }}</a>
            <a routerLink="/admin/trajets" routerLinkActive="selected" ariaCurrentWhenActive="page">{{ language.t('premium.tripsBookings') }}</a>
            <a routerLink="/admin/indisponibilites" routerLinkActive="selected" ariaCurrentWhenActive="page">{{ navText().indisponibilites }}</a>
          </nav>
        </aside>
      }
      <router-outlet></router-outlet>
    </main>
    <footer class="app-footer">
      <div class="footer-inner">
        <div>
          <strong>GestionTransport-Multiple</strong>
          <p>{{ language.t('ui.footerTagline') }}</p>
        </div>
        <div class="footer-links">
          <a routerLink="/">{{ navText().home }}</a><a routerLink="/voyageur">{{
            language.t('transport.myArea')
          }}</a
          ><a routerLink="/admin/login">{{ language.t('transport.admin') }}</a>
        </div>
      </div>
    </footer>
  `,
})
export class App {
  readonly adminMenuOpen = signal(false);
  get showJourneyBanner(): boolean { return this.router.url.split(/[?#]/)[0] !== '/'; }
  readonly menuOuvert = signal(false);
  readonly adminSession = inject(AdminSessionService);
  readonly voyageurSession = inject(VoyageurSessionService);
  readonly language = inject(LanguageService);
  readonly router = inject(Router);
  readonly navText = computed(() => ({
    home: this.language.t('nav.home'),
    admin: this.language.t('nav.admin'),
    voyageur: this.language.t('nav.voyageur'),
    profile: this.language.t('nav.profile'),
    trajets: this.language.t('nav.trajets'),
    dashboard: this.language.t('nav.dashboard'),
    voyageurs: this.language.t('nav.voyageurs'),
    agences: this.language.t('nav.agences'),
    vehicules: this.language.t('nav.vehicules'),
    indisponibilites: this.language.t('nav.indisponibilites'),
    reservations: this.language.t('nav.reservations'),
    payments: this.language.t('nav.payments'),
    create: this.language.t('nav.create'),
    public: this.language.t('nav.public'),
    private: this.language.t('nav.private'),
    logout: this.language.t('nav.logout'),
  }));

  logoutAdmin(): void {
    this.adminSession.clearSession();
    this.router.navigateByUrl('/admin/login');
  }

  logoutVoyageur(): void {
    this.voyageurSession.clearVoyageur();
    this.router.navigateByUrl('/voyageur');
  }
}
