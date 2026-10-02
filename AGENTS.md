# 三视图 (ThreeViews): project guidance

Engineering-drawing teaching app for PICO OS 6 (Project Swan), PICO Spatial SDK 6.1.9.
Package `io.embodit.threeviews`, version 1.0.0. Shared Space, three surfaces, bilingual UI
(Chinese first, English second, both always shown):

- **Projection box** (default container `ProjectionBox`, Volumetric, 0.8 × 0.72 × 0.9 m, system-managed
  scale, no base panel, opens in front of the user): the part between its three projection planes
  V, H, W, the projection rays, and a window-level `Toolbar` with ten controls.
- **Part library** (`WindowContainer("PartLibrary")`, Planar, 360 × 720 dp, 320 × 480 to 480 × 960,
  placed left of the box, opened at launch): the eight built-in parts.
- **Drawing sheet** (`WindowContainer("DrawingSheet")`, Planar, 960 × 720 dp, 720 × 540 to 1600 × 1200,
  placed right of the box, opened on demand or when a drawing fails): the GB first-angle sheet with
  its own one-switch toolbar (alignment guides).

GB first-angle projection: front view on V behind the part, top view on H below it, left view on W
to its right. Unfold swings H down about OX and W right about OZ; the flat box equals the sheet.
The part then settles in the empty fourth quadrant as a pictorial view.

## Rules that stay in force

- All 2D UI is SpatialUI (`com.pico.spatial.ui.*`) inside `ThreeViewsTheme` (a `PicoTheme` with all
  16 `ColorScheme` roles assigned). No Material or Material3.
- The design package in `design/` (`design-doc.md`, `preview.html`) is the accepted design (two
  rounds). Change the design there first, then the code.
- Brand colors live in `ui/theme/ThreeViewsColors.kt`; ink and paper colors on the sheet use `.fixed()`.
- Per-frame 3D motion belongs in `BoxAnimationSystem` / `BoxSceneController.onFrame`, not in Compose.
- Every label is bilingual in one string file (`values/strings.xml`); there is no `values-en`.
  Buttons, toggles and segments use `BilingualLabel` (Chinese `labelMedium` above, English
  `bodyTiny` below). Lists, name tags, view names and the title block put English after Chinese.
- Custom clickables carry `spatialHoverEffect`, `LocalIndication` and `controllerHapticFeedback`;
  every `Text` passes a color or is marked `// design-style: inherited-content-color <Provider>`.

## Layout of the code

| Path | What it holds |
| --- | --- |
| `domain/geometry/` | Pure Kotlin, millimetres. CSG solids, the part library, mesh, exact hidden-line projection (`computeDrawing`). |
| `domain/model/` | `SceneState` (shared by all surfaces), `SheetLayout`, `BoxLayout`, `BoxSceneSpec`, `BoxPose`, `BoxFit`, `PartSummary`. |
| `domain/usecase/` | `PrepareDrawingUseCase`, `SheetLayoutUseCase` (view arrangement, axes, alignment guides), `BoxLayoutUseCase` (planes, rays), `BoxSceneUseCase` (ribbon geometry of the box), `BoxPoseUseCase` (unfold motion and per-pose fit), `LineDashUseCase`, `SceneControlUseCase` (what every gesture means, window flags). |
| `data/repository/` | `PartRepository`, `BuiltInPartRepository` (shared computation, cache, background warm-up, bilingual notes), `FakePartRepository`. |
| `data/session/` | `SceneSessionStore`: the one process-wide `StateFlow<SceneState>`. |
| `platform/` | `SpatialApplication`, `LaunchActivity`, `ThreeViewsGraph` (object graph). Debug builds add `DebugCommandReceiver`. |
| `ui/navigation/` | `Containers`: ids and tags of the three containers. |
| `ui/components/` | `BilingualLabel`. |
| `ui/library/` | `LibraryScreen` / `LibraryViewModel` / `LibraryUiState`; component `PartLibrary` (SideNavigation rows). |
| `ui/sheet/` | `SheetScreen` / `SheetViewModel` / `SheetUiState`; components `DrawingSheet`, `TitleBlock`, `SheetToolbar`. |
| `ui/box/` | `BoxScreen` / `BoxViewModel` / `BoxUiState`; `components/ProjectionBoxView` (SpatialView, gestures, name tags), `components/BoxToolbar`; `scene/` (`BoxSceneController`, `BoxMeshes`, `BoxResources`, `BoxAnimationSystem`). |
| `Main.kt` | The three containers and their placement, nothing else. |

Coordinates: part space X length (right), Y up, Z toward the viewer. FRONT u=x, v=y; TOP u=x, v=−z;
LEFT u=z, v=y. Sheet millimetres have y running down, origin at the front view's top-left corner.

Scene hierarchy: `SpatialView → placementOwner (identity) → fittedSceneRoot (the uniform fit) → tilt →
turn → content (part space, mm)`. The fit is computed per unfold value in `BoxPoseUseCase.fit`, so
the folded box fills the volume and the flat sheet still fits.

## SDK facts learned the hard way

- Creating a mesh, material or collision shape waits for the next rendered frame. Create them off
  the main thread (`BoxMeshes.create`, `BoxResources()` run on `Dispatchers.Default`), assemble
  entities on the main thread.
- A resource is released with the last entity that uses it. Anything reused across entities or
  created before its entity must call `toGlobal()` and later `close()`.
- An attachment panel added under a disabled entity never renders. Name tags are placed again
  after `fittedSceneRoot` is enabled. Their natural size is about 0.25 mm per pixel; their scale is
  set in part-space units and is not compensated by the fit.
- `detectSpatialDragGesture` reports `dragAmount` per event in the volume's own axes, about 2500
  units per metre. The turn uses `hypot(x, z) * sign(x) * 0.07°`.
- The system keeps a volume at a fixed angular size, so a bigger declared volume is placed farther
  away. Fill the volume instead of enlarging it.
- `Toolbar` works under a Volumetric default container. `Placement.left/right(anchor)` positions
  the two Planar windows beside it.
- `SegmentControl` has no `enabled` parameter; give it a fixed width or it fills the toolbar.
  `ToggleButton` / `Button` with a custom size need an explicit `RoundedCornerShape`.
- More in `.scratch/spike_findings.md`.

## Build, test, run

```bash
JAVA_HOME='C:\Users\Admin\.pico\primer-cli\jdk\jdk-21.0.12.1+1' PICO_HOME='C:\Users\Admin\AppData\Local\PICO\sdk' \
  ./gradlew.bat assembleDebug :app:testDebugUnitTest --no-daemon --console=plain
python tools/count_tests.py
```

This project owns emulator `emulator-5558` (AVD `PICO_6.1_Views`). Pin every adb, Gradle install and
pico-cli call to it (`ANDROID_SERIAL=emulator-5558`); other sessions use 5554 and 5556.

```bash
adb -s emulator-5558 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5558 shell am start -n io.embodit.threeviews/.platform.LaunchActivity
```

Harness (posts input to the emulator window only): `bash tools/harness/tv.sh shot NAME | click X Y |
drag X1 Y1 X2 Y2 [-Button right] | cmd NAME [arg] | fast on`. Right-button drag turns the camera.
Take a screenshot twice, the first frame can be stale. `bash tools/record_demo.sh` records the
44 s demo video with the debug commands below.

Debug commands (`tv.sh cmd …`, debug builds): `select <partId>`, `focus <ALL|FRONT|TOP|LEFT>`,
`unfold`, `fold`, `rays|hidden|guides <on|off>`, `reset`, `retry`, `yaw <deg>`, `evidence` (logs the
planned and remeasured bounds of the box under tag `ThreeViews`).

Part ids: `l_block`, `step_block`, `wedge_cut`, `plate_hole`, `v_block`, `slot_block`,
`bearing_seat`, `notched_tube`. `tools/render_dump.py` renders all drawings to a PNG from the
dump written by `DrawingDumpTest`.

Verification gate (python3 shim on PATH, see the skill's notes):

```bash
ANDROID_SERIAL=emulator-5558 bash "C:/Users/Admin/.claude/plugins/cache/pico-xr/pico-spatial-agentic-tools/0.5.0/skills/spatial-design-to-app/scripts/validate_workflow_and_build.sh" \
  "C:/Users/Admin/Downloads/threeviews" --input-mode intent_only --generation-mode new_project --visual-asset false \
  --design-color interaction=#2B5FD9 --design-color error=#C62828 --design-color alert=#E08A00 --design-color passable=#2E8B57 --design-color labelPrimaryLight=#FFFFFF \
  --design-color paper=#F6F3EA --design-color paperEdge=#D9D3C3 --design-color ink=#16181C --design-color inkSoft=#5B616B --design-color centerLine=#C4372B --design-color rayBlue=#2B5FD9 \
  --design-color planeV=#DCE6F7 --design-color planeH=#F4ECD2 --design-color planeW=#DCEFE2 --design-color partBody=#B9C3CE
```

## Showcase material

`docs/media/` holds the demo video (`ThreeViews-demo.mp4`), the two showcase crops (`shot1.jpg`
folded, `shot2.jpg` unfolded) and the two window shots. `docs/PRD.md` is the product brief.
Figma: https://www.figma.com/design/RclDoEx4Txn4LjQntiUlVM (boards 01 definition, 02 layout,
03 key screens, 04 components and tokens).

## Not verified yet

- Nothing has run on a headset. Physical size of the box and its name tags, comfort, hand
  interaction and performance are emulator observations only.
- On the emulator the first drawing of a part takes several seconds (the emulator translates ARM
  code); the library is warmed up in the background after launch.

## Natural next steps

1. Dimensions on the sheet (尺寸标注).
2. Exercise mode: draw or pick the missing third view, checked by the projection itself (direction
   C in the design doc).
3. Import a model (STL / glTF) and project it with the same pipeline.
