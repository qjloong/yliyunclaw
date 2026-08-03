import {
  createPrivateKey,
  createPublicKey,
  randomBytes,
  sign,
  verify,
} from 'node:crypto';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const credentialRoot = resolve(scriptDir, '..', '..', 'data', 'yliyun-dev');

try {
  const privateKey = createPrivateKey(readFileSync(join(credentialRoot, 'obo-private.pem'), 'utf8'));
  const publicKey = createPublicKey(readFileSync(join(credentialRoot, 'obo-public.pem'), 'utf8'));
  if (privateKey.asymmetricKeyType !== 'rsa' || publicKey.asymmetricKeyType !== 'rsa') {
    throw new Error('OBO keys must both be RSA');
  }
  const challenge = randomBytes(32);
  const signature = sign('sha256', challenge, privateKey);
  if (!verify('sha256', challenge, publicKey, signature)) {
    throw new Error('OBO private/public key pair does not match');
  }

  const ticketSecret = readFileSync(join(credentialRoot, 'ticket-secret.txt'), 'utf8').trim();
  const ticketKeyId = readFileSync(join(credentialRoot, 'ticket-key-id.txt'), 'utf8').trim();
  const mcpAppKey = readFileSync(join(credentialRoot, 'mcp-app-key.txt'), 'utf8').trim();
  if (ticketSecret.length < 43) throw new Error('ticket secret is too short');
  if (!/^[A-Za-z0-9._-]{1,64}$/.test(ticketKeyId)) throw new Error('ticket key id is invalid');
  if (mcpAppKey.length < 43) throw new Error('MCP app key is too short');
  if (ticketSecret === mcpAppKey) throw new Error('ticket secret and MCP app key must not be reused');

  const previousSecretPath = join(credentialRoot, 'ticket-secret-previous.txt');
  if (existsSync(previousSecretPath)) {
    const previousSecret = readFileSync(previousSecretPath, 'utf8').trim();
    const previousKeyId = readFileSync(join(credentialRoot, 'ticket-key-id-previous.txt'), 'utf8').trim();
    if (previousSecret.length < 43) throw new Error('previous ticket secret is too short');
    if (previousSecret === ticketSecret) throw new Error('current and previous ticket secrets must differ');
    if (!/^[A-Za-z0-9._-]{1,64}$/.test(previousKeyId) || previousKeyId === ticketKeyId) {
      throw new Error('previous ticket key id is invalid or duplicated');
    }
  }

  process.stdout.write(`credential pair, secret separation and ticket key ring verified (${ticketKeyId})`);
} catch (error) {
  process.stderr.write(error instanceof Error ? error.message : String(error));
  process.exit(1);
}
