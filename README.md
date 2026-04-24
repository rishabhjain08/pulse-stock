# PulseStock

A real-time stock price HUD for Samsung Galaxy S25, built for Android 16 (API 36).

Tap a Quick Settings tile → a floating overlay appears showing live prices for up to 5 stocks, streamed via WebSocket from [Finnhub.io](https://finnhub.io/) with sub-second updates. Dismiss it by tapping the close button or tapping outside the card.

---

## Features

- **Quick Settings Tile** — one tap to start or stop the HUD from the notification shade
- **Floating Overlay** — semi-transparent card drawn over any app or home screen
- **Live WebSocket Stream** — tick-by-tick prices from Finnhub.io (free tier, no credit card)
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

Create `local.properties` at the project root (it is gitignored and will never be committed):

```properties
FINNHUB_API_KEY=your_finnhub_api_key_here
sdk.dir=/path/to/your/android/sdk
```

A template is provided at [`local.properties.template`](local.properties.template).

### 3. Add google-services.json

Download `google-services.json` from your [Firebase console](https://console.firebase.google.com/) and place it at:

```
app/google-services.json
```

This file is gitignored and will never be committed.

### 4. Open in Android Studio

Open the project root in Android Studio. Let it sync Gradle, then run on a connected S25 (or any Android 16 device/emulator).

### 5. Grant permissions on device

The app will prompt for two permissions on first launch:

- **Appear on top** (`SYSTEM_ALERT_WINDOW`) — required for the floating overlay
- **Post notifications** — required for the foreground service and Now Bar chip

### 6. Add the Quick Settings tile

1. Pull down the notification shade twice
2. Tap the pencil / edit icon
3. Find **PulseStock** and drag it into your active tiles
4. Tap it to start the HUD

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

---

### Option A — Firebase App Distribution (`firebase_distribute.yml`)

Builds a **debug APK** and delivers it directly to testers via Firebase App Distribution. No Play Store account or signing keystore needed. Best for rapid internal testing.

**Required secrets:**

| Secret | Where to get it |
|---|---|
| `FINNHUB_API_KEY` | [finnhub.io](https://finnhub.io/) dashboard |
| `GOOGLE_SERVICES_JSON` | Firebase console → Project settings → `google-services.json` |
| `FIREBASE_APP_ID` | Firebase console → Project settings → Your apps → App ID (`1:xxx:android:xxx`) |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | Firebase console → Project settings → Service accounts → Generate key (grant **Firebase App Distribution Admin** role in Google Cloud IAM) |

**Firebase setup:**
- Create a tester group named exactly `internal-testers` in **App Distribution → Testers & Groups**

---

### Option B — Google Play Internal Track (`build_deploy.yml`)

Builds a **signed release AAB** and publishes it to the Play Store internal testing track. Testers install via the Play Store — no sideloading required. Best for devices with MDM restrictions.

**One-time keystore generation (run locally, keep the file safe):**
```bash
keytool -genkey -v \
  -keystore pulsestock.keystore \
  -alias pulsestock \
  -keyalg RSA -keysize 2048 -validity 10000

# Encode for GitHub Secrets (macOS)
base64 -i pulsestock.keystore | pbcopy
```

**Required secrets:**

| Secret | Where to get it |
|---|---|
| `FINNHUB_API_KEY` | [finnhub.io](https://finnhub.io/) dashboard |
| `GOOGLE_SERVICES_JSON` | Firebase console → `google-services.json` |
| `KEYSTORE_BASE64` | Base64-encoded keystore file (command above) |
| `KEYSTORE_PASSWORD` | Password chosen during `keytool` |
| `KEY_ALIAS` | `pulsestock` (or whatever alias you chose) |
| `KEY_PASSWORD` | Key password (can match keystore password) |
| `PLAY_SERVICE_ACCOUNT_JSON` | Play Console → Setup → API access → Create service account → download JSON key |

**Play Console service account permissions (grant exactly these two, nothing else):**
- ✅ **Release apps to testing tracks** — lets CI upload the AAB and create releases
- ✅ **View app information and download bulk reports (read-only)** — required base permission for the API

Do not grant Admin, "Release to production", or any financial/store presence permissions to the CI service account.

**Play Console setup:**
1. Create the app and complete store listing, content rating, data safety, and target audience
2. Go to **Testing → Internal testing** and add tester emails
3. The service account gets access automatically once linked via API access

---

## Data & Privacy

- No user data is collected or transmitted beyond what Finnhub.io requires (your API key).
- All stock data is pulled directly from Finnhub's free-tier WebSocket. No backend server is involved.
- The app requires no account and stores only your watchlist locally on-device.

---

## Free Tier Limits (Finnhub.io)

| Limit | Value |
|---|---|
| API calls / minute | 60 |
| WebSocket symbols | Unlimited (free tier) |
| US stock data | Real-time |
| Cost | Free |

The free tier is sufficient for 5 symbols with continuous streaming.

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
