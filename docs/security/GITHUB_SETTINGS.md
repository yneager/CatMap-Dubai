# GitHub settings only the owner can change

The workflows in `.github/` are hardened in code (Phase 8 of `docs/done/MAJOR_UPDATE_PLAN.md`), but some protection only
exists as repository settings. These are click-steps for **github.com/yneager/CatMap-Dubai**, signed in as the
owner. Menu names are GitHub's as of October 2026; if one has moved, the search box on the Settings page finds it.

Do them in this order. Steps 1–4 matter most: together they stop a stolen password or token from shipping a
malicious update to every phone (threat model S6).

## 1. Two-factor sign-in on your GitHub account

1. Click your picture (top right) → **Settings** → **Password and authentication**.
2. Under **Two-factor authentication**, click **Enable two-factor authentication** and follow the steps (an
   authenticator app or a passkey).
3. Save the recovery codes somewhere safe (not in the repo).

## 2. A "release" environment that needs your approval

The release workflow's `build` job uses an environment called `release`. GitHub creates it automatically on the
first run, but without protection. Set it up first:

1. Repository → **Settings** → **Environments** → **New environment** → name it exactly `release` →
   **Configure environment**.
2. Tick **Required reviewers**, add yourself (`yneager`). Leave **Prevent self-review** unticked (you approve your
   own releases).
3. **Deployment branches and tags** → **Selected branches and tags** → **Add deployment branch or tag rule** →
   choose **Tag**, pattern `v*.*.*`.
4. Click **Save protection rules**.
5. Recommended: move the signing secrets into this environment, so only the build job can read them.
   * On the same page, under **Environment secrets**, click **Add environment secret** for each of
     `CATMAP_KEYSTORE_BASE64`, `CATMAP_KEYSTORE_PASSWORD`, `CATMAP_KEY_ALIAS`, `CATMAP_KEY_PASSWORD`,
     `GOOGLE_SERVICES_JSON` (and `MAPTILER_KEY` if you use it), with the same values as today.
   * Then **Settings** → **Secrets and variables** → **Actions** → delete the repository-level copies of those
     secrets.

From then on, pushing a tag runs the tests, then waits: open the run under **Actions** and click
**Review deployments** → tick `release` → **Approve and deploy**.

## 3. Version tags can't be moved or deleted

1. **Settings** → **Rules** → **Rulesets** → **New ruleset** → **New tag ruleset**.
2. Name: `release tags`. **Enforcement status**: Active.
3. **Bypass list**: leave empty.
4. **Target tags** → **Add target** → **Include by pattern** → `v*`.
5. Tick **Restrict updates** and **Restrict deletions**. (Leave **Restrict creations** unticked, so you can still
   create new version tags.)
6. **Create**.

## 4. Releases can't be changed after publishing

1. **Settings** → **General** → scroll to **Releases**.
2. Tick **Enable release immutability** (also called "Immutable releases").

The workflow already refuses to replace a release; this makes GitHub refuse it too, even for someone holding a
token.

## 5. Protect the main branch

For a one-person project, "require a pull request" would block your normal pushes, so only the destructive actions
are blocked:

1. **Settings** → **Rules** → **Rulesets** → **New ruleset** → **New branch ruleset**.
2. Name: `main`. **Enforcement status**: Active. **Target branches** → **Add target** → **Include default branch**.
3. Tick **Restrict deletions** and **Block force pushes**.
4. **Create**.

## 6. Secret scanning, push protection and Dependabot

1. **Settings** → **Advanced Security** (older name: **Code security and analysis**).
2. Turn on, each with its **Enable** button:
   * **Dependency graph** (the dependency-review workflow needs it);
   * **Dependabot alerts**;
   * **Dependabot security updates** (optional: opens fix pull requests by itself);
   * **Secret Protection** / **Secret scanning**;
   * **Push protection** (blocks a push that contains a password or key).
   * **Private vulnerability reporting** (lets people report a security problem privately).
3. **Code scanning**: leave **Default setup** off. The repo uses its own CodeQL workflow
   (`.github/workflows/codeql.yml`); turning on default setup as well makes them clash.

Dependabot's update pull requests come from `.github/dependabot.yml` (weekly, grouped). Merge them only when CI is
green.

## 7. What Actions may do

1. **Settings** → **Actions** → **General**.
2. **Actions permissions**: choose **Allow yneager, and select non-yneager, actions and reusable workflows**, then
   tick **Allow actions created by GitHub** and **Allow actions by Marketplace verified creators**. In the box
   below, add `gradle/actions/*` and `anchore/sbom-action@*` in case either isn't counted as verified.
3. Tick **Require actions to be pinned to a full-length commit SHA** (every workflow here already is).
4. **Fork pull request workflows from outside collaborators**: **Require approval for all external contributors**.
5. **Workflow permissions**: **Read repository contents and packages permissions**, and untick **Allow GitHub
   Actions to create and approve pull requests**. (The release job asks for write access itself, only in its
   publish step.)
6. **Save**.

## 8. Optional variable

Only if the signing key is ever replaced on purpose: **Settings** → **Secrets and variables** → **Actions** →
**Variables** → `CATMAP_RELEASE_CERT_SHA256` = the new certificate's SHA-256. Without it the workflow expects the
current certificate, `256df905…cdf42887`, read from a release-signed CatMap APK. A different key would also stop
every installed phone from updating, so this should never be needed.

## How to check it worked

* **Settings** → **Rules** → **Rulesets** shows `main` and `release tags` as Active.
* Push a commit: **Actions** shows CI, CodeQL (and Dependency review on pull requests) running.
* On release day, the run stops at "Waiting for review" until you approve the `release` environment, and the
  release afterwards shows an **Immutable** badge.
