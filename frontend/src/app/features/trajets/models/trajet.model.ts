export type TripStatus = 'PLANIFIE' | 'COMPLET' | 'ANNULE' | 'REALISE';
export interface TrajetResponse {
  trajetId: number;
  zoneId: number;
  vehiculeId: number;
  organisateurId: number;
  depart: string;
  arrivee: string;
  fuseauHoraire: string;
  type: 'PUBLIC' | 'PRIVE';
  statut: TripStatus;
  origine: string | null;
  destination: string | null;
  traiteJ1Le: string | null;
  verificationJ1Requise: boolean;
}
export interface TripCriteria {
  zoneId: number | null;
  page: number;
}
export const TRIPS_PAGE_SIZE = 12;
