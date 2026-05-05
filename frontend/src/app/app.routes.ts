import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./components/dashboard-layout/dashboard-layout.component').then(m => m.DashboardLayoutComponent),
    children: [
      { path: '', loadComponent: () => import('./components/dashboard/dashboard.component').then(m => m.DashboardComponent), title: 'Dashboard', data: { breadcrumb: { label: 'Dashboard' } } },
      { path: 'cluster', loadComponent: () => import('./components/cluster/cluster.component').then(m => m.ClusterComponent), title: 'Cluster', data: { breadcrumb: { label: 'Cluster' } } },
      { path: 'broker/:id', loadComponent: () => import('./components/broker/broker.component').then(m => m.BrokerComponent), title: 'Broker', data: { breadcrumb: { param: 'id' } } },
      { path: 'topic', loadComponent: () => import('./components/topics/topics.component').then(m => m.TopicsComponent), title: 'Topics', data: { breadcrumb: { label: 'Topics', url: '/topic' } } },
      { path: 'topic/:name', loadComponent: () => import('./components/topic/topic.component').then(m => m.TopicComponent), title: 'Topics', data: { breadcrumb: { param: 'name' } } },
      { path: 'consumer', loadComponent: () => import('./components/consumers/consumers.component').then(m => m.ConsumersComponent), title: 'Consumers', data: { breadcrumb: { label: 'Consumers' } } },
      { path: 'access-control', loadComponent: () => import('./components/access-control/access-control.component').then(m => m.AccessControlComponent), title: 'Access Control', data: { breadcrumb: { label: 'Access Control' } } },
      { path: 'stream', loadComponent: () => import('./components/streams/streams.component').then(m => m.StreamsComponent), title: 'Streams', data: { breadcrumb: { label: 'Streams' } } },
      { path: 'ksqldb', loadComponent: () => import('./components/ksql-db/ksql-db.component').then(m => m.KsqlDBComponent), title: 'KsqlDB', data: { breadcrumb: { label: 'KsqlDB' } } },
      { path: 'connector', loadComponent: () => import('./components/connectors/connectors.component').then(m => m.ConnectorsComponent), title: 'Connectors', data: { breadcrumb: { label: 'Connectors' } } },
      { path: 'schema-registry', loadComponent: () => import('./components/schema-registry/schema-registry.component').then(m => m.SchemaRegistryComponent), title: 'Schema Registry', data: { breadcrumb: { label: 'Schema Registry' } } },
      { path: 'connection', loadComponent: () => import('./components/connections/connections.component').then(m => m.ConnectionsComponent), title: 'Connections', data: { breadcrumb: { label: 'Connections' } } },
      { path: 'settings', loadComponent: () => import('./components/settings/settings.component').then(m => m.SettingsComponent), title: 'Settings', data: { breadcrumb: { label: 'Settings' } } }
    ]
  },
  {
    path: '',
    loadComponent: () => import('./components/base-layout/base-layout.component').then(m => m.BaseLayoutComponent),
    children: [
      { path: 'connect', loadComponent: () => import('./components/connect/connect.component').then(m => m.ConnectComponent), title: 'Connect' }
    ]
  },
  { path: '**', redirectTo: '' }
];
