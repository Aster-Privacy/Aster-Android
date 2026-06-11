# Aster Mail Android - Maestro E2E Test Kit

End-to-end UI tests for the Aster Mail Android app, run against a real device.

## One-time setup

1. **Install Maestro** (Windows native install, ~300 MB download):

   ```powershell
   $url = ((Invoke-RestMethod "https://api.github.com/repos/mobile-dev-inc/maestro/releases/latest").assets | Where-Object name -eq "maestro.zip").browser_download_url
   Invoke-WebRequest $url -OutFile "$env:TEMP\maestro.zip"
   Expand-Archive "$env:TEMP\maestro.zip" -DestinationPath "$env:USERPROFILE\.maestro" -Force
   [Environment]::SetEnvironmentVariable("PATH", "$env:USERPROFILE\.maestro\maestro\bin;" + [Environment]::GetEnvironmentVariable("PATH", "User"), "User")
   ```

   Restart your terminal and verify: `maestro --version`.

2. **Connect your device** (USB debugging enabled, plugged in).

   Verify: `adb devices` shows your device as `device` not `unauthorized`.

3. **Install the debug build** of the app on the device:

   ```powershell
   .\gradlew :app:installDebug
   ```

4. **Create `.maestro/.env`** with test account credentials (copy from `.env.example`).

## Turnstile / CAPTCHA constraint (READ THIS FIRST)

The prod sign-in and register screens require Cloudflare Turnstile, which Maestro
cannot solve automatically. The flow kit is built around this reality:

- **Unauthenticated flows** (welcome, form layout, register navigation, forgot password)
  run fully unattended.
- **Authenticated flows** (everything else - inbox, compose, settings, contacts, etc.)
  assume the app is already signed in.
- **You sign in once manually** with the test account on the device. Maestro then
  exercises every post-auth screen against that session. The session survives across
  Maestro runs as long as you don't `clearState`.

If a flow that needs auth runs and you are not signed in, the shared
`setup/sign_in.yaml` will fail with a clear assertion error - sign in on the device,
then re-run.

For sign-in itself, register flow, etc., use the `manual` tag - those flows pause
at the captcha for you to tap, then continue.

## Running flows

Run all flows against the connected device:

```powershell
.\.maestro\run_all.ps1
```

Run a single flow:

```powershell
maestro test .\.maestro\flows\auth\03_sign_in_success.yaml
```

Run by tag (only smoke flows, etc):

```powershell
maestro test .\.maestro\flows\ --include-tags=smoke
```

Record a video while running (useful for debugging UI glitches):

```powershell
maestro record .\.maestro\flows\mail\11_open_thread.yaml
```

## Layout

```
.maestro/
  config.yaml              # workspace config, flow order, tag set
  .env                     # test creds (gitignored)
  .env.example             # template
  flows/
    00_smoke_app_launches.yaml
    auth/                  # welcome, sign in, register, forgot pw
    mail/                  # inbox, thread, compose, search, drawer
    settings/              # all settings detail screens
    contacts/              # contacts CRUD
    subscriptions/         # subs list, upgrade sheet
    upgrade/               # upgrade flow
  run_all.ps1              # PowerShell runner that produces a report
  .screenshots/            # captured screenshots (gitignored)
  .reports/                # JUnit reports (gitignored)
```

## Writing a new flow

Each flow is a `.yaml` file under `flows/<area>/`:

```yaml
appId: org.astermail.android
tags:
  - mail
---
- launchApp:
    clearState: false
- tapOn: "Inbox"
- assertVisible: "Compose"
- takeScreenshot: open_inbox
```

Use **text selectors** by default — they survive testTag refactors and match what a
human sees. For ambiguous text (e.g. a button labeled "Cancel" appearing twice),
use `index` or a sibling `containsChild`.

## Conventions

- Filenames: `NN_short_description.yaml`, numbered in execution order.
- Tag every flow with at least one of: `smoke`, `auth`, `mail`, `settings`, `contacts`, `subscriptions`.
- Screenshots go to `.screenshots/<flow_name>/` automatically when using `takeScreenshot`.
- Never hardcode test creds in YAML; always reference `${TEST_EMAIL}` / `${TEST_PASSWORD}`.
- Flows must be **idempotent** - running twice in a row should pass both times.
  If a flow creates state (a contact, a draft), it must clean up at the end.

## Troubleshooting

- **"No connected devices found"**: run `adb devices`, reattach USB cable, accept the debug prompt on phone.
- **Flow times out on a tap**: the text selector probably no longer matches a UI string. Run `maestro studio` to inspect the live UI hierarchy.
- **App crashes mid-flow**: check `adb logcat | grep AndroidRuntime` for the stack trace.
