import { ParamMap } from '@angular/router';
import { TripCriteria, TRIPS_PAGE_SIZE } from './trajet.model';

export function positiveId(value: string | null): number | null {
  if (!value || !/^[1-9]\d*$/.test(value)) return null;
  const number = Number(value);
  return Number.isSafeInteger(number) ? number : null;
}

export function readTripCriteria(params: ParamMap): TripCriteria | null {
  const rawZone = params.get('zoneId');
  const zoneId = rawZone === null ? null : positiveId(rawZone);
  const rawPage = params.get('page') ?? '0';
  const page = Number(rawPage);
  if (
    (rawZone !== null && zoneId === null) ||
    !/^\d+$/.test(rawPage) ||
    !Number.isSafeInteger(page) ||
    page * TRIPS_PAGE_SIZE > 2_147_483_647
  )
    return null;
  return { zoneId, page };
}
