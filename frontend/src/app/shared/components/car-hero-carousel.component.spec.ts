import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CarHeroCarouselComponent } from './car-hero-carousel.component';
import { LanguageService } from '../../core/i18n/language.service';

describe('CarHeroCarouselComponent', () => {
  let fixture: ComponentFixture<CarHeroCarouselComponent>;
  let motion: {matches: boolean; addEventListener: ReturnType<typeof vi.fn>; removeEventListener: ReturnType<typeof vi.fn>};

  beforeEach(async () => {
    localStorage.setItem('gestiontransport_language','fr');
    Object.defineProperty(document,'hidden',{configurable:true,value:false});
    motion={matches:false,addEventListener:vi.fn(),removeEventListener:vi.fn()};
    vi.stubGlobal('matchMedia',vi.fn(()=>motion));
    await TestBed.configureTestingModule({imports:[CarHeroCarouselComponent]}).compileComponents();
    vi.useFakeTimers();
  });

  afterEach(() => {
    fixture?.destroy(); TestBed.resetTestingModule(); vi.useRealTimers();
    vi.restoreAllMocks(); vi.unstubAllGlobals(); Reflect.deleteProperty(document,'hidden');
  });

  function create(): CarHeroCarouselComponent {
    fixture=TestBed.createComponent(CarHeroCarouselComponent); fixture.detectChanges();
    return fixture.componentInstance;
  }
  function advance(ms=6000): void { vi.advanceTimersByTime(ms); fixture.detectChanges(); }
  function click(selector:string): void { fixture.nativeElement.querySelector(selector).click(); fixture.detectChanges(); }

  it('présente quatre photos et tourne toutes les six secondes, avec retour au début', () => {
    const component=create();
    expect(component.slides).toHaveLength(4);
    expect(component.current()).toBe(0);
    advance(5999); expect(component.current()).toBe(0);
    advance(1); expect(component.current()).toBe(1);
    advance(18000); expect(component.current()).toBe(0);
  });

  it('met en pause lors de la navigation manuelle et expose la pagination active', () => {
    const component=create(); click('.carousel-next');
    expect(component.current()).toBe(1);
    expect(component.canRotate()).toBe(false);
    advance(12000); expect(component.current()).toBe(1);
    click('.carousel-previous'); expect(component.current()).toBe(0);
    click('.carousel-dots button:last-child'); expect(component.current()).toBe(3);
    expect(fixture.nativeElement.querySelector('.carousel-dots button:last-child').getAttribute('aria-current')).toBe('true');
    expect(component.announcement()).toBe('Photographie 4 sur 4');
  });

  it('permet une pause explicite puis une reprise avec une nouvelle période complète', () => {
    const component=create(); click('.carousel-pause');
    advance(12000); expect(component.current()).toBe(0);
    click('.carousel-pause');
    advance(5999); expect(component.current()).toBe(0);
    advance(1); expect(component.current()).toBe(1);
  });

  it('arrête la rotation à l’interaction et ne reprend que sur demande', () => {
    const component=create();
    fixture.nativeElement.querySelector('section').dispatchEvent(new Event('pointerenter'));
    fixture.detectChanges(); advance(12000); expect(component.current()).toBe(0);
    click('.carousel-pause'); advance(); expect(component.current()).toBe(1);
    fixture.nativeElement.querySelector('section').dispatchEvent(new Event('focusin'));
    fixture.detectChanges(); advance(12000); expect(component.current()).toBe(1);
  });

  it('suspend la rotation pour un onglet caché sans annuler la pause utilisateur', () => {
    const component=create();
    Object.defineProperty(document,'hidden',{configurable:true,value:true});
    document.dispatchEvent(new Event('visibilitychange')); fixture.detectChanges();
    advance(12000); expect(component.current()).toBe(0);
    Object.defineProperty(document,'hidden',{configurable:true,value:false});
    document.dispatchEvent(new Event('visibilitychange')); fixture.detectChanges();
    advance(); expect(component.current()).toBe(1);
    component.pause(); fixture.detectChanges();
    document.dispatchEvent(new Event('visibilitychange')); fixture.detectChanges();
    advance(12000); expect(component.current()).toBe(1);
  });

  it('respecte le mouvement réduit et les changements de préférence', () => {
    motion.matches=true; const component=create();
    expect(fixture.nativeElement.querySelector('.carousel-pause').disabled).toBe(true);
    component.toggleRotation(); fixture.detectChanges(); advance(18000); expect(component.current()).toBe(0);
    const change=motion.addEventListener.mock.calls.find(call=>call[0]==='change')![1];
    change({matches:false}); fixture.detectChanges(); advance(); expect(component.current()).toBe(1);
    change({matches:true}); fixture.detectChanges(); advance(12000); expect(component.current()).toBe(1);
  });

  it('gère les flèches, Début et Fin au clavier sans changer le focus', () => {
    const component=create();
    const section=fixture.nativeElement.querySelector('section');
    section.dispatchEvent(new KeyboardEvent('keydown',{key:'ArrowLeft',bubbles:true}));fixture.detectChanges();
    expect(component.current()).toBe(3);
    section.dispatchEvent(new KeyboardEvent('keydown',{key:'Home',bubbles:true}));fixture.detectChanges();
    expect(component.current()).toBe(0);
    section.dispatchEvent(new KeyboardEvent('keydown',{key:'End',bubbles:true}));fixture.detectChanges();
    expect(component.current()).toBe(3);
    advance(12000);expect(component.current()).toBe(3);
  });

  it('détruit le timer et les abonnements DOM', () => {
    const remove=vi.spyOn(document,'removeEventListener');
    const component=create(); fixture.destroy();
    vi.advanceTimersByTime(18000);expect(component.current()).toBe(0);
    expect(motion.removeEventListener).toHaveBeenCalledWith('change',expect.any(Function));
    expect(remove).toHaveBeenCalledWith('visibilitychange',expect.any(Function));
  });

  it('précharge seulement la suivante et propose un secours local en cas d’erreur photo', () => {
    const component=create();
    expect(component.prepared().size).toBe(1);
    component.onImageLoad(0);component.onImageLoad(1);fixture.detectChanges();
    expect(component.prepared().size).toBe(2);
    component.onImageError(0);fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('img').getAttribute('src')).toBe('images/mobilite-transport.svg');
    expect(fixture.nativeElement.querySelector('picture source')).toBeTruthy(); // la photo suivante conserve son source responsive
    expect(fixture.nativeElement.querySelector('.carousel-photo:first-child source')).toBeNull();
    TestBed.inject(LanguageService).setLocale('en');fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.carousel-next').getAttribute('aria-label')).toBe('Next photograph');
  });
});
