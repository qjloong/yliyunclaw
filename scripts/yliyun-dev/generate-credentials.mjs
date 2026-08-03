import { generateKeyPairSync, randomBytes } from 'node:crypto';
import { copyFileSync, existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const mateclawRoot = resolve(scriptDir, '..', '..');
const outputDir = join(mateclawRoot, 'data', 'yliyun-dev');
const force = process.argv.includes('--force');
const prepareTicketRotation = process.argv.includes('--prepare-ticket-rotation');
const retireTicketPrevious = process.argv.includes('--retire-ticket-previous');

if (prepareTicketRotation && retireTicketPrevious) {
  throw new Error('prepare and retire ticket rotation modes are mutually exclusive');
}

mkdirSync(outputDir, { recursive: true });

const files = {
  privateKey: join(outputDir, 'obo-private.pem'),
  publicKey: join(outputDir, 'obo-public.pem'),
  ticketSecret: join(outputDir, 'ticket-secret.txt'),
  ticketSecretPrevious: join(outputDir, 'ticket-secret-previous.txt'),
  ticketKeyId: join(outputDir, 'ticket-key-id.txt'),
  ticketKeyIdPrevious: join(outputDir, 'ticket-key-id-previous.txt'),
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

if (!existsSync(files.ticketKeyId)) {
  writeFileSync(files.ticketKeyId, 'dev-current\n', { mode: 0o600 });
}

if (prepareTicketRotation) {
  if (existsSync(files.ticketSecretPrevious)
      && readFileSync(files.ticketSecretPrevious, 'utf8').trim()) {
    throw new Error('ticket rotation is already pending; retire or roll back the previous key first');
  }
  copyFileSync(files.ticketSecret, files.ticketSecretPrevious);
  copyFileSync(files.ticketKeyId, files.ticketKeyIdPrevious);
  const nextKeyId = `dev-${new Date().toISOString().replace(/[-:TZ.]/g, '').slice(0, 14)}`;
  writeFileSync(files.ticketSecret, randomBytes(48).toString('base64url'), { mode: 0o600 });
  writeFileSync(files.ticketKeyId, `${nextKeyId}\n`, { mode: 0o600 });
  process.stdout.write(`ticket rotation prepared: currentKeyId=${nextKeyId}, previousKeyId=${readFileSync(files.ticketKeyIdPrevious, 'utf8').trim()}\n`);
}

if (retireTicketPrevious) {
  if (!existsSync(files.ticketSecretPrevious) || !readFileSync(files.ticketSecretPrevious, 'utf8').trim()) {
    throw new Error('there is no previous ticket key to retire');
  }
  const retiredAt = new Date().toISOString().replace(/[-:TZ.]/g, '').slice(0, 14);
  const retiredSecret = join(outputDir, `ticket-secret-previous.retired-${retiredAt}.txt`);
  const retiredKeyId = join(outputDir, `ticket-key-id-previous.retired-${retiredAt}.txt`);
  renameSync(files.ticketSecretPrevious, retiredSecret);
  renameSync(files.ticketKeyIdPrevious, retiredKeyId);
  process.stdout.write(`previous ticket key retired to recoverable local backup (${retiredAt})\n`);
}

if (force || !existsSync(files.mcpAppKey)) {
  writeFileSync(files.mcpAppKey, randomBytes(48).toString('base64url'), { mode: 0o600 });
}

if (!prepareTicketRotation && !retireTicketPrevious) process.stdout.write(`${outputDir}\n`);
