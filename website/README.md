# Kafkador documentation

The [Docusaurus](https://docusaurus.io/) site served at https://kafkador.codestreamlab.com/docs/. Pages are the Markdown
files in `docs/`, and `sidebars.ts` sets the navigation.

## Work on it

You need Node 20 or newer.

```bash
npm ci
npm start
```

This opens http://localhost:3000/docs/ and reloads as you edit. Search only works in a production build:

```bash
npm run build
npm run serve
```

The build fails on a broken link. It writes plain static files to `build/`.

## Publish it

A push to `main` that changes `website/` runs [the Docs workflow](../.github/workflows/docs.yml), which copies `build/` to
the VPS with `rsync --delete`. It removes anything else in the target folder, so keep that folder for the docs only.

### One-time setup

1. On the VPS, create a `deploy` user with its own SSH key and write access to the target folder only.
2. Add these repository secrets under *Settings → Secrets and variables → Actions*:

   | Secret | Value |
   |---|---|
   | `DOCS_SSH_KEY` | The private key of the `deploy` user |
   | `DOCS_SSH_HOST` | The VPS's hostname or IP address |
   | `DOCS_SSH_USER` | `deploy` |
   | `DOCS_PATH` | `/var/www/kafkador/docs` |

3. Add this to the Nginx `server` block for `kafkador.codestreamlab.com`, then reload Nginx:

   ```nginx
   location = /docs {
       return 301 /docs/;
   }

   location ^~ /docs/ {
       root /var/www/kafkador;
       try_files $uri $uri/ =404;
       error_page 404 /docs/404.html;
   }
   ```

## Versions

Docs aren't versioned during the beta. To freeze a release, run this and add a `docsVersionDropdown` to the navbar:

```bash
npm run docusaurus docs:version 1.0.0
```
