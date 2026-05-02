# PulseStock

A real-time stock price HUD for Samsung Galaxy S25, built for Android 16 (API 36).

Tap a Quick Settings tile → a floating overlay appears showing live prices for up to 5 stocks, streamed via WebSocket from [Finnhub.io](https://finnhub.io/) with sub-second updates. Dismiss it by tapping the close button or tapping outside the card.

---

## Features

- **Quick Settings Tile** — one tap to start or stop the HUD from the notification shade
- **Floating Overlay** — semi-transparent card drawn over any app or home screen
- **Live WebSocket Stream** — tick-by-tick prices from Finnhub.io (free tier, no credit card)
- **Indian Stock Support** — NSE and BSE stocks via Yahoo Finance (free, no key); search `NSE:RELIANCE`, `NSE:INFY`, etc.
- **Odometer Animation** — individual digits scroll vertically on price change (Robinhood style)
- **Colour-coded P&L** — green `#00C805` for gains, red `#FF5A5F` for losses
- **Android 16 Now Bar** — live price chip next to the system clock via `setShortCriticalText`
- **Haptic Ticks** — low-tick vibration on every price update (Samsung Haptic Engine)
- **User-configurable watchlist** — add or remove up to 5 symbols from inside the app
- **Battery-safe** — WebSocket opens only while the HUD is visible; closes immediately on dismiss

---

## Screenshots

> _Add screenshots here once the app is running on device._

---

## Requirements

| Requirement | Detail |
|---|---|
| Device | Samsung Galaxy S25 (or any Android 16 phone) |
| OS | Android 16 (API 36) / One UI 8.0 |
| Android Studio | Ladybug or newer |
| JDK | 17 |
| Finnhub API key | Free — [finnhub.io/register](https://finnhub.io/register) |

---

## Getting Started

### 1. Clone the repo

```bash
git clone https://github.com/rishabhjain08/pulse-stock.git
cd pulse-stock
```

### 2. Add your Finnhub API key

The app streams stock prices from Finnhub.io. A free API key is required — it's the credential Finnhub uses to track your usage against the free-tier limits (60 calls/min, unlimited WebSocket symbols).

Create `local.properties` at the project root (or copy from the provided template):

```properties
FINNHUB_API_KEY=your_finnhub_api_key_here
POARVAULT_API_URL=https://your-api-id.execute-api.us-east-1.amazonaws.com
POARVAULT_API_KEY=your_poarvault_api_key_here
sdk.dir=/path/to/your/android/sdk
```

**Why `local.properties` and not directly in code?** This repo is public. Any key committed to source is permanently exposed (even if later deleted — git history is forever). `local.properties` is listed in `.gitignore` so it is never committed. The build system reads it at compile time via `System.getenv()` in `build.gradle.kts` and injects it as a `BuildConfig` field.

A template with all expected keys is provided at [`local.properties.template`](local.properties.template).

### 3. Add google-services.json

Firebase is used for crash reporting and CI app distribution. Each Firebase project has a unique `google-services.json` that tells the Android SDK which project to connect to.

Download it from your [Firebase console](https://console.firebase.google.com/) (Project settings → Your apps → Download `google-services.json`) and place it at:

```
app/google-services.json
```

**Why not committed?** The file contains your Firebase project ID and API keys. Committing it would expose your Firebase project to abuse (quota exhaustion, unauthorized data access). It is gitignored.

> If you don't have a Firebase project, create one at [console.firebase.google.com](https://console.firebase.google.com/), add an Android app with package name `com.pulsestock.app`, and download the file.

### 4. Open in Android Studio

Open the project root in Android Studio. Let Gradle sync (it downloads all dependencies declared in `build.gradle.kts`). Then run on a connected S25 or any Android 16 device/emulator.

**Why Android Studio?** It handles the Gradle toolchain, JDK version pinning, and ADB device connection automatically. Building from the command line is also possible (`./gradlew assembleDebug`) but Studio is the recommended path for first-time setup.

### 5. Grant permissions on device

The app requests two permissions on first launch — both are required for the core HUD feature:

- **Appear on top** (`SYSTEM_ALERT_WINDOW`) — Android requires this special permission for any app that draws a floating window over other apps. Without it, the overlay cannot be shown.
- **Post notifications** (`POST_NOTIFICATIONS`) — Required since Android 13 for any app running a foreground service. The HUD runs as a foreground service so Android doesn't kill it while you're using other apps; the notification is the system's way of making that service visible to the user.

Both prompts open the relevant system settings screen directly. Grant them and return to the app.

### 6. Add the Quick Settings tile

The HUD is toggled from Quick Settings (the panel that appears when you swipe down twice), not from an app launcher icon. You need to add the PulseStock tile once:

1. Pull down the notification shade twice to open Quick Settings
2. Tap the **pencil / edit** icon at the bottom
3. Find **PulseStock** in the inactive tiles list and drag it into your active tiles
4. Tap it to start the HUD — a floating overlay will appear

**Why Quick Settings?** It gives one-tap access to the HUD from anywhere, without unlocking or navigating to the app. The tile also shows live status (active/inactive) so you always know if the service is running.

---

## Project Structure

```
app/src/main/
├── java/com/pulsestock/app/
│   ├── MainActivity.kt              # Settings screen + permission flow
│   ├── PulseHUDService.kt           # Foreground service, overlay, haptics, Now Bar
│   ├── PulseTileService.kt          # Quick Settings tile
│   ├── ServiceLifecycleOwner.kt     # Lifecycle bridge for ComposeView-in-Service
│   ├── data/
│   │   ├── FinnhubModels.kt         # Serializable WebSocket message models
│   │   ├── StockStreamManager.kt    # Ktor WebSocket client + reconnect logic
│   │   └── StockPreferences.kt      # DataStore-backed watchlist persistence
│   └── ui/
│       ├── HUDContent.kt            # Floating overlay card layout
│       ├── SettingsScreen.kt        # Add / remove symbols
│       └── components/
│           ├── OdometerText.kt      # Per-digit animated price display
│           └── StockRow.kt          # Symbol | $Price | ±% row
└── res/
    ├── drawable/                    # ic_pulse_tile.xml, ic_launcher_foreground.xml
    ├── mipmap-anydpi-v26/           # Adaptive launcher icons
    └── values/                      # strings, themes, colors
```

---

## Tech Stack

| Layer | Library |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Animation | `AnimatedContent` with `slideInVertically` / `slideOutVertically` |
| Networking | Ktor 3 (WebSockets over OkHttp) |
| Serialization | kotlinx.serialization |
| Persistence | Jetpack DataStore (Preferences) |
| System | `WindowManager` overlay, `TileService`, `VibrationEffect.Composition` |
| Build | AGP 8.9.1, Gradle 9.4.1, Kotlin 2.0.21 |

---

## CI / CD

Two independent deployment pipelines are available. Set up whichever suits your workflow — or both.

Builds that only change documentation or non-build files are skipped automatically on both pipelines.

Each pipeline is gated by a **repository variable** (not a secret). Set the variable to `true` to enable a pipeline, leave it unset or `false` to disable it.

**Why a variable and not a secret?** Variables are visible in the GitHub Actions UI, making it easy to see at a glance which pipelines are active. Secrets are for sensitive values only; an on/off flag isn't sensitive.

**Settings → Secrets and variables → Actions → Variables tab:**

| Variable | Set to | Effect |
|---|---|---|
| `ENABLE_FIREBASE_DISTRIBUTION` | `true` | Enables Firebase App Distribution pipeline |
| `ENABLE_PLAY_STORE_DEPLOY` | `true` | Enables Play Store internal track pipeline |

---

### Option A — Firebase App Distribution (`firebase_distribute.yml`)

Builds a **debug APK** and delivers it directly to testers via Firebase App Distribution. No Play Store account or signing keystore needed. Best for rapid internal testing.

**Enable:** Set repository variable `ENABLE_FIREBASE_DISTRIBUTION = true`

**Required secrets** — these go in **Settings → Secrets and variables → Actions → Secrets tab:**

| Secret | Where to get it | Why it's needed |
|---|---|---|
| `FINNHUB_API_KEY` | [finnhub.io](https://finnhub.io/) dashboard | Baked into the APK at build time via `BuildConfig`; never hardcoded in source |
| `POARVAULT_API_URL` | `node infra/scripts/get-outputs.js` after running setup | PoarVault Lambda base URL; baked into the build via `BuildConfig` |
| `POARVAULT_API_KEY` | `node infra/scripts/get-outputs.js` after running setup | Authenticates Android app to PoarVault Lambda; baked into the build via `BuildConfig` |
| `GOOGLE_SERVICES_JSON` | Firebase console → Project settings → `google-services.json` | CI doesn't have access to your local file; the secret injects it during the build |
| `FIREBASE_APP_ID` | Firebase console → Project settings → Your apps → App ID (`1:xxx:android:xxx`) | Tells the Firebase CLI which app to distribute to |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | Firebase console → Project settings → Service accounts → Generate key | Authenticates CI to Firebase without exposing your personal Google account credentials |

**One-time Firebase setup:**
- Grant the service account the **Firebase App Distribution Admin** role in Google Cloud IAM
- Create a tester group named exactly `internal-testers` in **App Distribution → Testers & Groups** — the workflow targets this group by name

---

### Option B — Google Play Internal Track (`build_deploy.yml`)

Builds a **signed release AAB** and publishes it to the Play Store internal testing track. Testers install via the Play Store — no sideloading required. Best for devices with MDM restrictions.

**Enable:** Set repository variable `ENABLE_PLAY_STORE_DEPLOY = true`

**Why a signed AAB?** The Play Store requires all uploads to be signed with a consistent release keystore. The keystore proves that future app updates come from the same publisher. Lose the keystore and you can never update the app on the Play Store — keep it safe.

**One-time keystore generation (run locally, keep the file safe):**
```bash
keytool -genkey -v \
  -keystore pulsestock.keystore \
  -alias pulsestock \
  -keyalg RSA -keysize 2048 -validity 10000

# Base64-encode it so it can be stored as a GitHub Secret (macOS)
base64 -i pulsestock.keystore | pbcopy
# Linux: base64 pulsestock.keystore | xclip -selection clipboard
```

**Required secrets:**

| Secret | Where to get it | Why it's needed |
|---|---|---|
| `FINNHUB_API_KEY` | [finnhub.io](https://finnhub.io/) dashboard | Baked into the release build at compile time |
| `POARVAULT_API_URL` | `node infra/scripts/get-outputs.js` after running setup | PoarVault Lambda base URL; baked into the release build |
| `POARVAULT_API_KEY` | `node infra/scripts/get-outputs.js` after running setup | Authenticates Android app to PoarVault Lambda; baked into the release build |
| `GOOGLE_SERVICES_JSON` | Firebase console → `google-services.json` | Same reason as Option A |
| `KEYSTORE_BASE64` | Base64 output of the command above | CI needs the keystore to sign the AAB; base64 encoding lets it be stored as a text secret |
| `KEYSTORE_PASSWORD` | Password chosen during `keytool` | Unlocks the keystore file |
| `KEY_ALIAS` | `pulsestock` (or whatever alias you chose) | Identifies which key within the keystore to sign with |
| `KEY_PASSWORD` | Key password (can match keystore password) | Unlocks the individual key within the keystore |
| `PLAY_SERVICE_ACCOUNT_JSON` | Play Console → Setup → API access → Create service account → download JSON key | Authenticates CI to the Play Store without your personal Google account |

**Play Console service account permissions — grant exactly these two, nothing else:**
- ✅ **Release apps to testing tracks** — lets CI upload the AAB and create a release
- ✅ **View app information and download bulk reports (read-only)** — base permission the API requires

**Why not grant Admin?** Least-privilege principle. A compromised CI token should only be able to upload an APK — not modify store listings, access financial data, or publish to production.

**Play Console setup:**
1. Create the app and complete store listing, content rating, data safety, and target audience
2. Go to **Testing → Internal testing** and add tester emails
3. The service account gets access automatically once linked via API access

> **First release only:** CI uploads as `status: draft` because Play Console requires a human to review a brand-new app before rollout. Go to Play Console → Internal testing → promote the draft → Start rollout. After this one-time step, change `status: draft` to `status: completed` in `build_deploy.yml` so future CI builds go live to testers automatically.

---

## PoarVault Backend Setup (optional)

PoarVault is the financial "slice and dice" feature inside PulseStock. It connects to bank accounts via Plaid and stores all financial data **locally on the device** — no customer data ever lands in the cloud.

> Skip this entire section if you only want the stock HUD.

### Why a backend at all?

Plaid's security model requires that `PLAID_CLIENT_ID` and `PLAID_SECRET` never appear in client-side code. Two operations are impossible directly from the Android app:

1. **`/link/token/create`** — generates the `link_token` needed to open Plaid Link UI
2. **`/item/public_token/exchange`** — exchanges the one-time `public_token` for a persistent `access_token`

The backend is a thin HTTPS proxy (5 AWS Lambda functions) that injects the Plaid credentials and forwards the call. It is **stateless** — it stores nothing. All financial data (transactions, balances, access tokens) is returned to the app and stored on-device only.

### Architecture

```
Android App
    │  POST /link-token        ← x-api-key header on every request
    │  POST /exchange-token       (random key generated at setup time,
    │  POST /transactions          stored in SSM, added to local.properties)
    │  POST /balances
    │  POST /disconnect
    ▼
HTTP API Gateway (AWS)          ← single public HTTPS endpoint
    ▼
AWS Lambda (Node.js 20)         ← stateless; one function per route
    reads PLAID_CLIENT_ID + PLAID_SECRET from SSM Parameter Store
    stores NOTHING
    ▼
Plaid API
```

Plaid `access_token`s returned by `/exchange-token` are encrypted on-device using Android Keystore (AES-256-GCM, hardware-backed on S25). Transaction and balance data lives in a local Room database. Nothing is persisted in AWS.

### Prerequisites

- [Node.js 20+](https://nodejs.org/) — used for both the setup scripts and the Lambda runtime
- A [Plaid account](https://dashboard.plaid.com/signup) — free sandbox tier is sufficient for development
- An AWS account with an IAM user that has programmatic (API key) access

**Minimum IAM permissions for the deployment user** — attach these managed policies to the IAM user whose keys go in `infra/.env`:

| Policy | Why it's needed |
|---|---|
| `AmazonS3FullAccess` | Creates the artifact bucket and uploads Lambda zips |
| `AWSCloudFormationFullAccess` | Deploys and updates the CloudFormation stacks |
| `AWSLambda_FullAccess` | CloudFormation creates and updates the Lambda functions |
| `AmazonAPIGatewayAdministrator` | CloudFormation creates the HTTP API Gateway |
| `AmazonSSMFullAccess` | Writes Plaid credentials and API key to SSM Parameter Store |
| `IAMFullAccess` | CloudFormation creates the Lambda execution IAM role |

**Why a dedicated IAM user and not your root account?** Root account credentials give unrestricted access to everything in your AWS account. A dedicated IAM user scoped to only the permissions above limits the blast radius if the credentials are ever exposed.

### Step 1 — Create `infra/.env`

```bash
cp infra/.env.template infra/.env
```

Open `infra/.env` and fill in your AWS credentials and Plaid keys. This file is listed in `.gitignore` — it will never be committed.

**Why a local file and not environment variables?** The setup scripts need to run multiple AWS API calls across multiple CloudFormation stacks. Keeping credentials in a single file makes the workflow repeatable without re-exporting variables in every shell session. The file never leaves your machine.

### Step 2 — Install setup script dependencies

```bash
cd infra
npm install
```

**Why `npm install` here?** The setup scripts (`infra/scripts/`) use the AWS SDK (`@aws-sdk/client-cloudformation`, `@aws-sdk/client-s3`, etc.) to talk to AWS directly — no external CLI tools needed. This `npm install` is for the setup scripts only; it is completely separate from `infra/lambda/package.json`, which holds the Lambda runtime dependencies.

### Step 3 — Run setup (one time)

```bash
npm run setup
```

This does the following in order:

1. **Writes Plaid credentials to SSM Parameter Store** as `SecureString` (encrypted at rest using AWS-managed keys, free tier). SSM is used instead of environment variables or a config file because it keeps secrets out of the Lambda deployment package and out of CloudFormation templates — they're fetched at cold-start over an authenticated API call.

2. **Generates a random 64-character API key** and stores it in SSM under `/poarvault/api-key`. Every request from the Android app must include this key in the `x-api-key` header. This prevents random internet users from invoking your Lambda endpoints. The key is printed once — **copy it to `local.properties`** immediately.

3. **Deploys the bootstrap CloudFormation stack** (`poarvault-bootstrap`) — this creates the S3 artifact bucket (`poarvault-artifacts-{account}-{region}`). It must be a separate stack deployed first because the Lambda code zip needs somewhere to be uploaded *before* the main stack can reference it. The bucket has `DeletionPolicy: Retain` so it survives stack recreations and preserves your deploy history.

4. **Packages the Lambda functions** — runs `npm install --omit=dev` inside `infra/lambda/`, zips the result, and uploads it to the artifact bucket. A timestamp in the zip filename (`lambda-{timestamp}.zip`) ensures each deploy creates a new S3 object, which triggers CloudFormation to actually update the Lambda function code.

5. **Deploys the main CloudFormation stack** (`poarvault`) — creates the IAM execution role, 5 Lambda functions, the HTTP API Gateway, routes, and CloudWatch log groups (90-day retention). All resources are named with the `poarvault-` prefix to avoid collisions with other projects in the same AWS account.

At the end, the script prints the API URL and API key:

```
POARVAULT_API_URL=https://xxx.execute-api.us-east-2.amazonaws.com
POARVAULT_API_KEY=abc123...
```

Add both to `local.properties` — the Android app reads them at build time.

### Subsequent deploys (after Lambda code changes)

```bash
cd infra && npm run deploy
```

Re-packages the Lambda, uploads a new zip, and updates the CloudFormation stack. CloudFormation detects the new S3 key and updates only the Lambda functions — everything else (API Gateway, IAM role, log groups) is left unchanged.

### Retrieve outputs at any time

```bash
cd infra && npm run outputs
```

Re-prints the API URL and API key from CloudFormation and SSM without redeploying anything.

### Tear down

```bash
cd infra && npm run destroy
```

Deletes both CloudFormation stacks and all `/poarvault/*` SSM parameters. The S3 artifact bucket is **retained** (by `DeletionPolicy: Retain` in the bootstrap template) so your Lambda zip history isn't accidentally destroyed. To fully clean up, empty and delete the bucket manually after running destroy.

---

## Data & Privacy

- No user data is collected or transmitted beyond what Finnhub.io requires (your API key).
- All stock data is pulled directly from Finnhub's free-tier WebSocket. No backend server is involved.
- The app requires no account and stores only your watchlist locally on-device.

---

## Free Tier Limits

### Finnhub.io (US stocks)

| Limit | Value |
|---|---|
| API calls / minute | 60 |
| WebSocket symbols | Unlimited (free tier) |
| US stock data | Real-time |
| Cost | Free |

### Yahoo Finance (Indian stocks — NSE/BSE)

| Limit | Value |
|---|---|
| Auth required | None |
| Indian stock data | ~15-second delayed quotes while popup open; 60-second poll when closed |
| Cost | Free (unofficial API) |

The free tier is sufficient for 5 symbols with continuous streaming. Mix US and Indian symbols freely.

---

## Contributing

Pull requests are welcome. For major changes, open an issue first to discuss what you'd like to change.

1. Fork the repo
2. Create a feature branch (`git checkout -b feature/your-feature`)
3. Commit your changes
4. Push and open a pull request against `main`

---

## License

[MIT](LICENSE)
