import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpErrorResponse,HttpResponse   } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Cluster, Connection, Config, Broker, Alert, Topic, SearchResult, ConsumerGroup, GenericResponse,
  SchemaRegistry, SchemaRegistryConfig, Chart, Event, KsqlDbConfig, KsqlServerInfo, KsqlStream, KsqlTable, KsqlQuery,
  AclBinding, KafkaConnectConfig, ConnectorPlugin, Connector, ConnectorCreateRequest } from '../models';
import { environment } from '../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class ApiService {

  private static ApiBaseUrl = `${environment.baseUrl}/api/v1`;

  constructor(private http: HttpClient) {}

  public getClusterDetails(): Observable<GenericResponse<Cluster>> {
    return this.http.get<GenericResponse<Cluster>>(`${ApiService.ApiBaseUrl}/cluster`,{ withCredentials: true });
  }

  public getBrokerDetails(id:string): Observable<GenericResponse<Broker>> {
    return this.http.get<GenericResponse<Broker>>(`${ApiService.ApiBaseUrl}/brokers/${id}`,{ withCredentials: true });
  }

  public getConnections(): Observable<HttpResponse<GenericResponse<Connection[]>>> {
    return this.http.get<GenericResponse<Connection[]>>(`${ApiService.ApiBaseUrl}/connections`,
      { withCredentials: true, observe: 'response' });
  }

  public addConnection(connection:Connection): Observable<HttpResponse<GenericResponse<Connection>>> {
    return this.http.post<GenericResponse<Connection>>(`${ApiService.ApiBaseUrl}/connections`,connection,
      { withCredentials: true ,observe: 'response' });
  }

  public updateConnection(id:string, connection:Connection): Observable<HttpResponse<GenericResponse<Connection>>> {
    return this.http.put<GenericResponse<Connection>>(`${ApiService.ApiBaseUrl}/connections/`+id,connection,
      { withCredentials: true ,observe: 'response' });
  }

  public deleteConnection(id:string): Observable<HttpResponse<void>> {
    return this.http.delete<void>(`${ApiService.ApiBaseUrl}/connections/`+id,
      { withCredentials: true ,observe: 'response' });
  }

  public getAlerts(): Observable<GenericResponse<Alert[]>> {
    return this.http.get<GenericResponse<Alert[]>>(`${ApiService.ApiBaseUrl}/alerts`,{ withCredentials: true });
  }

  public getAclBindings(): Observable<HttpResponse<GenericResponse<AclBinding[]>>> {
    return this.http.get<GenericResponse<AclBinding[]>>(`${ApiService.ApiBaseUrl}/acl`,
      { withCredentials: true ,observe: 'response'});
  }

  public createAclBinding(binding:AclBinding): Observable<HttpResponse<GenericResponse<AclBinding>>> {
    return this.http.post<GenericResponse<AclBinding>>(`${ApiService.ApiBaseUrl}/acl`,binding,
      { withCredentials: true ,observe: 'response' });
  }

  public deleteAclBinding(binding:AclBinding): Observable<HttpResponse<void>> {
    return this.http.delete<void>(`${ApiService.ApiBaseUrl}/acl`,
      { withCredentials: true, observe: 'response', body: binding });
  }

  public getTopics(): Observable<HttpResponse<GenericResponse<Topic[]>>> {
    return this.http.get<GenericResponse<Topic[]>>(`${ApiService.ApiBaseUrl}/topics`,
      { withCredentials: true ,observe: 'response'});
  }

  public getTopicDetails(name:string): Observable<GenericResponse<Topic>> {
    return this.http.get<GenericResponse<Topic>>(`${ApiService.ApiBaseUrl}/topics/${name}`,{ withCredentials: true });
  }

  public createTopic(topic:Topic): Observable<HttpResponse<GenericResponse<Topic>>> {
    return this.http.post<GenericResponse<Topic>>(`${ApiService.ApiBaseUrl}/topics`,topic,
      { withCredentials: true ,observe: 'response' });
  }

  public deleteTopic(name:string): Observable<HttpResponse<void>> {
    return this.http.delete<void>(`${ApiService.ApiBaseUrl}/topics/${name}`,
      { withCredentials: true ,observe: 'response' });
  }

  public getConsumerGroups(): Observable<HttpResponse<GenericResponse<ConsumerGroup[]>>> {
    return this.http.get<GenericResponse<ConsumerGroup[]>>(`${ApiService.ApiBaseUrl}/consumer-groups`,
      { withCredentials: true ,observe: 'response'} );
  }

  public getDefaultConsumerGroupId(): Observable<HttpResponse<GenericResponse<string>>> {
    return this.http.get<GenericResponse<string>>(`${ApiService.ApiBaseUrl}/consumer-groups/default-id`,
      { withCredentials: true, observe: 'response' });
  }

  public getSchemaSubjects(): Observable<GenericResponse<SchemaRegistry>> {
    return this.http.get<GenericResponse<SchemaRegistry>>(`${ApiService.ApiBaseUrl}/schema-registry/subjects`,{ withCredentials: true });
  }

  public getSchemaRegistryConfig(): Observable<HttpResponse<GenericResponse<SchemaRegistryConfig>>> {
    return this.http.get<GenericResponse<SchemaRegistryConfig>>(`${ApiService.ApiBaseUrl}/schema-registry/config`,
      { withCredentials: true, observe: 'response' });
  }

  public saveSchemaRegistryConfig(url:string): Observable<HttpResponse<GenericResponse<SchemaRegistryConfig>>> {
    return this.http.put<GenericResponse<SchemaRegistryConfig>>(`${ApiService.ApiBaseUrl}/schema-registry/config`,{ url },
      { withCredentials: true, observe: 'response' });
  }

  public getKsqlDbConfig(): Observable<HttpResponse<GenericResponse<KsqlDbConfig>>> {
    return this.http.get<GenericResponse<KsqlDbConfig>>(`${ApiService.ApiBaseUrl}/ksqldb/config`,
      { withCredentials: true, observe: 'response' });
  }

  public saveKsqlDbConfig(url:string): Observable<HttpResponse<GenericResponse<KsqlDbConfig>>> {
    return this.http.put<GenericResponse<KsqlDbConfig>>(`${ApiService.ApiBaseUrl}/ksqldb/config`,{ url },
      { withCredentials: true, observe: 'response' });
  }

  public getKsqlDbInfo(): Observable<HttpResponse<GenericResponse<KsqlServerInfo>>> {
    return this.http.get<GenericResponse<KsqlServerInfo>>(`${ApiService.ApiBaseUrl}/ksqldb/info`,
      { withCredentials: true, observe: 'response' });
  }

  public getKsqlDbStreams(): Observable<HttpResponse<GenericResponse<KsqlStream[]>>> {
    return this.http.get<GenericResponse<KsqlStream[]>>(`${ApiService.ApiBaseUrl}/ksqldb/streams`,
      { withCredentials: true, observe: 'response' });
  }

  public getKsqlDbTables(): Observable<HttpResponse<GenericResponse<KsqlTable[]>>> {
    return this.http.get<GenericResponse<KsqlTable[]>>(`${ApiService.ApiBaseUrl}/ksqldb/tables`,
      { withCredentials: true, observe: 'response' });
  }

  public getKsqlDbQueries(): Observable<HttpResponse<GenericResponse<KsqlQuery[]>>> {
    return this.http.get<GenericResponse<KsqlQuery[]>>(`${ApiService.ApiBaseUrl}/ksqldb/queries`,
      { withCredentials: true, observe: 'response' });
  }

  public terminateKsqlDbQuery(id:string): Observable<HttpResponse<void>> {
    return this.http.post<void>(`${ApiService.ApiBaseUrl}/ksqldb/queries/${id}/terminate`,null,
      { withCredentials: true, observe: 'response' });
  }

  public search(query:string): Observable<GenericResponse<SearchResult[]>> {
    let params = new HttpParams().set('query', query);
    return this.http.get<GenericResponse<SearchResult[]>>(`${ApiService.ApiBaseUrl}/search`,{ params, withCredentials: true });
  }

  public getChart(id:string,query:string): Observable<HttpResponse<GenericResponse<Chart>>> {
    return this.http.get<GenericResponse<Chart>>(`${ApiService.ApiBaseUrl}/metrics/chart/${id}${query}`,
      { withCredentials: true ,observe: 'response' });
  }

  public connect(id:string): Observable<GenericResponse<Connection>> {
    return this.http.post<GenericResponse<Connection>>(`${ApiService.ApiBaseUrl}/connections/${id}/connect`,{},{ withCredentials: true });
  }

  public disconnect(): Observable<HttpResponse<GenericResponse<Connection>>> {
    return this.http.post<GenericResponse<Connection>>(`${ApiService.ApiBaseUrl}/connections/disconnect`,{},
      { withCredentials: true, observe: 'response' });
  }

  public updateBrokerConfig(brokerId:string, config:Config): Observable<HttpResponse<void>> {
    return this.http.put<void>(`${ApiService.ApiBaseUrl}/brokers/${brokerId}/config`,config,
      { withCredentials: true ,observe: 'response' });
  }

  public updateTopicConfig(topicId:string, config:Config): Observable<HttpResponse<void>> {
    return this.http.put<void>(`${ApiService.ApiBaseUrl}/topics/${topicId}/config`,config,
      { withCredentials: true ,observe: 'response' });
  }

  /**
   * Opens a live Server-Sent Events stream of messages consumed from `topic` under
   * consumer group `groupId`. HttpClient has no SSE support, so this wraps the
   * native EventSource API in an Observable that closes the connection when
   * unsubscribed (component teardown, navigation away, etc).
   */
  public consumeTopicMessages(topic: string, groupId: string): Observable<string> {
    return new Observable<string>((subscriber) => {
      const params = new HttpParams().set('groupId', groupId);
      const url = `${ApiService.ApiBaseUrl}/topics/${encodeURIComponent(topic)}/messages/stream?${params.toString()}`;
      const eventSource = new EventSource(url, { withCredentials: true });

      eventSource.onmessage = (event) => subscriber.next(event.data);
      eventSource.onerror = () => {
        subscriber.error(new Error('Connection to the message stream was lost.'));
        eventSource.close();
      };

      return () => eventSource.close();
    });
  }

  public produceTopicMessage(topic: string, event: Event): Observable<GenericResponse<Event>> {
    return this.http.post<GenericResponse<Event>>(
      `${ApiService.ApiBaseUrl}/topics/${encodeURIComponent(topic)}/messages`, event,
      { withCredentials: true });
  }

  public getKafkaConnectConfig(): Observable<HttpResponse<GenericResponse<KafkaConnectConfig>>> {
    return this.http.get<GenericResponse<KafkaConnectConfig>>(`${ApiService.ApiBaseUrl}/kafka-connect/config`,
      { withCredentials: true, observe: 'response' });
  }

  public saveKafkaConnectConfig(url:string): Observable<HttpResponse<GenericResponse<KafkaConnectConfig>>> {
    return this.http.put<GenericResponse<KafkaConnectConfig>>(`${ApiService.ApiBaseUrl}/kafka-connect/config`,{ url },
      { withCredentials: true, observe: 'response' });
  }

  public getConnectorPlugins(): Observable<HttpResponse<GenericResponse<ConnectorPlugin[]>>> {
    return this.http.get<GenericResponse<ConnectorPlugin[]>>(`${ApiService.ApiBaseUrl}/kafka-connect/plugins`,
      { withCredentials: true, observe: 'response' });
  }

  public getConnectors(): Observable<HttpResponse<GenericResponse<Connector[]>>> {
    return this.http.get<GenericResponse<Connector[]>>(`${ApiService.ApiBaseUrl}/kafka-connect/connectors`,
      { withCredentials: true, observe: 'response' });
  }

  public createConnector(request:ConnectorCreateRequest): Observable<HttpResponse<GenericResponse<Connector>>> {
    return this.http.post<GenericResponse<Connector>>(`${ApiService.ApiBaseUrl}/kafka-connect/connectors`,request,
      { withCredentials: true, observe: 'response' });
  }

  public updateConnectorConfig(name:string, config:{ [key: string]: string }): Observable<HttpResponse<GenericResponse<Connector>>> {
    return this.http.put<GenericResponse<Connector>>(`${ApiService.ApiBaseUrl}/kafka-connect/connectors/${encodeURIComponent(name)}/config`,config,
      { withCredentials: true, observe: 'response' });
  }

  public pauseConnector(name:string): Observable<HttpResponse<void>> {
    return this.http.put<void>(`${ApiService.ApiBaseUrl}/kafka-connect/connectors/${encodeURIComponent(name)}/pause`,null,
      { withCredentials: true, observe: 'response' });
  }

  public resumeConnector(name:string): Observable<HttpResponse<void>> {
    return this.http.put<void>(`${ApiService.ApiBaseUrl}/kafka-connect/connectors/${encodeURIComponent(name)}/resume`,null,
      { withCredentials: true, observe: 'response' });
  }

  public restartConnector(name:string): Observable<HttpResponse<void>> {
    return this.http.post<void>(`${ApiService.ApiBaseUrl}/kafka-connect/connectors/${encodeURIComponent(name)}/restart`,null,
      { withCredentials: true, observe: 'response' });
  }

  public deleteConnector(name:string): Observable<HttpResponse<void>> {
    return this.http.delete<void>(`${ApiService.ApiBaseUrl}/kafka-connect/connectors/${encodeURIComponent(name)}`,
      { withCredentials: true, observe: 'response' });
  }

}
