import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  docs: [
    'intro',
    {
      type: 'category',
      label: 'Getting started',
      collapsed: false,
      items: ['getting-started/quick-start', 'getting-started/first-run-setup'],
    },
    {
      type: 'category',
      label: 'Install',
      items: ['install/docker', 'install/desktop', 'install/jar'],
    },
    {
      type: 'category',
      label: 'Connect',
      items: [
        'connect/clusters',
        'connect/tls-and-sasl',
        'connect/from-docker',
        'connect/ecosystem-services',
      ],
    },
    {
      type: 'category',
      label: 'Using Kafkador',
      items: [
        'guides/overview',
        'guides/topics',
        'guides/records',
        'guides/consumer-groups',
        'guides/schema-registry',
        'guides/kafka-connect',
        'guides/ksqldb',
        'guides/acls',
        'guides/ai-assistant',
      ],
    },
    {
      type: 'category',
      label: 'Security',
      items: ['security/sign-in', 'security/ldap', 'security/production'],
    },
    {
      type: 'category',
      label: 'Reference',
      items: ['reference/configuration', 'reference/data-and-backup'],
    },
    'troubleshooting',
  ],
};

export default sidebars;
