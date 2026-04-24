# Security Policy

## Sensitive Files

The following files are **gitignored** and must never be committed to this public repository:

| File | Contains |
|---|---|
| `local.properties` | Finnhub API key (local dev) |
| `app/google-services.json` | Firebase project config |
| `secrets/` | Firebase service account keys |

All secrets used in CI are stored as **GitHub Actions repository secrets**, not in code.

## Reporting a Vulnerability

If you discover a security issue, please open a [GitHub Issue](../../issues) marked **[Security]**
or email the maintainer directly. Do not include sensitive keys or credentials in public issues.
