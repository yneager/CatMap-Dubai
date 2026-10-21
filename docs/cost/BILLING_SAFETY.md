# Billing safety (Phase 9, 21 Oct 2026)

## Today: Spark, nothing can be billed

CatMap's Firebase project is on the free **Spark** plan. Spark has no payment method, so **no bill is possible**,
however the app is used or abused. The risk on Spark is an **outage**: when a daily quota runs out, that service stops
for everyone until it resets the next day, and when the 1 GiB of stored data is full, new writes fail
(`docs/cost/COST_MODEL.md`).

What protects the free quota today:
* Reads need a verified email (Phase 2); lists are paged and the map reads only the area on screen (Phases 4–5).
* Rules limit how fast and how big writes can be (`docs/security/ABUSE_LIMITS.md`).
* App Check reports which requests come from the real app (monitoring only, `docs/security/APP_CHECK.md`).

**What to watch (once a week is enough at today's size):** Firebase console → **Firestore** → **Usage** (reads,
writes, deletes a day) and the **Data** tab's "Stored data". Act at about 80% of a limit (40,000 reads a day, or
700 MB stored).

## If the project ever moves to Blaze

Blaze bills per use, with **no upper limit by default**. A bug (a listener in a loop) or an attacker can turn into a
real bill. Do these **before** switching, in this order.

### 1. Budget alerts (5 minutes)

1. Google Cloud console (console.cloud.google.com), choose the CatMap project → **Billing** → **Budgets & alerts**
   → **Create budget**.
2. Scope: this project. Amount: for example **$10 a month** (`COST_MODEL.md` puts 1,000 daily users at about $8).
3. Alert thresholds: 50%, 90%, 100%, and add **150%** and **200%** as "forecasted" too. Tick "Email alerts to billing
   admins and users".
4. Make a second, small budget (for example $2) with the same thresholds, as an early warning.

**Important: a budget alert does not stop spending.** It only sends an email, and the email can arrive hours after
the money is spent, because Google's cost reports lag. Spending continues until someone acts.

### 2. Hard stop (optional, more work)

Google's documented way to stop spending automatically is a Cloud Function, triggered by the budget's Pub/Sub topic,
that **disables billing** on the project when the budget is exceeded. Disabling billing stops every paid service
(the app then behaves like Spark at its limits, and anything that only works on Blaze stops). Guide: Google Cloud docs,
"Disable billing usage with notifications". This is worth doing only if the project grows beyond a demo; Claude can
prepare the function for the owner to deploy.

### 3. Limits that keep a bill small anyway

* Keep App Check monitoring on; if the console shows that almost all real traffic is verified, consider enforcing it
  (see `APP_CHECK.md` first: GitHub installs may not pass).
* Keep the rules' size caps and rate limits (they apply on Blaze too).
* Restrict the API keys (Google Cloud console → APIs & Services → Credentials): the Android key to package
  `com.catmap.dubai` + the release certificate SHA-256 (`256df905…cdf42887`, `GITHUB_SETTINGS.md`), the web key to
  `catmap-dubai.web.app` and `catmap-dubai.firebaseapp.com`.
* Put photos in Cloud Storage in **us-central1** (free amounts apply only there, `PHOTO_STORAGE.md`).

### 4. Emergency checklist (a surprise bill, or usage climbing fast)

1. **Look:** Google Cloud console → Billing → **Reports**, group by service and SKU. Firebase console → Firestore →
   Usage: which operation is growing (reads, writes, deletes, stored data, downloads)?
2. **Stop the bleeding**, quickest first:
   * a runaway feature or old app version: deploy rules that refuse it (Claude can prepare them in minutes);
   * an attacker's account: suspend it in the admin dashboard, and disable it in Firebase console →
     Authentication;
   * still climbing: Billing → **Account management** → **Disable billing** for the project (it falls back to Spark
     limits; nothing is deleted).
3. **Clean up:** delete junk documents (photos and posts of the abusive account; the admin tools can hide them, and
   Claude can prepare a one-off clean-up script run against the emulator first).
4. **Ask for a refund:** Google Cloud Billing support considers one-off charges caused by abuse or mistakes; open a
   billing case from the console with the dates and what was done.
5. Write down what happened and what changed in `HANDOFF.md`.
