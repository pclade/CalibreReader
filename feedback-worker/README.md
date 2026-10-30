# Calibre To Go feedback service

This Cloudflare Worker provides the public feedback form used by the Android app.

## Protection

The form uses two independent protections:

1. Cloudflare Turnstile. The browser receives only the site key. The Turnstile
   secret is stored as a Worker secret and verification is performed server-side.
2. Cloudflare Workers Rate Limiting. A browser-local installation identifier is
   limited to three submissions per minute.

Successful submissions are converted into GitHub issues in the configured repository.
No GitHub token is shipped with the Android app.

## Setup

1. Create a Turnstile widget in Cloudflare and set its hostname to the Worker/custom domain.
2. Replace `TURNSTILE_SITE_KEY` in `wrangler.toml`.
3. Set `EXPECTED_HOSTNAME` when using a stable custom hostname.
4. Create a fine-grained GitHub token with **Issues: Read and write** permission for
   `pclade/CalibreReader`.
5. Store the secrets:

   ```bash
   npx wrangler secret put TURNSTILE_SECRET_KEY
   npx wrangler secret put GITHUB_TOKEN
   ```

6. Deploy:

   ```bash
   npm install
   npm run deploy
   ```

7. Configure the Android build with the resulting HTTPS URL:

   ```properties
   FEEDBACK_URL=https://feedback.example.com/
   ```

   Put it in local.properties for local builds, a Gradle property, or the
   FEEDBACK_URL environment variable in CI.

The Android feedback button is hidden when FEEDBACK_URL is empty.

## Privacy

The form intentionally has no email/name field. Do not request or submit passwords,
access tokens, private Calibre metadata, or other sensitive information. GitHub issues
created from this form are visible according to the repository's issue visibility.
