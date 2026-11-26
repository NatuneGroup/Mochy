# Mochy 1:1 port handoff (2026-11-26)

Branch `feat/mochy-1to1`, PR #19 into `dev`. Reference app: github.com/realidkroo/mochy-music (GPL-3.0, credits kept).
Operator rules for this work: UI must match Mochy 1:1 on phone and be adapted for wide desktop windows; use parallel
Haiku subagents; no local Gradle builds, verify on CI (Blacksmith runners); `dev` is integration, `main` is Stable.

## State

PR #19 has not had a green CI run yet. Every round so far failed only on compile errors from code written without builds,
and each round had fewer: 80+, then 3, then 22, then 5. The last pushed fixes are in `66d50053` and later. Next step:
read the newest CI run on PR #19, fix the `e:` lines, push, and repeat until Android, Desktop and iOS are green. Then
fast-forward `dev` to the branch, which publishes the nightly APK.

Get the errors with:
`gh run view <id> -R NatuneGroup/Mochy --log-failed | grep ' e: '`

PRs #2 to #18 were closed because their changes were already on `dev`. PR #1 (`feat/mochy-multiplatform` into `main`)
is the Stable release, is identical to the old `dev`, and is left for the operator to merge.

## Landed on the branch (Mochy look only unless noted)

- Settings home: recommendations card with per-tip icons, Mochy category order and icons, collapsing blurred title.
- Wide settings: list and page side by side (list 38% of width, 300 to 400dp), placeholder, close button on pages.
- Sub-pages: Mochy cards and rows, bottom sheets for choices, 28dp Mochy slider, hide-descriptions setting
  (default on) with a long-press popup, 32dp row icons where the reference has them.
- Audio Lab: Lab kit, animated header, card stack, Reverb, 8D, Spatial and Stage cards, FX presets. Two columns on wide windows.
- Transition page (Off, Auto, Manual crossfade cards) and the animated transport-fade card. There is no CrossMix
  engine in KMP, so there is no CrossMix card.
- Profile page, ID card fixes, Discord preview, floating preview, profile card and toggle card.
- Home hero and shelves tuning, Search (pill, folding header, mood tiles, Mochy rows, query history), Library tiles,
  pills and collapsing header, album and playlist detail (photo backdrop, rows, pills), queue rows, grip and header.
- Player: Mochy overflow menu with icons, the Mochy style picker (long press on the cover, menu entry and a
  "Customize player" row), lyrics page wrapper, glow line, interlude dots, and Flagship landscape reflection, frost,
  lyrics band, retract, cover styles and disc variants.
- New `MiniPlayerType.MOCHY` (default for fresh settings) and an icon-only floating tab bar (all looks), wired on Android.
- Desktop: like, share and cast in the fullscreen Flagship, hover feedback on the transport, carousel cards and list rows.
- Onboarding: Enter and Esc keys, and the flow is capped at 430dp on wide windows.

## Known gaps

- KMP has no backdrop blur. The glass and frost effects are Compose-layer approximations built on `supportsNativeBlur()`.
- Not ported: AGSL lyric shaders (Android-only), the CrossMix engine and card, the share session, card-to-page morphs,
  the reference's real-render style previews, and the module styles and editor.
- Landscape glide distances may still be estimates. An agent was asked to use the reference values.
- None of this has been checked on a device. Compare phone screenshots with the reference before calling a screen matched.

## Working setup on the VPS

The worktrees are in `/home/ubuntu/work/wt/<area>`, the integration branch is in `wt/integration`, and the reference
checkout is in `/home/ubuntu/work/ref/mochy-music`. Agents follow `/home/ubuntu/work/AGENT-RULES.md`, which includes
the list of compile mistakes CI has already caught. Every agent appends its strings in its own block at the end of
`values/strings.xml`, so merges conflict there. Resolve them by keeping both sides; `/home/ubuntu/work/union.py` does this.
