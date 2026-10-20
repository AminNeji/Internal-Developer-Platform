import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApplicationStatus, CreateEnvironmentReq, Environment } from './environment.model';

@Injectable({ providedIn: 'root' })
export class EnvironmentService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/environment';

  // GET /api/environment
  listAll(): Observable<Environment[]> {
    return this.http.get<Environment[]>(this.base);
  }

  // POST /api/environment/create
  create(req: CreateEnvironmentReq): Observable<Environment> {
    return this.http.post<Environment>(`${this.base}/create`, req);
  }

  // ---------------------------------------------------------------
  // Your turn. Delete each throw and return the right http call.
  // ---------------------------------------------------------------

  // GET /api/environment/{envId}
  getByEnvId(envId: string): Observable<Environment> {
    return this.http.get<Environment>(`${this.base}/${envId}`);
  }

  // DELETE /api/environment/{envId}
  // The backend returns 204 No Content, so there is no body to type.
  destroy(envId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${envId}`);
  }

  // GET /api/environment/{envId}/status
  // This endpoint does not exist in EnvironmentController yet - it will 404
  // until you add it. Write the frontend side anyway.
  getStatus(envId: string): Observable<ApplicationStatus> {
    return this.http.get<ApplicationStatus>(`${this.base}/${envId}/status`);
  }
}
