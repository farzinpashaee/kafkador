import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, map, shareReplay } from 'rxjs/operators';
import { environment } from '../environments/environment';
import { GenericResponse } from '../models';

/**
 * The version of the running Kafkador, as the backend reports it from its build: the pom is its one source. Asked
 * once and shared; empty when the backend can't be reached.
 */
@Injectable({ providedIn: 'root' })
export class AppInfoService {

  readonly version$: Observable<string>;

  constructor(http: HttpClient) {
    this.version$ = http.get<GenericResponse<{ version: string }>>(`${environment.baseUrl}/api/v1/app-info`, { withCredentials: true })
      .pipe(
        map(res => res.data?.version ?? ''),
        catchError(() => of('')),
        shareReplay(1)
      );
  }

}
