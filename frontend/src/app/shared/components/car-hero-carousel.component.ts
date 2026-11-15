import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { LanguageService } from '../../core/i18n/language.service';

@Component({
  selector: 'app-car-hero-carousel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [`
:host { display: block; }
.car-hero { position: relative; isolation: isolate; background: var(--ui-navy); color: var(--ui-surface); min-height: 660px; overflow: clip; }
.carousel-photo, .hero-shade, .carousel-photos { position: absolute; inset: 0; }
.carousel-photo { opacity: 0; transition: opacity 850ms ease; pointer-events: none; }
.carousel-photo.active { opacity: 1; }
.carousel-photo picture, .carousel-photo img { display: block; width: 100%; height: 100%; }
.carousel-photo img { object-fit: cover; object-position: center; }
.hero-shade { background: linear-gradient(90deg, rgb(11 31 51 / 96%) 0%, rgb(11 31 51 / 88%) 36%, rgb(11 31 51 / 32%) 73%, rgb(11 31 51 / 12%)), linear-gradient(0deg, rgb(11 31 51 / 88%), transparent 45%); }
.hero-inner, .hero-footer { position: relative; width: min(1200px, calc(100% - 3rem)); margin-inline: auto; }
.hero-inner { padding: 5rem 0 13rem; }
.hero-copy { max-width: 650px; }
.hero-eyebrow { display: flex; gap: .65rem; align-items: center; color: var(--ui-hero-accent); text-transform: uppercase; letter-spacing: .15em; font-size: .8rem; font-weight: 700; margin: 0 0 1.5rem; }
.hero-eyebrow > span { width: 8px; height: 8px; border-radius: 50%; background: var(--ui-warm); }
h1 { font-size: clamp(2.75rem, 4.5vw, 3.75rem); font-weight: 800; line-height: 1.1; letter-spacing: -.045em; color: var(--ui-surface); margin: 0; }
h1 em { font-style: normal; color: var(--ui-hero-accent); }
.hero-lead { font-size: clamp(1rem, 1.5vw, 1.15rem); max-width: 440px; line-height: 1.8; color: var(--ui-surface); margin: 1.5rem 0 2rem; }
.hero-actions, .hero-trust { display: flex; flex-wrap: wrap; gap: .85rem; }
.hero-actions a { display: inline-flex; min-height: 50px; align-items: center; justify-content: center; gap: 1rem; padding: .85rem 1.3rem; border-radius: var(--ui-radius-button); font-weight: 700; font-size: .95rem; text-decoration: none; transition: background var(--ui-motion), box-shadow var(--ui-motion), transform var(--ui-motion); }
.hero-primary { color: var(--ui-surface); background: var(--ui-primary); border: 1px solid var(--ui-primary); box-shadow: 0 8px 24px rgb(0 0 0 / 18%); }
.hero-primary:hover { background: var(--ui-primary-strong); transform: translateY(-2px); }
.hero-secondary { color: var(--ui-surface); border: 1px solid rgb(255 255 255 / 55%); background: rgb(11 31 51 / 65%); }
.hero-secondary:hover { background: var(--ui-navy); border-color: var(--ui-surface); }
.hero-trust { margin-top: 2rem; font-size: .85rem; gap: .65rem 1.4rem; color: var(--ui-surface); }
.hero-footer { position: absolute; inset-inline: 0; bottom: 5rem; display: flex; align-items: end; justify-content: space-between; flex-wrap: wrap; gap: 1rem; }
.photo-credit { margin: 0; display: grid; gap: .25rem; font-size: .75rem; line-height: 1.7; color: var(--ui-surface); }
.photo-credit > span { color: var(--ui-hero-accent); font-weight: 650; }
.photo-credit a { color: var(--ui-surface); text-decoration-color: rgb(255 255 255 / 50%); }
.carousel-controls, .carousel-arrows, .carousel-dots { display: flex; align-items: center; }
.carousel-controls { flex-wrap: wrap; gap: .75rem; }
.carousel-arrows { gap: .3rem; }
.carousel-controls button { display: grid; place-items: center; width: 44px; min-width: 44px; height: 44px; padding: 0; border-radius: 50%; cursor: pointer; color: var(--ui-surface); border: 1px solid rgb(255 255 255 / 45%); background: rgb(11 31 51 / 85%); transition: background var(--ui-motion), border-color var(--ui-motion); }
.carousel-controls button:hover:not(:disabled) { border-color: var(--ui-surface); background: var(--ui-primary-strong); }
.carousel-controls button:disabled { cursor: not-allowed; opacity: .65; }
.carousel-dots button { background: transparent; border-color: transparent; border-radius: 10px; }
.carousel-dots span { width: 7px; height: 7px; border-radius: 9px; background: var(--ui-surface); transition: width var(--ui-motion), background var(--ui-motion); }
.carousel-dots [aria-current=true] span { width: 22px; background: var(--ui-hero-accent); }
.car-hero :focus-visible { outline-color: var(--ui-hero-accent); outline-offset: 4px; }
.visually-hidden { position: absolute; width: 1px; height: 1px; margin: -1px; overflow: hidden; clip-path: inset(50%); white-space: nowrap; }
@media(max-width: 700px) {
  .hero-inner, .hero-footer { width: calc(100% - 2rem); }
  .hero-inner { padding: 3rem 0 16rem; }
  .car-hero { min-height: 720px; }
  h1 { font-size: clamp(2.3rem, 8.5vw, 3.2rem); }
  .hero-copy { max-width: 540px; }
  .hero-shade { background: linear-gradient(180deg, rgb(11 31 51 / 95%), rgb(11 31 51 / 83%) 44%, rgb(11 31 51 / 48%) 73%, rgb(11 31 51 / 93%)); }
  .hero-lead { max-width: 35ch; }
  .hero-footer { bottom: 4.5rem; align-items: start; flex-direction: column; gap: .75rem; }
  .hero-trust { gap: .5rem 1rem; font-size: .8rem; }
}
@media(max-width: 360px) {
  .hero-inner { padding-bottom: 20rem; }
  .hero-eyebrow { font-size: .72rem; letter-spacing: .08em; }
  .hero-actions a { width: 100%; }
}
@media(prefers-reduced-motion: reduce) {
  .carousel-photo, .hero-actions a, .carousel-controls button, .carousel-dots span { transition: none; transform: none; }
}

  `],
  template: `
    <section class="car-hero" role="region" [attr.aria-label]="language.t('carousel.label')"
      [attr.aria-roledescription]="language.t('carousel.description')"
      (pointerenter)="pause()" (focusin)="pause()" (keydown)="onKey($event)">
      <div class="carousel-photos" aria-live="off">
        @for (slide of slides; track slide.name; let i = $index) {
          @if (prepared().has(i)) {
            <div class="carousel-photo" [class.active]="current() === i" [attr.aria-hidden]="current() !== i"
              role="group" [attr.aria-roledescription]="language.t('carousel.slide')"
              [attr.aria-label]="language.t('carousel.position', {current: i + 1, total: slides.length})">
              <picture>
                @if (!failed().has(i)) {
                  <source media="(max-width: 640px)" [srcset]="'images/car-' + slide.name + '-mobile.webp'" />
                }
                <img [src]="failed().has(i) ? 'images/mobilite-transport.svg' : 'images/car-' + slide.name + '-1024.webp'"
                  [attr.srcset]="failed().has(i) ? null : imageSet(slide.name)" sizes="100vw"
                  width="1920" height="1080" [alt]="language.t(failed().has(i) ? 'design.carAlt' : slide.alt)"
                  [attr.loading]="i === 0 ? 'eager' : 'lazy'" [attr.fetchpriority]="i === 0 ? 'high' : 'low'"
                  decoding="async" (load)="onImageLoad(i)" (error)="onImageError(i)" />
              </picture>
            </div>
          }
        }
      </div>
      <div class="hero-shade" aria-hidden="true"></div>
      <div class="hero-inner">
        <div class="hero-copy">
          <p class="hero-eyebrow"><span aria-hidden="true"></span>{{ language.t('transport.eyebrow') }}</p>
          <h1>{{ language.t('transport.together') }} <em>{{ language.t('transport.simply') }}</em></h1>
          <p class="hero-lead">{{ language.t('carousel.subtitle') }}</p>
          <div class="hero-actions">
            <a href="#recherche" class="hero-primary">{{ language.t('transport.search') }} <span aria-hidden="true">↗</span></a>
            <a href="#fonctionnement" class="hero-secondary">{{ language.t('carousel.discover') }}</a>
          </div>
          <div class="hero-trust">
            <span>✓ {{ language.t('transport.driverKnown') }}</span>
            <span>✓ {{ language.t('transport.maximum') }}</span>
            <span>✓ {{ language.t('transport.price') }}</span>
          </div>
        </div>
      </div>
      <div class="hero-footer">
        <p class="photo-credit">
          <span>{{ language.t('carousel.illustration') }}</span>
          <a [href]="slides[current()].url" target="_blank" rel="noopener noreferrer">{{ slides[current()].author }} · Pexels <span aria-hidden="true">↗</span></a>
        </p>
        <div class="carousel-controls" role="group" [attr.aria-label]="language.t('carousel.controls')">
          <div class="carousel-arrows">
            <button type="button" class="carousel-previous" (click)="select(current() - 1)" [attr.aria-label]="language.t('carousel.previous')"><span aria-hidden="true">←</span></button>
            <button type="button" class="carousel-pause" (click)="toggleRotation()" [disabled]="reducedMotion()"
              [attr.aria-label]="language.t(reducedMotion() ? 'carousel.motionOff' : canRotate() ? 'carousel.pause' : 'carousel.play')">
              <span aria-hidden="true">{{ canRotate() ? 'Ⅱ' : '▶' }}</span>
            </button>
            <button type="button" class="carousel-next" (click)="select(current() + 1)" [attr.aria-label]="language.t('carousel.next')"><span aria-hidden="true">→</span></button>
          </div>
          <div class="carousel-dots" role="group" [attr.aria-label]="language.t('carousel.pagination')">
            @for (slide of slides; track slide.name; let i = $index) {
              <button type="button" (click)="select(i)" [attr.aria-current]="current() === i ? 'true' : null"
                [attr.aria-label]="language.t('carousel.goTo', {current: i + 1, total: slides.length})"><span aria-hidden="true"></span></button>
            }
          </div>
        </div>
      </div>
      <p class="visually-hidden" role="status" aria-live="polite" aria-atomic="true">{{ announcement() }}</p>
    </section>
  `,
})
export class CarHeroCarouselComponent {
  readonly language = inject(LanguageService);
  private readonly document = inject(DOCUMENT);
  private readonly destroyRef = inject(DestroyRef);
  readonly slides = [
    {name:'route', alt:'carousel.roadAlt', author:'Kelvin Linhares', url:'https://www.pexels.com/photo/sleek-silver-car-on-scenic-country-road-28502253/'},
    {name:'famille', alt:'carousel.familyAlt', author:'Mikhail Nilov', url:'https://www.pexels.com/photo/a-family-standing-near-parked-car-while-looking-at-the-camera-8323226/'},
    {name:'partage', alt:'carousel.sharedAlt', author:'Clément Proust', url:'https://www.pexels.com/photo/people-traveling-on-a-road-18378381/'},
    {name:'bruxelles', alt:'carousel.brusselsAlt', author:'Hakan Yasar', url:'https://www.pexels.com/photo/cars-parked-along-street-14599371/'},
  ] as const;
  readonly current = signal(0);
  readonly paused = signal(false);
  readonly hidden = signal(this.document.hidden);
  readonly reducedMotion = signal(false);
  readonly prepared = signal<ReadonlySet<number>>(new Set([0]));
  readonly failed = signal<ReadonlySet<number>>(new Set());
  readonly announcement = signal('');
  readonly canRotate = computed(() => !this.paused() && !this.hidden() && !this.reducedMotion());

  constructor() {
    const motion = typeof window.matchMedia === 'function' ? window.matchMedia('(prefers-reduced-motion: reduce)') : null;
    this.reducedMotion.set(motion?.matches ?? false);
    const onMotion = (event: MediaQueryListEvent) => this.reducedMotion.set(event.matches);
    const onVisibility = () => this.hidden.set(this.document.hidden);
    motion?.addEventListener('change', onMotion);
    this.document.addEventListener('visibilitychange', onVisibility);
    this.destroyRef.onDestroy(() => {
      motion?.removeEventListener('change', onMotion);
      this.document.removeEventListener('visibilitychange', onVisibility);
    });
    effect((onCleanup) => {
      if (!this.canRotate()) return;
      const timer = window.setInterval(() => this.advance(this.current() + 1), 6000);
      onCleanup(() => window.clearInterval(timer));
    });
  }

  imageSet(name: string): string {
    return [640,1024,1920].map(width => `images/car-${name}-${width}.webp ${width}w`).join(', ');
  }

  pause(): void { this.paused.set(true); }

  toggleRotation(): void {
    if (!this.reducedMotion()) this.paused.set(this.canRotate());
  }

  select(index: number): void {
    this.pause();
    this.advance(index);
    this.announcement.set(this.language.t('carousel.position', {current:this.current()+1, total:this.slides.length}));
  }

  onKey(event: KeyboardEvent): void {
    const index = event.key === 'ArrowLeft' ? this.current()-1 : event.key === 'ArrowRight' ? this.current()+1
      : event.key === 'Home' ? 0 : event.key === 'End' ? this.slides.length-1 : null;
    if (index === null) return;
    event.preventDefault(); this.select(index);
  }

  onImageLoad(index: number): void {
    if (index === this.current()) this.prepare((index + 1) % this.slides.length);
  }

  onImageError(index: number): void {
    this.failed.update(values => new Set([...values,index]));
  }

  private advance(index: number): void {
    const next = (index + this.slides.length) % this.slides.length;
    this.prepare(next); this.current.set(next);
  }

  private prepare(index: number): void {
    if (!this.prepared().has(index)) this.prepared.update(values => new Set([...values,index]));
  }
}
