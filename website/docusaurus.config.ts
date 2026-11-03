import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

const repository = 'https://github.com/farzinpashaee/kafkador';

const config: Config = {
  title: 'Kafkador',
  tagline: 'A web console for Apache Kafka and Confluent Platform',
  favicon: 'img/favicon.ico',

  future: {
    v4: true,
  },

  // Served at kafkador.codestreamlab.com/docs/, so the docs sit at the root of the site (routeBasePath below).
  url: 'https://kafkador.codestreamlab.com',
  baseUrl: '/docs/',
  trailingSlash: true,

  onBrokenLinks: 'throw',

  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  presets: [
    [
      'classic',
      {
        docs: {
          routeBasePath: '/',
          sidebarPath: './sidebars.ts',
          editUrl: `${repository}/tree/main/website/`,
        },
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],

  themes: [
    [
      '@easyops-cn/docusaurus-search-local',
      {
        hashed: true,
        docsRouteBasePath: '/',
        indexBlog: false,
        highlightSearchTermsOnTargetPage: true,
      },
    ],
  ],

  themeConfig: {
    image: 'img/screenshots/dashboard.jpg',
    colorMode: {
      respectPrefersColorScheme: true,
    },
    navbar: {
      title: 'Kafkador',
      logo: {
        alt: 'Kafkador',
        src: 'img/logo.svg',
      },
      items: [
        {type: 'docSidebar', sidebarId: 'docs', position: 'left', label: 'Docs'},
        {href: `${repository}/blob/main/CHANGELOG.md`, label: 'Changelog', position: 'right'},
        {href: repository, label: 'GitHub', position: 'right'},
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: 'Docs',
          items: [
            {label: 'Quick start', to: '/getting-started/quick-start/'},
            {label: 'Configuration', to: '/reference/configuration/'},
            {label: 'Troubleshooting', to: '/troubleshooting/'},
          ],
        },
        {
          title: 'Project',
          items: [
            {label: 'GitHub', href: repository},
            {label: 'Issues', href: `${repository}/issues`},
            {label: 'Docker Hub', href: 'https://hub.docker.com/r/codestreamlab/kafkador'},
          ],
        },
      ],
      copyright: `Copyright © 2025-${new Date().getFullYear()} Codestreamlab.`,
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
      additionalLanguages: ['bash', 'java', 'nginx', 'properties'],
    },
  } satisfies Preset.ThemeConfig,
};

export default config;
