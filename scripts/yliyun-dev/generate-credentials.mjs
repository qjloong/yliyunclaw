import { generateKeyPairSync, randomBytes } from 'node:crypto';
import { existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const mateclawRoot = resolve(scriptDir, '..', '..');
const outputDir = join(mateclawRoot, 'data', 'yliyun-dev');
const force = process.argv.includes('--force');

mkdirSync(outputDir, { recursive: true });

const files = {
  privateKey: join(outputDir, 'obo-private.pem'),
  publicKey: join(outputDir, 'obo-public.pem'),
  ticketSecret: join(outputDir, 'ticket-secret.txt'),
  mcpAppKey: join(outputDir, 'mcp-app-key.txt'),
};

if (force || !existsSync(files.privateKey) || !existsSync(files.publicKey)) {
  const { privateKey, publicKey } = generateKeyPairSync('rsa', {
    modulusLength: 2048,
    privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
    publicKeyEncoding: { type: 'spki', format: 'pem' },
  });
  writeFileSync(files.privateKey, privateKey, { mode: 0o600 });
  writeFileSync(files.publicKey, publicKey, { mode: 0o644 });
}

if (force || !existsSync(files.ticketSecret)) {
  writeFileSync(files.ticketSecret, randomBytes(48).toString('base64url'), { mode: 0o600 });
}

if (force || !existsSync(files.mcpAppKey)) {
  writeFileSync(files.mcpAppKey, randomBytes(48).toString('base64url'), { mode: 0o600 });
}

process.stdout.write(`${outputDir}\n`);
