import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { API_URL } from '../services/api-url';
import {
  AcceptInvitationRequest,
  LoginRequest,
  ProfileResponse,
  TokenResponse,
} from '../../shared/models/auth.models';

@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_URL);
  login(body: LoginRequest) {
    return this.http.post<TokenResponse>(`${this.base}/auth/login`, body, { timeout: 15000 });
  }
  profile() {
    return this.http.get<ProfileResponse>(`${this.base}/auth/me`, { timeout: 15000 });
  }
  activate(body: AcceptInvitationRequest) {
    return this.http.post<void>(`${this.base}/auth/invitations/accept`, body, { timeout: 15000 });
  }
}
