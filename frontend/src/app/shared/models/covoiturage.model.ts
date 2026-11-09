export interface PageResult<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
export interface Zone {
  zoneId: number;
  nom: string;
  actif: boolean;
  fuseauHoraire: string | null;
  ouverteOccasionnels: boolean;
}
export interface Vehicule {
  vehiculeId: number;
  zoneId: number;
  zoneNom: string;
  actif: boolean;
}
export interface Trajet {
  trajetId: number;
  zoneId: number;
  vehiculeId: number;
  organisateurId: number;
  depart: string;
  arrivee: string;
  fuseauHoraire: string;
  type: 'PUBLIC' | 'PRIVE';
  statut: 'PLANIFIE' | 'COMPLET' | 'ANNULE' | 'REALISE';
  origine: string | null;
  destination: string | null;
  traiteJ1Le: string | null;
  verificationJ1Requise: boolean;
}
export interface TrajetRequest {
  zoneId: number;
  vehiculeId: number;
  depart: string;
  arrivee: string;
  type: 'PUBLIC' | 'PRIVE';
  origine: string;
  destination: string;
}
export interface Reservation {
  reservationId: number;
  trajetId: number;
  clientId: number;
  statut: 'EN_ATTENTE' | 'CONFIRMEE' | 'ANNULEE';
  organisateur: boolean;
  annuleLe: string | null;
  annuleAvecPenalite: boolean;
}
export interface Dette {
  detteId: number;
  trajetId: number;
  montant: number;
  regle: number;
  statut: 'ACTIVE' | 'REGULARISEE';
}
export interface Paiement {
  paiementId: number;
  nature: string;
  ressourceId: number;
  montant: number;
  mode: string;
  valideLe: string;
  montantRembourse: number;
  rembourseLe: string | null;
  remboursementRefusePourPenalite: boolean;
}
export interface Compte {
  compteId: number;
  identifiant: string;
  role: 'ADMIN' | 'CLIENT';
  categorie: 'VIP' | 'LOCAL' | 'OCCASIONNEL' | null;
  etatIdentifiants: string;
  accesEffectif: boolean;
}
