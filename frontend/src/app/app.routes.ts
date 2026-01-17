import { Routes } from '@angular/router';
import { DashboardLayoutComponent } from './components/dashboard-layout/dashboard-layout.component'
import { BaseLayoutComponent } from './components/base-layout/base-layout.component'
import { DashboardComponent } from './components/dashboard/dashboard.component'
import { ClusterComponent } from './components/cluster/cluster.component'
import { TopicComponent } from './components/topic/topic.component'
import { ConsumersComponent } from './components/consumers/consumers.component'
import { ConnectComponent } from './components/connect/connect.component'
import { BrokerComponent } from './components/broker/broker.component'
import { TopicsComponent } from './components/topics/topics.component'
import { AccessControlComponent } from './components/access-control/access-control.component'
import { StreamsComponent } from './components/streams/streams.component'
import { ConnectorsComponent } from './components/connectors/connectors.component'
import { KsqlDBComponent } from './components/ksql-db/ksql-db.component'
import { SchemaRegistryComponent } from './components/schema-registry/schema-registry.component'
import { ConnectionsComponent } from './components/connections/connections.component'
import { SettingsComponent } from './components/settings/settings.component'

export const routes: Routes = [
  {
    path: '',
    component: DashboardLayoutComponent,
    children: [
      { path: '', component: DashboardComponent, title: 'Dashboard', data: { breadcrumb: { label: 'Dashboard'} } },
      { path: 'cluster', component: ClusterComponent, title: 'Cluster', data: { breadcrumb: { label: 'Cluster'} } },
      { path: 'broker/:id', component: BrokerComponent, title: 'Broker', data: { breadcrumb: { param: 'id'} } },
      { path: 'topic', component: TopicsComponent, title: 'Topics', data: { breadcrumb: { label:'Topics', url:'/topic'} } },
      { path: 'topic/:name', component: TopicComponent, title: 'Topics', data: { breadcrumb: { param: 'name'} } },
      { path: 'consumer', component: ConsumersComponent, title: 'Consumers', data: { breadcrumb: { label: 'Consumers'} } },
      { path: 'access-control', component: AccessControlComponent, title: 'Access Control', data: { breadcrumb: { label: 'Access Control'} } },
      { path: 'stream', component: StreamsComponent, title: 'Streams', data: { breadcrumb: { label: 'Streams'} } },
      { path: 'ksqldb', component: KsqlDBComponent, title: 'KsqlDB', data: { breadcrumb: { label: 'KsqlDB'} } },
      { path: 'connector', component: ConnectorsComponent, title: 'Connectors', data: { breadcrumb: { label: 'Connectors'} } },
      { path: 'schema-registry', component: SchemaRegistryComponent, title: 'Schema Registry', data: { breadcrumb: { label: 'Schema Registry'} } },
      { path: 'connection', component: ConnectionsComponent, title: 'Connections', data: { breadcrumb: { label: 'Connections'} } },
      { path: 'settings', component: SettingsComponent, title: 'Settings', data: { breadcrumb: { label: 'Settings'} } }
    ]
  },
  {
    path: '',
    component: BaseLayoutComponent,
    children: [
      { path: 'connect', component: ConnectComponent, title: 'Connect' }
    ]
  },
  { path: '**', redirectTo: '' }
];
