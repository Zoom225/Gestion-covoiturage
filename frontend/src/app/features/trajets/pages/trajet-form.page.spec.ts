import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { CovoiturageApi } from '../../../core/services/covoiturage-api';
import { TrajetFormPage } from './trajet-form.page';

describe('URL de modification de trajet', () => {
  it.each(['0', '-1', 'abc', '9007199254740992'])(
    'refuse %s sans charger ni créer un trajet',
    async (id) => {
      const api = { zones: vi.fn(), trip: vi.fn(), saveTrip: vi.fn() };
      TestBed.configureTestingModule({
        providers: [
          provideRouter([{ path: 'trajets/:id/modifier', component: TrajetFormPage }]),
          { provide: CovoiturageApi, useValue: api },
        ],
      });
      const harness = await RouterTestingHarness.create('/trajets/' + id + '/modifier');
      expect(harness.routeNativeElement?.textContent).toContain('Identifiant de trajet invalide');
      expect(
        harness.routeNativeElement?.querySelector<HTMLButtonElement>('button[type=submit]')
          ?.disabled,
      ).toBe(true);
      expect(api.zones).not.toHaveBeenCalled();
      expect(api.trip).not.toHaveBeenCalled();
      expect(api.saveTrip).not.toHaveBeenCalled();
    },
  );
});
