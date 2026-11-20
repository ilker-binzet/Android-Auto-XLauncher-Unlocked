# Car App Hub — original car UI preview

An interactive, offline-capable design prototype for the Android Auto application
area. This is **not a new APK**, a live Android Auto connection, or a replacement
for the working projection implementation. It deliberately has no Android Auto
host navigation/media bar: the host owns that area.

Open `index.html` in a browser. If the browser restricts local files, serve this
directory with `python3 -m http.server 8540` and open it locally. No installation,
API key, CDN, telemetry or remote requests are required at runtime.

## Direction

Quiet instrument-panel typography and spacing. Graphite and amber for application
chrome; recognizable application icons provide their own identifying colors.
Individual apps are not enclosed in repetitive marketing cards. The main menu
has six items on ordinary landscape screens, three on short screens, four in a
narrow portrait review. The list is paged rather than squeezing the targets.

- Select an application to inspect the content layout.
- Menu returns to the same application; Escape also returns from content.
- The preview's Back action returns to the menu because it has no external app
  history. Android integration must send Back to the active app separately.
- Recent shows only applications opened during this preview session; its empty
  state is functional.
- Display opens Fit / Fill. The sample keeps a 16:9 ratio in both modes; Fill
  crops instead of distorting it.
- Finish sharing opens a confirmation. Cancel preserves the open view. Confirm
  shows the ended state; Restart returns to the menu. These are simulated states.
- Appearance offers dark, light and system themes. The preference is stored in
  the browser if storage is available; system preference changes update live.
- Tab, arrows, Enter, Space and Escape work. Native modal dialogs contain focus;
  closing and returning restore the expected focus.

The narrow side rail becomes a bottom strip on short head units. Touch targets
are at least 76 CSS pixels in the tested layouts. CSS pixels here define the
prototype geometry, **not verified Android dp or physical target sizes**. Actual
dp, density, safe areas, rotary focus and touch mapping require the host adapter.

## Deliberately illustrative data

The visible app list is a design fixture, not a scan of the user's phone. The
Güneş TV entry demonstrates a missing-icon fallback; it does not contain that
app's artwork. YouTube, Spotify, VLC and Firefox icons come from the CC0 Simple
Icons collection; trademarks remain their owners' property and are used only to
identify example entries. They are not part of Car App Hub's brand identity.
Space Grotesk is bundled under SIL OFL. Both license files are in `assets/`.

The small steering-wheel/app-bundle mark is a drawing for the preview. The
previously approved production SVG is absent from the current checkout and must
be restored from the working source archive before release. This prototype is
not approval to silently replace the existing launcher icon.

## Android integration boundary

Git and the GitHub API both currently return `development` at
`b60b7d52564e304b7c9f6e974a5b9c9c41577634`, with `versionName = "3.0.0"`.
The other branch and pull request contain the earlier libxposed migration.
No 3.1.1 tag/release or projection source is available in those remote refs.
The previously delivered 3.1.1 source ZIP and modified files are also absent from
the current workspace. The preview therefore stays separate from application
logic and version/signing configuration. No reduced-functionality APK is shipped.

Once the working source is available, implement the same design in its native
car host surface; embedding this browser preview is **not** the integration plan.
The integration must:

1. Read user-selected packages, names and icons from the phone's real catalog.
   Resolve/cache icons off the render thread and use a labeled fallback on failure.
2. Wire launch, app Back, menu, fit/fill and stop to the existing controllers.
   Simulated browser state must never be treated as proof of capture state.
3. Preserve session and driving-status restrictions, root leases, capture lifecycle
   and the existing installer hooks. An app menu redesign does not create isolated
   app capture or solve the reported video FPS problems.
4. Use runtime host dimensions, density and safe areas; apply the same content
   transform to input coordinates. Keep the shared screen separate from chrome.
5. Bind English/Turkish resources, semantic theme tokens, the approved logo and
   actual connection/error states. Configure the app catalog on the phone.
6. Validate in DHU and the user's real host; the browser checks below do not
   establish Android Auto compliance or device compatibility.

## Verification

`node check.cjs` uses Playwright and an installed Chromium. It serves this folder
temporarily on loopback and closes its server on completion. Set `CHROMIUM_PATH`
and `CAR_UI_PREVIEW_DIR` when needed. Playwright is a development-only tool; it is
not part of the delivered UI or an added Android dependency.

Checks cover layout bounds/targets at five sizes, short-screen controls, long
names, missing-icon example, paging, empty recents, spatial keyboard navigation,
focus restoration, fit/fill aspect ratio, modal cancellation, finish/restart and
persisted/system themes. PNGs and `checks.json` record the run. No FPS, phone
projection, physical touchscreen, DHU or root/Vector tests were run.
