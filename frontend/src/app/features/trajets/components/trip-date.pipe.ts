import { Pipe, PipeTransform } from '@angular/core';

/** Intl respecte les fuseaux IANA et les changements d'heure, sans dépendance supplémentaire. */
@Pipe({ name: 'tripDate' })
export class TripDatePipe implements PipeTransform {
  transform(value: string, timeZone: string, format: 'date' | 'time' | 'full' = 'full'): string {
    try {
      const options: Intl.DateTimeFormatOptions = { timeZone, hourCycle: 'h23' };
      if (format !== 'time')
        Object.assign(options, { day: 'numeric', month: 'long', year: 'numeric' });
      if (format !== 'date') Object.assign(options, { hour: '2-digit', minute: '2-digit' });
      return new Intl.DateTimeFormat('fr-BE', options).format(new Date(value));
    } catch {
      return 'Horaire non disponible';
    }
  }
}
