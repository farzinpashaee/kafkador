import { Routes } from '@angular/router';
import { DashboardLayoutComponent } from './components/dashboard-layout/dashboard-layout.component'
import { BaseLayoutComponent } from './components/base-layout/base-layout.component'
import { ClusterComponent } from './components/cluster/cluster.component'
import { TopicComponent } from './components/topic/topic.component'
import { ConsumerComponent } from './components/consumer/consumer.component'
import { ConnectComponent } from './components/connect/connect.component'
import { BrokerComponent } from './components/broker/broker.component'

export const routes: Routes = [
  {
    path: '',
    component: DashboardLayoutComponent,
    children: [
      { path: '', component: ClusterComponent, title: 'Cluster' },
      { path: 'topics', component: TopicComponent, title: 'Topics' },
      { path: 'consumers', component: ConsumerComponent, title: 'Consumers' },
      { path: 'broker/:id', component: BrokerComponent, title: 'Broker' }
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
