# SmartPay AI — REST API Documentation

Base URL: `http://localhost:8080/api`

Conventions:

- `Content-Type: application/json`; camelCase keys; ISO-8601 dates (`2026-11-25`).
- All money values are decimals with 2 decimal places; totals are **recomputed server-side**.
- Status codes: `200` OK · `201` Created · `400` validation error · `401/403` auth (Phase 6) · `404` not found · `502` external integration failure.
- Endpoints are grouped by implementation phase (see `docs/architecture.md` §9).
  Phases 2–4 and Phase 6 (security) are implemented and covered by
  `smartpay-backend/smoke-test.ps1`.

**Error envelope** (all non-2xx responses):

```json
{
  "timestamp": "2026-11-11T22:40:12.345+00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Invoice must contain at least one item",
  "path": "/api/invoices"
}
```

---

## Authentication — Phase 6

All endpoints except `/api/health` and `/api/auth/**` require a Bearer token:
send `Authorization: Bearer <token>` (the frontend does this automatically).
Missing or invalid tokens get the same error envelope with status `401`.
Every invoice, customer and payment is **scoped to the authenticated user** —
another user's records are simply `404`.

### `POST /api/auth/register`

Request:

```json
{ "email": "you@example.com", "password": "at-least-8-chars", "fullName": "Jane Freelancer" }
```

Response `201`:

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 86400,
  "user": { "id": 1, "email": "you@example.com", "fullName": "Jane Freelancer", "role": "USER" }
}
```

Duplicate email → `400` "An account with this email already exists".

### `POST /api/auth/login`

Request: `{ "email": "you@example.com", "password": "..." }`.

Response `200`: same shape as register. Wrong credentials → `400`
"Invalid email or password" (no hint about which field was wrong).

### `GET /api/users/me`

Requires a token. Returns the current profile:
`{ "id": 1, "email": "...", "fullName": "...", "role": "USER" }`.

---

## Health — Phase 2

### `GET /api/health`

Liveness + connectivity snapshot used by the frontend status bar.

```json
{
  "status": "UP",
  "database": "UP",
  "invoiceCount": 12,
  "ai": { "provider": "gemini", "mode": "LIVE" },
  "paypal": { "mode": "LIVE" }
}
```

`ai.mode` = `LIVE` (Gemini key configured) or `FALLBACK` (local parser).
`paypal.mode` = `LIVE` (sandbox credentials configured) or `SIMULATED`.

---

## AI Invoice Generation — Phase 3

### `POST /api/ai/generate-invoice`

Turns a natural-language prompt into an editable invoice draft.

Request:

```json
{ "prompt": "Invoice Acme Corp for 10 hours of web development at $75/hour, 18% tax, due in 14 days. Contact billing@acmecorp.com" }
```

Response `200`:

```json
{
  "clientName": "Acme Corp",
  "clientEmail": "billing@acmecorp.com",
  "currency": "USD",
  "taxRate": 18.00,
  "dueDate": "2026-11-25",
  "notes": "",
  "items": [
    {
      "description": "Web development",
      "quantity": 10,
      "unitPrice": 75.00
    }
  ],
  "source": "gemini",
  "summary": "Invoice for Acme Corp — 10 × Web development @ 75.00"
}
```

`source` is `"gemini"` or `"local-parser"` (fallback used when no `GEMINI_API_KEY`
is configured or the API call fails — the response always states which engine ran).

Validation: `prompt` required, 10–2000 chars.

---

## Invoices — Phase 2 (CRUD) / Phase 4 (payments)

### `POST /api/invoices`

Creates an invoice (usually from a reviewed AI draft). Server computes
`subtotal`, `taxAmount`, `total` and assigns `invoiceNumber` (`INV-0001`, …).

Request:

```json
{
  "clientName": "Acme Corp",
  "clientEmail": "billing@acmecorp.com",
  "customerId": 3,
  "currency": "USD",
  "taxRate": 18.00,
  "dueDate": "2026-11-25",
  "notes": "Thanks for your business!",
  "aiSource": "gemini",
  "items": [
    { "description": "Web development", "quantity": 10, "unitPrice": 75.00 }
  ]
}
```

Response `201`:

```json
{
  "id": 1,
  "invoiceNumber": "INV-0001",
  "clientName": "Acme Corp",
  "clientEmail": "billing@acmecorp.com",
  "customer": { "id": 3, "name": "Acme Corp" },
  "currency": "USD",
  "taxRate": 18.00,
  "subtotal": 750.00,
  "taxAmount": 135.00,
  "total": 885.00,
  "status": "DRAFT",
  "dueDate": "2026-11-25",
  "notes": "Thanks for your business!",
  "aiSource": "gemini",
  "items": [
    { "id": 1, "description": "Web development", "quantity": 10, "unitPrice": 75.00, "amount": 750.00 }
  ],
  "payment": null,
  "createdAt": "2026-11-11T22:41:00",
  "updatedAt": "2026-11-11T22:41:00"
}
```

Validation: `clientName` required; `items` ≥ 1; `quantity > 0`; `unitPrice ≥ 0`;
`taxRate` 0–100; `currency` ISO-4217 3-letter code.

### `GET /api/invoices`

Query params (all optional): `status` (`DRAFT|SENT|PAID|OVERDUE`),
`customerId`, `search` (matches client name/email/invoice number).

Response `200`: array of invoice objects as above (items included).

### `GET /api/invoices/{id}` → `200` | `404`

Single invoice including `payment` (latest payment record, if any).

### `PUT /api/invoices/{id}`

Full update while `status` is `DRAFT` (same body as create). Totals recomputed.
Returns `200` with updated invoice. Editing a `PAID` invoice returns `400`.

### `DELETE /api/invoices/{id}` → `204` | `404`

Allowed for `DRAFT` invoices; `PAID` invoices return `400` (payment audit retained).

### `GET /api/invoices/{id}/payments` → `200` | `404`

All payment attempts for the invoice (see Payments schema below).

---

## Invoice Delivery — PDF download & email

### `GET /api/invoices/{id}/pdf` → `200` | `401` | `404`

Branded PDF rendering (SmartPay AI header, issue/due dates, client details,
line items, tax and total). Works for any status — DRAFT proofs and PAID
receipts alike. `Content-Type: application/pdf`,
`Content-Disposition: attachment; filename="INV-0007.pdf"`; the body is raw
PDF bytes, not JSON. User-scoped like every invoice endpoint.

### `POST /api/invoices/{id}/send`

Emails the invoice to the client: branded HTML body (invoice number, total,
currency, due date, line items) with the generated PDF attached, and — only
when PayPal Sandbox credentials are configured — a genuine checkout link
created through the Orders API.

Response `200` (delivery status is a domain outcome, so it lives in the body):

```json
{
  "deliveryStatus": "NOT_CONFIGURED",
  "message": "Email delivery is not configured on this server. Set the SMTP_HOST, SMTP_PORT, SMTP_USER, SMTP_PASSWORD and SMTP_FROM environment variables and restart the backend. …",
  "recipient": "billing@acmecorp.com",
  "invoiceNumber": "INV-0007",
  "paypalLinkIncluded": false,
  "paypalMode": "NOT_CONFIGURED",
  "sentAt": null
}
```

| `deliveryStatus` | Meaning |
| --- | --- |
| `SENT` | The SMTP provider accepted the message — never claimed otherwise |
| `FAILED` | Delivery was attempted and rejected (reason in `message`), e.g. the invoice has no client email |
| `NOT_CONFIGURED` | SMTP credentials missing; `message` lists the exact env vars to set |

`paypalLinkIncluded` is `true` only when a real PayPal order was created for
this send — a fake payment link is never included.

SMTP settings (environment variables): `SMTP_HOST`, `SMTP_PORT` (default 587),
`SMTP_USER`, `SMTP_PASSWORD`, `SMTP_FROM`, `SMTP_STARTTLS` (default true).
For Gmail: `smtp.gmail.com`, port 587, your address as both `SMTP_USER` and
`SMTP_FROM`, and an app password as `SMTP_PASSWORD`.

---

## PayPal Payment Flow — Phase 4

### `POST /api/invoices/{id}/paypal/order`

Creates a PayPal order for the invoice and flips status `DRAFT → SENT`.

Response `200`:

```json
{
  "orderId": "5O190127TN364715T",
  "approveUrl": "https://www.sandbox.paypal.com/checkoutnow?token=5O190127TN364715T",
  "mode": "LIVE",
  "invoiceStatus": "SENT"
}
```

`mode: "SIMULATED"` (with a synthetic `SIM-…` order id) is returned when sandbox
credentials are not configured — the response is explicitly labelled, never
masquerading as a live payment.

### `POST /api/invoices/{id}/paypal/capture`

Captures the previously created order after buyer approval.

Response `200`:

```json
{
  "invoiceId": 1,
  "status": "PAID",
  "payment": {
    "id": 7,
    "method": "PAYPAL",
    "status": "COMPLETED",
    "amount": 885.00,
    "currency": "USD",
    "paypalOrderId": "5O190127TN364715T",
    "paypalCaptureId": "91B28459XH526732J",
    "payerEmail": "sb-buyer@test.com",
    "capturedAt": "2026-11-11T22:45:10"
  }
}
```

Failure modes: `400` order not yet approved (`ORDER_NOT_APPROVED`),
`502` PayPal unreachable, `409` invoice already paid.

---

## Payments — Phase 2 (read) / Phase 4 (write via PayPal flow)

### `GET /api/payments`

Query params (optional): `invoiceId`, `status`, `method`.

```json
[
  {
    "id": 7,
    "invoiceId": 1,
    "invoiceNumber": "INV-0001",
    "method": "PAYPAL",
    "status": "COMPLETED",
    "amount": 885.00,
    "currency": "USD",
    "paypalOrderId": "5O190127TN364715T",
    "paypalCaptureId": "91B28459XH526732J",
    "payerEmail": "sb-buyer@test.com",
    "capturedAt": "2026-11-11T22:45:10"
  }
]
```

### `GET /api/payments/{id}` → `200` | `404`

Single payment record (same shape).

---

## Customers — Phase 2

### `POST /api/customers`

```json
{
  "name": "Acme Corp",
  "email": "billing@acmecorp.com",
  "phone": "+1 555 0100",
  "company": "Acme Corporation Inc.",
  "city": "Austin",
  "country": "US",
  "notes": "Net-30 preferred"
}
```

Response `201`: full customer object with `id`, `createdAt`, `updatedAt`.
Validation: `name` required (2–200 chars); `email` format-checked when present.

### `GET /api/customers` → `200`

Query param (optional): `search` (name/email/company contains).

### `GET /api/customers/{id}` → `200` | `404`

### `PUT /api/customers/{id}` — same body as create → `200`

### `DELETE /api/customers/{id}` → `204` | `404`

Invoices keep their denormalized `clientName`/`clientEmail` snapshot, so
deleting a customer never corrupts invoice history.

---

## Users & Auth — Phase 6 (deferred by default)

### `POST /api/auth/register`

```json
{ "email": "demo@smartpay.ai", "password": "S3cure!pass", "fullName": "Demo User" }
```

Response `201`: `{ "id": 1, "email": "demo@smartpay.ai", "fullName": "Demo User", "role": "USER" }`.
Passwords stored as BCrypt hashes only.

### `POST /api/auth/login`

Response `200`: `{ "token": "<JWT>", "expiresIn": 86400, "user": { … } }`.

After Phase 6, all endpoints above (except `/api/auth/**` and `/api/health`)
require `Authorization: Bearer <JWT>`, and users only see records they own.

### `GET /api/users/me` → `200`

Current authenticated user profile.

---

## curl Quick Reference (Phase 2 smoke test)

```bash
# health
curl http://localhost:8080/api/health

# AI draft
curl -X POST http://localhost:8080/api/ai/generate-invoice \
  -H "Content-Type: application/json" \
  -d '{"prompt":"Invoice Jane Smith for 15 hours of consulting at $100/hour, 10% tax, due in 14 days"}'

# create invoice from draft
curl -X POST http://localhost:8080/api/invoices \
  -H "Content-Type: application/json" \
  -d '{"clientName":"Jane Smith","currency":"USD","taxRate":10,"dueDate":"2026-11-25","items":[{"description":"Consulting","quantity":15,"unitPrice":100}]}'

# list
curl http://localhost:8080/api/invoices

# payment link + capture
curl -X POST http://localhost:8080/api/invoices/1/paypal/order
curl -X POST http://localhost:8080/api/invoices/1/paypal/capture

# branded PDF download (auth token required)
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/invoices/1/pdf -o INV-0001.pdf

# email the invoice (reports SENT / FAILED / NOT_CONFIGURED)
curl -X POST -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/invoices/1/send
```
