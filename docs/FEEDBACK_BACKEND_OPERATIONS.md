# Feedback Backend Operations

This document records the operational boundaries for the PointIQ feedback Pages
Function. Production submissions remain disabled until the required bindings,
secrets, and protections have been configured and verified.

## Deployment and configuration

- Keep the Cloudflare Pages repository root blank, build command `exit 0`, and
  build output `feedback-web`.
- Pages Functions are discovered from the repository-root `functions/` directory;
  the API routes are `functions/api/feedback.js` and
  `functions/api/feedback-config.js`.
- Bind the D1 database as `FEEDBACK_DB` and the private R2 bucket as
  `FEEDBACK_MEDIA`.
- Configure `FEEDBACK_ALLOWED_ORIGIN` as the exact HTTPS origin of the website.
  A path, query string, credentials, or non-HTTPS scheme is rejected by the
  function configuration checks.
- Keep `FEEDBACK_SUBMISSIONS_ENABLED=false` until Turnstile, D1, R2, and the
  Cloudflare edge protections have been verified in an isolated preview.

## Rate limits and distributed abuse

The application rate limit is stored in D1 and updated/read in one D1 batch,
so it is shared by concurrent requests handled by different Worker instances.
Expired rate-limit rows are removed by the same batch using a cutoff before the
current window; the current active bucket is not eligible for deletion.

This does not eliminate distributed abuse. Attackers can rotate IP addresses,
use botnets or proxies, consume Turnstile verification capacity, and cause
request, D1, R2, or log costs. Cloudflare WAF and Rate Limiting rules must be
configured separately for the production hostname. At minimum, add a rule for
`POST /api/feedback` with an edge action and threshold appropriate for the
expected traffic, and consider managed bot protections and a request-body
inspection/size policy available to the account. The exact thresholds and
actions are deployment decisions and are not encoded in this repository.

## R2 orphan reconciliation

The request handler attempts to delete an uploaded object when the later D1
insert fails. That cleanup is best effort because R2 and D1 do not share an
atomic transaction. A Worker interruption or a failed cleanup can therefore
leave an object under the `feedback/` prefix without a matching D1 row.

The practical reconciliation process is an operator-controlled or separately
scheduled maintenance job:

1. List R2 objects under `feedback/`.
2. Read the non-null `attachment_key` values from D1.
3. Compare the two sets and record unmatched R2 keys for review.
4. Delete only reviewed unmatched objects, with an audit record of the action.

No automatic attachment-age deletion period is defined. The D1
`attachment_key` is authoritative for retained submissions, and unmatched
objects must be reviewed before deletion.

## Preview readiness boundary

An isolated preview can be considered only after the Pages project discovers
the root `functions/` directory, the preview bindings are present, and the
submission flag remains false until a deliberate verification enables it. This
repository does not deploy WAF rules, run migrations, or perform the R2/D1
reconciliation job.
