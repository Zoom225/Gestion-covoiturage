export type Role = 'CLIENT' | 'ADMIN';
export type ClientCategory = 'VIP' | 'LOCAL' | 'OCCASIONNEL';
export type AdminScope = 'GLOBAL' | 'LOCAL';

export interface LoginRequest {
  identifiant: string;
  password: string;
}
export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}
export interface AcceptInvitationRequest {
  jeton: string;
  password: string;
}
export interface ProfileResponse {
  compteId: number;
  identifiant: string;
  role: Role;
  clientId: number | null;
  categorie: ClientCategory | null;
  zoneClientId: number | null;
  perimetreAdmin: AdminScope | null;
  zoneAdminId: number | null;
}
