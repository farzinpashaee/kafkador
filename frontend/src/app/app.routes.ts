import { Routes } from '@angular/router';
import { DashboardLayoutComponent } from './components/dashboard-layout/dashboard-layout.component'
import { BaseLayoutComponent } from './components/base-layout/base-layout.component'
import { ClusterComponent } from './components/cluster/cluster.component'
import { TopicComponent } from './components/topic/topic.component'
import { ConsumersComponent } from './components/consumers/consumers.component'
import { ConnectComponent } from './components/connect/connect.component'
import { BrokerComponent } from './components/broker/broker.component'
import { TopicsComponent } from './components/topics/topics.component'
import { ConnectionsComponent } from './components/connections/connections.component'

export const routes: Routes = [
  {
    path: '',
    component: DashboardLayoutComponent,
    children: [
      { path: '', component: ClusterComponent, title: 'Cluster' },
      { path: 'broker/:id', component: BrokerComponent, title: 'Broker' },
      { path: 'topics', component: TopicsComponent, title: 'Topics' },
      { path: 'topic/:name', component: TopicComponent, title: 'Topic' },
      { path: 'consumers', component: ConsumersComponent, title: 'Consumers' },
      { path: 'connections', component: ConnectionsComponent, title: 'Connections' }
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
