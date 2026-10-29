import { createServer } from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { extname, relative, resolve, sep } from 'node:path';

// Serveur local de recette du build, sans proxy API ni authentification simulée.
const root = fileURLToPath(new URL('../dist/driveconnect/browser/', import.meta.url));
const types = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript',
  '.css': 'text/css',
  '.svg': 'image/svg+xml',
  '.woff2': 'font/woff2',
  '.woff': 'font/woff',
  '.json': 'application/json',
};
const server = createServer(async (request, response) => {
  if (!['GET', 'HEAD'].includes(request.method)) {
    response.writeHead(405, { Allow: 'GET, HEAD' });
    response.end();
    return;
  }
  try {
    const pathname = decodeURIComponent(new URL(request.url, 'http://localhost').pathname);
    let file = resolve(root, `.${pathname}`);
    const location = relative(root, file);
    if (location === '..' || location.startsWith(`..${sep}`)) {
      response.writeHead(403);
      response.end();
      return;
    }
    try {
      if (!(await stat(file)).isFile()) file = resolve(root, 'index.html');
    } catch {
      if (extname(pathname)) {
        response.writeHead(404);
        response.end();
        return;
      }
      file = resolve(root, 'index.html');
    }
    const body = await readFile(file);
    response.writeHead(200, {
      'Content-Type': types[extname(file)] ?? 'application/octet-stream',
      'Cache-Control': 'no-store',
      'X-Content-Type-Options': 'nosniff',
    });
    response.end(request.method === 'HEAD' ? undefined : body);
  } catch {
    response.writeHead(500);
    response.end('Le build local n’est pas disponible. Exécutez npm run build.');
  }
});
server.listen(4200, 'localhost', () => console.log('Recette DriveConnect : http://localhost:4200'));
