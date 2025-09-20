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
import { ConnectionsComponent } from './components/connections/connections.component'

export const routes: Routes = [
  {
    path: '',
    component: DashboardLayoutComponent,
    children: [
      { path: '', component: DashboardComponent, title: 'Dashboard', data: { breadcrumb: 'Dashboard' } },
      { path: 'cluster', component: ClusterComponent, title: 'Cluster', data: { breadcrumb: 'Cluster' } },
      { path: 'broker/:id', component: BrokerComponent, title: 'Broker', data: { breadcrumb: 'Cluster > Broker' } },
      { path: 'topics', component: TopicsComponent, title: 'Topics', data: { breadcrumb: 'Topics' } },
      { path: 'topic/:name', component: TopicComponent, title: 'Topic', data: { breadcrumb: 'Topics > {{Topic Name}}' } },
      { path: 'consumers', component: ConsumersComponent, title: 'Consumers', data: { breadcrumb: 'Consumers' } },
      { path: 'access-control', component: AccessControlComponent, title: 'Access Control', data: { breadcrumb: 'Access Control' } },
      { path: 'streams', component: StreamsComponent, title: 'Streams', data: { breadcrumb: 'Streams' } },
      { path: 'ksqldb', component: KsqlDBComponent, title: 'KsqlDB', data: { breadcrumb: 'KsqlDB' } },
      { path: 'connectors', component: ConnectorsComponent, title: 'Connectors', data: { breadcrumb: 'Connectors' } },
      { path: 'connections', component: ConnectionsComponent, title: 'Connections', data: { breadcrumb: 'Connections' } }
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
