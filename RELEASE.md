# Releasing HERP POS

The app ships as a signed APK attached to a **GitHub Release**. Installed apps
check GitHub on launch and offer any newer release as an in-app update
(see `data/GithubUpdater.kt` + `ui/update/UpdateGate.kt`).

A push of a `v*` tag triggers `.github/workflows/release.yml`, which builds a
**signed** release APK and publishes it to the matching GitHub Release.

---

## One-time setup

1. **Point the app at your repo.** In `gradle.properties` set:
   ```properties
   githubRepo=your-user/your-repo
   ```
   (CI also passes this automatically.)

2. **Add the signing secrets.** Repo → **Settings → Secrets and variables →
   Actions → New repository secret**. Add all four:

   | Secret | Value |
   |---|---|
   | `KEY_ALIAS` | `herp-pos` |
   | `KEYSTORE_PASSWORD` | your keystore password |
   | `KEY_PASSWORD` | same password |
   | `KEYSTORE_BASE64` | `base64 -w0 release.keystore` output |

   > Every release must be signed with the **same** keystore, or Android rejects
   > the update with a signature mismatch. Back up `release.keystore` + password.

---

## Cut a release

1. Commit your changes.
2. Pick the next version and tag it (semantic version, `v`-prefixed):
   ```bash
   git tag v1.1.0
   git push origin v1.1.0
   ```
3. GitHub Actions builds the signed APK and creates the release automatically.
   The APK is named `herp-pos-1.1.0.apk`. `versionCode` = the CI run number,
   `versionName` = the tag without `v`.

Watch it under the repo's **Actions** tab; the result appears under **Releases**.

### Custom release notes
By default the workflow auto-generates notes from commits/PRs
(`generate_release_notes: true`). To write your own, edit the release on GitHub
after it's published — the text you put in the release **body** is what users see
in the in-app "Update available" dialog. Keep it short and user-facing, e.g.:

```
- Faster menu search
- Fix: receipt total rounding
- New: reprint last order
```

---

## How the update reaches users

1. On launch the app calls `releases/latest` for `githubRepo`.
2. If the release tag's version is higher than the installed `versionName`,
   it shows **Update available** with the release notes.
3. **Update now** downloads the `.apk` asset and opens the system installer.
   (Android may ask the user to allow installs from this app the first time.)

Drafts and pre-releases are ignored.

---

## Test a signed build locally

```bash
KEYSTORE_FILE="release.keystore" \
KEYSTORE_PASSWORD="<password>" \
KEY_ALIAS="herp-pos" \
KEY_PASSWORD="<password>" \
./gradlew :app:assembleRelease -PappVersionName=1.1.0 -PappVersionCode=2
```

Output: `app/build/outputs/apk/release/app-release.apk`.
Without the `KEYSTORE_*` env vars, release builds fall back to the debug key so
local testing still works — but debug-signed APKs can't auto-update a
release-signed install.

---

## Version numbering

- **Tag / versionName** — semantic, human-facing: `v1.1.0`. Update comparison
  uses this.
- **versionCode** — the CI run number, always increasing. Android requires a
  higher `versionCode` to install an update.
