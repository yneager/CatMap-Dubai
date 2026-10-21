"""CatMap cost model: daily Firestore use for N daily active users (DAU), from the measured per-action numbers in
docs/cost/BASELINE.md ("After Phase 5"). Prints the tables used in docs/cost/COST_MODEL.md.

    python tools/cost/cost_model.py

Every assumption is a named constant below, so the model can be re-run with other guesses.
"""

# ---- Per daily active user, reads (measured costs x assumed behaviour) ----
FIRST_OPEN = 1 + 10 + 30 + 24 + 5      # profile, chat list, inbox window, map changes since last sync, own posts check
LATER_COLD_OPEN = 40                    # a second open more than 30 min later (inbox + chats + map changes)
EXPLORE = 2 * 25                        # two pages of Explore
OPEN_POSTS = 5 * (1 + 5)                # five posts, each with about five updates
SEARCH = 0.5 * 26                       # half the users search once
CHAT = 0.3 * 50                         # 30% open a chat (newest 50)
PHOTOS_UNCACHED = 15 + 3                # thumbnails and full photos not in the 30-day cache
NEW_USER_SHARE, FIRST_MAP = 0.05, 588   # 5% are new each day; first map with nothing saved (grown data)
INSTALLS_PER_DAU = 2                    # signed-in phones that didn't open the app today still check the inbox
BACKGROUND_PER_PHONE = 75               # ~50 runs a day under Android Doze x 1-3 reads (96-288 if never idle)

# ---- Per daily active user, writes ----
POSTERS, WRITES_PER_POST = 0.10, 8      # post, stats, 2 photos x 2 docs, AI job + answer (+1 delete counted below)
LOST_SHARE, FOLLOWERS = 0.01, 50        # lost reports alert followers of the area, one write each
CHATTERS, MESSAGES, WRITES_PER_MSG = 0.30, 5, 3   # message, chat preview, alert
READ_MARKS = 2                          # chat unread reset, alert marked read
OTHER_WRITES = 0.4                      # sightings, updates, check-ins, favourites

# ---- Per daily active user, stored data and downloads ----
PHOTO_KB, THUMB_KB, PHOTOS_PER_POST = 250, 25, 2
STORED_KB_PER_DAU = POSTERS * (PHOTOS_PER_POST * (PHOTO_KB + THUMB_KB) + 6) + CHATTERS * MESSAGES * 1 + 2
DOC_KB_PER_READ = 1.0

# ---- Fixed, whatever the number of users ----
PC_WORKER_READS = 2_000                 # Phase 3 estimate (two full window reads a day + changes)
PC_WORKER_WRITES = 288                  # heartbeat every 5 minutes

# ---- Spark free quota and Blaze prices (us-central1, Firestore Standard; checked 21 Oct 2026) ----
SPARK = dict(reads=50_000, writes=20_000, deletes=20_000, stored_gib=1.0, egress_gib_month=10.0)
PRICE = dict(reads=0.03 / 100_000, writes=0.09 / 100_000, deletes=0.01 / 100_000,
             gib_month=0.000205479 * 730, egress_gib=0.12)


def per_dau():
    writes = (POSTERS * WRITES_PER_POST + LOST_SHARE * FOLLOWERS + CHATTERS * MESSAGES * WRITES_PER_MSG
              + READ_MARKS + OTHER_WRITES)
    foreground = (FIRST_OPEN + LATER_COLD_OPEN + EXPLORE + OPEN_POSTS + SEARCH + CHAT + PHOTOS_UNCACHED
                  + NEW_USER_SHARE * FIRST_MAP)
    background = INSTALLS_PER_DAU * BACKGROUND_PER_PHONE
    reads = foreground + background + writes          # the rules' get() of users/{uid} on each write
    deletes = POSTERS * 1 + 0.5                       # AI job, dismissed alerts
    download_kb = (foreground - PHOTOS_UNCACHED) * DOC_KB_PER_READ + 15 * THUMB_KB + 3 * PHOTO_KB
    return dict(reads=reads, foreground=foreground, background=background, writes=writes, deletes=deletes,
                stored_kb=STORED_KB_PER_DAU, download_kb=download_kb)


def day(dau):
    p = per_dau()
    return dict(
        reads=dau * p["reads"] + PC_WORKER_READS,
        writes=dau * p["writes"] + PC_WORKER_WRITES,
        deletes=dau * p["deletes"],
        stored_mb_day=dau * p["stored_kb"] / 1024,
        egress_gib_month=dau * p["download_kb"] * 30 / 1024 / 1024,
    )


def blaze_month(dau, stored_gib):
    d = day(dau)
    reads = max(0, d["reads"] - SPARK["reads"]) * 30 * PRICE["reads"]
    writes = max(0, d["writes"] - SPARK["writes"]) * 30 * PRICE["writes"]
    deletes = max(0, d["deletes"] - SPARK["deletes"]) * 30 * PRICE["deletes"]
    storage = max(0, stored_gib - SPARK["stored_gib"]) * PRICE["gib_month"]
    egress = max(0, d["egress_gib_month"] - SPARK["egress_gib_month"]) * PRICE["egress_gib"]
    return reads + writes + deletes + storage + egress, dict(reads=reads, writes=writes, storage=storage, egress=egress)


if __name__ == "__main__":
    p = per_dau()
    print("Per daily user: reads %.0f (foreground %.0f, background %.0f), writes %.1f, stored %.0f KB/day, download %.0f KB/day"
          % (p["reads"], p["foreground"], p["background"], p["writes"], p["stored_kb"], p["download_kb"]))
    print("Daily users at which a Spark limit runs out:")
    print("  reads   %.0f" % ((SPARK["reads"] - PC_WORKER_READS) / p["reads"]))
    print("  writes  %.0f" % ((SPARK["writes"] - PC_WORKER_WRITES) / p["writes"]))
    print("  egress  %.0f" % (SPARK["egress_gib_month"] * 1024 * 1024 / 30 / p["download_kb"]))
    print()
    print("| Daily users | Reads/day | Writes/day | Downloads/month | Stored data growth | 1 GiB full after* | Spark? | Blaze/month** |")
    for dau in (100, 500, 1_000, 5_000, 10_000):
        d = day(dau)
        days_to_full = (1024 - 100) / d["stored_mb_day"]   # *starting from about 100 MB already used
        over = [k for k, v in (("reads", d["reads"] > SPARK["reads"]), ("writes", d["writes"] > SPARK["writes"]),
                                ("downloads", d["egress_gib_month"] > SPARK["egress_gib_month"])) if v]
        cost, parts = blaze_month(dau, stored_gib=max(1.0, d["stored_mb_day"] * 180 / 1024))
        print("| %s | %s | %s | %.1f GiB | %.0f MB/day | %s | %s | $%.0f |" % (
            f"{dau:,}", f"{d['reads']:,.0f}", f"{d['writes']:,.0f}", d["egress_gib_month"], d["stored_mb_day"],
            f"{days_to_full:,.0f} days" if days_to_full > 2 else "%.1f days" % days_to_full,
            "no: " + ", ".join(over) if over else "yes", cost))
        print("    parts:", {k: round(v, 2) for k, v in parts.items()})
