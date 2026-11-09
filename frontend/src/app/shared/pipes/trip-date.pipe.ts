import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'tripDate' })
export class TripDatePipe implements PipeTransform {
  transform(value: string, zone: string | null): string {
    return new Intl.DateTimeFormat('fr-BE', {
      dateStyle: 'short',
      timeStyle: 'short',
      timeZone: zone || 'UTC',
    }).format(new Date(value));
  }
}
