import { Injectable, computed, effect, signal } from '@angular/core';
import { Locale, TRANSLATIONS, TranslationKey, TranslationParams } from './translations';

const STORAGE_KEY = 'gestiontransport_language';
const SUPPORTED_LOCALES: Locale[] = ['fr', 'en'];

@Injectable({ providedIn: 'root' })
export class LanguageService {
  private readonly localeState = signal<Locale>(this.readInitialLocale());

  readonly locale = computed(() => this.localeState());

  constructor() {
    effect(() => {
      const locale = this.localeState();

      if (typeof document !== 'undefined') {
        document.documentElement.lang = locale;
        document.documentElement.dir = 'ltr';
        document.querySelector<HTMLMetaElement>('meta[name="description"]')?.setAttribute('content', this.t('seo.description'));
      }

      try {
        localStorage.setItem(STORAGE_KEY, locale);
      } catch {
        // Ignore storage failures.
      }
    });
  }

  setLocale(locale: Locale): void {
    this.localeState.set(locale);
  }

  t(key: TranslationKey, params: TranslationParams = {}): string {
    const locale = this.localeState();
    const template = TRANSLATIONS[locale][key] ?? TRANSLATIONS.fr[key] ?? key;

    return template.replace(/\{(\w+)\}/g, (_, token: string) => {
      const value = params[token];
      return value === undefined || value === null ? '' : String(value);
    });
  }

  private readInitialLocale(): Locale {
    try {
      const stored = localStorage.getItem(STORAGE_KEY) as Locale | null;
      if (stored && SUPPORTED_LOCALES.includes(stored)) {
        return stored;
      }
    } catch {
      // Ignore storage access failures.
    }

    return 'fr';
  }
}
