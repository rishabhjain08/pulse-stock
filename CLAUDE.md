# CLAUDE.md — Project Rules for Claude Code

Guidelines derived from real mistakes made during development of this project.

---

## CI / Build

### Never patch build files mid-pipeline to inject values

**Wrong:**
```yaml
- run: sed -i "s/versionCode = .*/versionCode = $RUN_NUMBER/" app/build.gradle.kts
- run: gradle bundleRelease   # second build just to pick up the change
```

**Right:**
```kotlin
// build.gradle.kts — read at Gradle configuration time
versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
```
```yaml
- env:
    VERSION_CODE: ${{ github.run_number }}
  run: gradle bundleRelease   # one build, correct value from the start
```

**Why:** Gradle reads `build.gradle.kts` once during the configuration phase, before any task executes. Patching the file after a build has run forces a full second build to pick up the change. Any value that varies between builds (versionCode, API keys, feature flags) must be injected as an env var read at configuration time — not written into a file mid-pipeline.

---

## README

### Keep README in sync with every change

Update `README.md` in the same commit as any code, config, workflow, secret, or permission change that affects setup or usage. Never batch README updates separately.

---

## Security

### Never hardcode secrets in tracked files

The repo is public. API keys, keystore passwords, and service account credentials must live in:
- `local.properties` (gitignored) for local dev
- GitHub Actions secrets for CI

Read them via `System.getenv()` in `build.gradle.kts` or via `${{ secrets.NAME }}` in workflows. See `local.properties.template` for the expected keys.

---

## Diagnostic Toolkit

Diagnostic scripts are located in `infra/scripts/`. They do not contain secrets and are used for troubleshooting the production environment.

| Script | Purpose | Usage |
|---|---|---|
| `verify-plaid.js` | Verifies Plaid credentials by calling `/link/token/create` directly from the local machine. | `NODE_PATH=infra/node_modules:infra/lambda/node_modules node infra/scripts/verify-plaid.js` |
| `fetch-logs.js` | Fetches the latest CloudWatch logs for the `poarvault-link-token` Lambda. | `node infra/scripts/fetch-logs.js` |
| `check-ssm.js` | Checks the Plaid credential values currently stored in AWS SSM (masking secrets). | `node infra/scripts/check-ssm.js` |

---

## AWS / SSM

### Always decrypt SSM parameters in Lambda

**Wrong:**
```javascript
getParam('/poarvault/plaid-env', false) // returns ciphertext if type is SecureString
```

**Right:**
```javascript
getParam('/poarvault/plaid-env') // defaults to WithDecryption: true
```

---

## Plaid Integration

### Some institutions require `min_last_updated_datetime` for balances

**Problem:** Calling `/accounts/balance/get` for institutions like Capital One (`ins_128026`) fails with `INVALID_FIELD` if `min_last_updated_datetime` is missing.

**Solution:** Always provide a default `min_last_updated_datetime` (e.g., 30 days ago) in the `/balances` Lambda if not explicitly passed by the client. This satisfies the API requirement while allowing for cached data.
