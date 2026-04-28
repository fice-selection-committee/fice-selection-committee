# STEP-00 — Repository Cleanup

**Status**: ✅ DONE
**Depends on**: nothing
**Blocks**: nothing (cosmetic)

## Goal

Remove ~123 untracked dev artifacts at the repo root left over from a prior NerdWallet competitive-analysis exercise. They pollute `git status`, slow down editor indexing, and confuse new contributors.

## Scope (executed)

Deleted file patterns at repo root only (depth-1, never recursed into tracked folders):

| Pattern | Count |
|---|---|
| `nerdwallet-*.png`, `*.md`, `*.yml` | ~80 |
| `nw-*.png`, `*.md`, `*.yml` | ~32 |
| `desktop-cc-*.png`, `desktop-home-*.png` | 6 |
| `tablet-*.png` | 13 |
| `final-tablet-*.png` | 4 |
| **Total** | **~123** |

## Kept Intentionally

- `docs/claude/prompts/*.md` — historical prompt knowledge base, possibly still referenced
- `client/web/public/frog-logo.png` — production app asset
- `server/libs/sc-common/.../Telegram*Exception.java` (untracked) — real new code, not junk
- `infra/grafana/dashboards/telegram.json` (untracked) — real new dashboard
- `docs/RELEASE_v1.2.0_UA.{html,pdf}` — release notes
- `drawn-frog-logo.png` left as `D` in git status — user-facing decision whether to stage the deletion in next commit

## Tests (Acceptance Gates) — all green

- [x] `git status --porcelain | grep -E '^\?\? (nerdwallet|nw-|desktop-cc|desktop-home|tablet-|final-tablet)'` returns 0 matches
- [x] No file under `docs/`, `server/`, `client/`, `infra/` was touched (verified by inspecting `git status --porcelain` after deletion — same set of tracked-file modifications as before)
- [x] No tracked file was deleted (only untracked artifacts removed)

## Verification (post-step)

```bash
git status --porcelain | wc -l   # was 155+, now ~32
ls -1 nerdwallet-* nw-* desktop-cc-* tablet-* 2>&1 | head -1   # "No such file or directory"
```

## Definition of Done

- [x] All 123 junk files deleted
- [x] `git status` clean of junk patterns
- [x] No legitimate file touched
- [x] STEP-00 row in `progress/README.md` marked ✅
