import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpErrorResponse,HttpResponse   } from '@angular/common/http';
import { throwError, Observable } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Cluster } from '../models/cluster';
import { Connection } from '../models/connection';
import { Broker } from '../models/broker';
import { Alert } from '../models/alert';
import { Topic } from '../models/topic';
import { SearchResult } from '../models/search-result';
import { ConsumerGroup } from '../models/consumer-group';
import { GenericResponse } from '../models/generic-response';
import { SchemaRegistry } from '../models/schema-registry';

@Injectable({
  providedIn: 'root'
})
export class ApiService {

  private static ApiBaseUrl = 'http://localhost:8080/api';

  constructor(private http: HttpClient) {}

  public getClusterDetails(): Observable<GenericResponse<Cluster>> {
    return this.http.get<GenericResponse<Cluster>>(`${ApiService.ApiBaseUrl}/cluster`,{ withCredentials: true });
  }

  public getBrokerDetails(id:string): Observable<GenericResponse<Broker>> {
    return this.http.get<GenericResponse<Broker>>(`${ApiService.ApiBaseUrl}/broker/${id}`,{ withCredentials: true });
  }

  public getConnections(): Observable<HttpResponse<GenericResponse<Connection[]>>> {
    return this.http.get<GenericResponse<Connection[]>>(`${ApiService.ApiBaseUrl}/connection`,
      { withCredentials: true, observe: 'response' });
  }

  public addConnection(connection:Connection): Observable<HttpResponse<GenericResponse<Connection>>> {
    return this.http.post<GenericResponse<Connection>>(`${ApiService.ApiBaseUrl}/connection`,connection,
      { withCredentials: true ,observe: 'response' });
  }

  public deleteConnection(id:string): Observable<HttpResponse<void>> {
    return this.http.delete<void>(`${ApiService.ApiBaseUrl}/connection/`+id,
      { withCredentials: true ,observe: 'response' });
  }

  public getAlerts(): Observable<GenericResponse<Alert[]>> {
    return this.http.get<GenericResponse<Alert[]>>(`${ApiService.ApiBaseUrl}/alert`,{ withCredentials: true });
  }

  public getTopics(): Observable<GenericResponse<Topic[]>> {
    return this.http.get<GenericResponse<Topic[]>>(`${ApiService.ApiBaseUrl}/topic`,{ withCredentials: true });
  }

  public getTopicDetails(name:string): Observable<GenericResponse<Topic>> {
    return this.http.get<GenericResponse<Topic>>(`${ApiService.ApiBaseUrl}/topic/${name}`,{ withCredentials: true });
  }

  public getConsumerGroups(): Observable<GenericResponse<ConsumerGroup[]>> {
    return this.http.get<GenericResponse<ConsumerGroup[]>>(`${ApiService.ApiBaseUrl}/consumer-group`,{ withCredentials: true });
  }

  public getSchemaSubjects(): Observable<GenericResponse<SchemaRegistry>> {
    return this.http.get<GenericResponse<SchemaRegistry>>(`${ApiService.ApiBaseUrl}/schema-registry/subject`,{ withCredentials: true });
  }

  public search(query:string): Observable<GenericResponse<SearchResult[]>> {
    return this.http.get<GenericResponse<SearchResult[]>>(`${ApiService.ApiBaseUrl}/search?query=${query}`,{ withCredentials: true });
  }


  public connect(id:string): Observable<GenericResponse<Connection>> {
    let params = new HttpParams();
    params = params.set('id', id);
    return this.http.get<GenericResponse<Connection>>(`${ApiService.ApiBaseUrl}/connect`,{ params, withCredentials: true })
      .pipe(catchError((error: HttpErrorResponse) => {
          return throwError(() => error);
        })
      );
  }

  public disconnect(): Observable<HttpResponse<GenericResponse<Connection>>> {
    return this.http.get<GenericResponse<Connection>>(`${ApiService.ApiBaseUrl}/disconnect`,{withCredentials: true, observe: 'response' });
  }

}
