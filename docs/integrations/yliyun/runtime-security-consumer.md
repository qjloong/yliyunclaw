# Yliyun Runtime Security Consumer

This patch implements the MateClaw side of MC-RT-010 for internal runtime
requests. It is intentionally isolated under `/api/internal/v1`; existing
`/api/v1` routes are unchanged.

## Implemented boundary

The servlet filter performs these checks before an internal controller runs:

1. Bearer service token and `X-Yly-Internal-Token`.
2. Timestamp window and required signature headers.
3. Exact request-body SHA-256.
4. Base64url Trusted Context decoding and context SHA-256.
5. HMAC-SHA256 verification using constant-time comparisons.
6. Header-to-context equality for tenant, user, workspace, app, business,
   run and trace identifiers.
7. Context field validation, lifetime, role and capability enums.
8. Database-backed nonce consumption using the existing `sso_state` table.
9. Cloud user to MateClaw workspace binding validation.
10. Entitlement `configVersion` validation and optional app-key mapping.
11. Idempotency-key validation for mutating methods.

Verified context is available from:

```java
YliyunTrustedContext context = YliyunTrustedContextHolder.requireCurrent();
```

Controllers can also read it from the request attribute through
`YliyunTrustedContextHolder.from(request)`.

## Context carrier

The reviewed context-v1.1 transport uses:

```text
X-Yly-Trusted-Context: base64url-without-padding(UTF-8 JSON bytes)
X-Yly-Context-SHA256: lowercase-hex(SHA256(decoded JSON bytes))
```

The context digest is included in the HMAC canonical request:

```text
METHOD + "\n" +
PATH + "\n" +
SORTED_RAW_QUERY_COMPONENTS + "\n" +
TIMESTAMP + "\n" +
NONCE + "\n" +
CONTEXT_SHA256 + "\n" +
BODY_SHA256
```

HMAC input is the UTF-8 canonical request. The result is standard Base64 with
padding. A shared executable vector is stored at:

```text
mateclaw-server/src/test/resources/contracts/yliyun/runtime-signing-v1_1.json
```

The older Frozen-v1 canonical can only be enabled together with the explicit
`allow-legacy-signature` acknowledgement because it does not bind the context
digest.

## Configuration

Spring environment variables map directly to these properties:

```yaml
mateclaw:
  runtime:
    yliyun:
      enabled: true
      path-prefix: /api/internal/v1
      service-token: ${MATECLAW_YLIYUN_RUNTIME_SERVICE_TOKEN}
      internal-token: ${MATECLAW_YLIYUN_RUNTIME_INTERNAL_TOKEN}
      service-secret: ${MATECLAW_YLIYUN_RUNTIME_SERVICE_SECRET}
      clock-skew-seconds: 300
      max-body-bytes: 2097152
      max-context-bytes: 16384
      require-internal-token: true
      signature-version: CONTEXT_V1_1
      allow-legacy-signature: false
      app-entitlements:
        GOAL: mateclaw_ai_assistant
        AI_SITES: mateclaw_ai_assistant
```

Do not store real secrets in the repository. Missing or short credentials cause
a fail-closed `503 INTERNAL_ERROR` only on protected internal requests.

## Workspace mapping rule

The current MateClaw database stores an internal numeric workspace ID, while
the cloud contract carries a string. This patch accepts either:

```text
ws_<internal workspace id>
ws_<workspace slug>
```

The producer and final contract should choose one representation and remove the
other after the shared contract is frozen.

## Error response

Authentication failures are written by the filter because they happen before
Spring MVC exception handling:

```json
{
  "code": "AUTH_SIGNATURE_MISMATCH",
  "message": "Request signature verification failed",
  "stage": "auth.request_signature",
  "suggestion": "Create a new signed request using the configured service credentials",
  "traceId": "trace-id",
  "retryable": false
}
```

No token, secret, full context or body is logged.

## Validation after merge

Run the focused tests first:

```bash
mvn -pl mateclaw-server -Dtest='YliyunRuntime*Test,YliyunTrustedContext*Test' test
```

Then run the module test suite:

```bash
mvn -pl mateclaw-server test
```

This patch does not create Conversation, Run, SSE or Approval endpoints. Those
remain MC-RT-011 through MC-RT-014 and should reuse this verified context
boundary rather than repeating authentication logic.
