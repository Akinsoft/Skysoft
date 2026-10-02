# Minecraft 26.3 source target

This port starts from Akinsoft/Skysoft commit
`79259743734d614a75f9d64bb8fa54163bfcfc1d` and retains `skysoft.version=0.1.30`.
It adds a genuine 26.3 build alongside 26.1 and 26.2. Native SDL input,
RenderPearl rendering, brewing recipes and moved player/HUD hooks retain the
existing feature paths, configuration formats and defaults.

## Build the SoftConfig dependency first

The 26.3 target consumes
`io.github.akinsoft.softconfig:modern-26.3:4.8.5` from the separately ported
Akinsoft/SoftConfig source at
`f83ff6aaefb09860775b9b05e5f2d1bdb930af50`. Follow that source's
`PORTING_26_3.md` to build and publish into a caller-selected local filesystem
Maven repository. Its common code requires a real JDK 8 toolchain, and its
26.3 platform requires JDK 25. Its retained 1.21.11 target additionally uses
JDK 21 when building the complete library matrix.

Run Skysoft's checked-in wrapper with JDK 25, then pass the selected repository:

```sh
./gradlew build --refresh-dependencies --no-daemon --max-workers=4 \
  -Pmoulconfig.repository="$SOFTCONFIG_PORT_REPO" \
  -Pkotlin.compiler.execution.strategy=in-process
```

`SOFTCONFIG_PORT_REPO` is the local repository prepared in the previous step.
The property accepts a URI or a path resolved relative to this repository;
an absolute local path makes separate checkout locations explicit. Existing
official repositories remain configured. The refresh is important after
replacing the same-version artifact inside a new private development
repository. Do not overwrite a published Maven release.

The full `build` validates all three supported targets, runs tests, detekt,
checkstyle and access-widener checks, and writes release JARs to `build/libs`.
This recipe builds production artifacts; it does not require `runClient`,
DevAuth or account credentials.

## Strict verification and bundled isolation

Dependency verification stays enabled. The selected original SoftConfig
26.3 JAR SHA-256 is
`2e1f24801068c472c91e24ebacc144cf544eb3bb9ba95893e8443bd088f2496e`.
Its native Escape handler repair is the only class change from the prior local
library artifact. Skysoft's verification metadata accepts that exact selected
coordinate/file checksum; other pins are unchanged. A different rebuild must
be reviewed and its genuine provenance established before deliberately changing
that checksum. Verification must not be disabled to consume an unknown JAR.

The library is still bundled under `skysoft_softconfig` and
`com.skysoft.deps.softconfig`, with its access widener and original namespace
isolation. The original TinyFD wrapper and all 11 supported native libraries,
along with their license notices, remain included. The original compatible
Hypixel Mod API Fabric adapter remains an external runtime dependency.

The validated Skysoft production JAR SHA-256 is
`498ee38d98e753ce936f7940a291e589cedd19bd12cc12f19b5588b88ca1f16b`.
Compared with the preceding validated JAR, only its nested SoftConfig JAR
changes; within that nested JAR only the relocated screen-handler class
changes. Other Skysoft entries are byte-identical.

## Validation scope

The all-target production build passed with strict dependency verification and
refreshed dependencies. Eight tests passed: required mixin application, all 279
native brewing records, keyboard/mouse compatibility and exact optional
Skyblocker integration method contracts. SoftConfig's four-target production
remaps and authored checkstyle passed; 24 existing cases report zero failures
and one original chroma skip.

The exact JAR passed isolated cold startup, full required mixin audit, actual
Mod Menu config GUI, native X11/SDL Escape from focused and unfocused pages,
Input Math toggle-and-restore, and fresh offline world rendering captured by
Minecraft's native screenshot function. The isolated helper reported zero
failures, shader compilation errors and mixin application errors. The existing
editor's internal focus context is separate from its screen wrapper, so focused
search closes on the first Escape; that common behavior remains unchanged.

The exact JAR also passed actual config GUI, native Escape and world rendering
in the corrected combined pack, including cold/post-world required mixin audits
with zero cumulative helper failures. Its final checkpoint recorded 12,779
frames in each shared Skyblocker and NoFrills GPU fixture; all 24 farm-key
assertions passed. The preceding combined run's unrelated unsupported operator
command remains documented in its original evidence.

These checks used native OpenGL software rendering. They do not establish
authenticated Hypixel gameplay, Vulkan/hardware GPU, other operating systems,
all optional integrations, native keybind-editor process-restart reload or
native file-dialog behavior. The compact config About page retains its prior
update-status/description overlap, and a copied resource pack emitted a
min/max-format metadata warning; those were documented rather than attributed
to this one-class input repair.
