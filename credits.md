# Credits

## License Scope

Unless a file states otherwise, Skysoft's original code is licensed under the GNU Lesser General Public License v3.0 only. The SkyHanni-adapted files listed below contain an `SPDX-License-Identifier: LGPL-2.1-only` notice and remain licensed under the [GNU Lesser General Public License v2.1 only](LICENSE-LGPL-2.1). This file-level exception does not relicense the rest of Skysoft.

## SkyHanni

Skysoft uses selected implementation ideas and adapted code from SkyHanni. SkyHanni is licensed under the [GNU Lesser General Public License v2.1 only](LICENSE-LGPL-2.1).

- Project: https://github.com/hannibal002/SkyHanni
- License: GNU Lesser General Public License v2.1 only

## SkyblockOverhaul Diana Command Compatibility

Thanks to Swift for giving Skysoft permission to follow SBO's Diana party-command style for compatibility.

- Project: https://github.com/SkyblockOverhaul/SBO

## Held Item Update Fix

Skysoft's original held-item update fix is inspired by the behavior of Hot Shirtless Men.

- Project: https://github.com/Rekteiru/Hot-Shirtless-Men
- Reference commit: `9162e02bf6f13477954e995e0c65b1737612ba30`
- License: MIT

## Diana Burrow Pitch Projection

Skysoft's Diana burrow path projection uses a pitch-weighting formula adapted from SkyHanni's `LocationUtils.computePitchWeight`.

Credit to Bloxigus for the original SkyHanni implementation:

- Project: https://github.com/hannibal002/SkyHanni
- Source: `at/hannibal2/skyhanni/utils/LocationUtils.kt`
- Commit: `74f7afcb7` (`Improvement: Change hoppity guess logic to be the same as the Diana burrow guess`)
- Skysoft file: `src/main/kotlin/com/skysoft/utils/particle/ParticlePathEstimator.kt`
- License: GNU Lesser General Public License v2.1 only

## Skill Experience and Overflow Calculations

Skysoft's skill XP storage fields and overflow-level calculation logic are adapted from SkyHanni's skill progress implementation.

Credit to HiZe, hannibal2, NopoTheGamer, and J10a1n15 for the original and updated SkyHanni implementations:

- Sources: `at/hannibal2/skyhanni/api/SkillApi.kt`, `at/hannibal2/skyhanni/features/skillprogress/SkillUtil.kt`
- Commits: `8dfbcf2a6` (`Feature: Skill progress display`), `2f2d5ffc3` (`Fix: Skill Overflow`), `4f83de935` (`Fix: Skill Overflow For Hunting And Foraging`)
- Skysoft file: `src/main/kotlin/com/skysoft/features/pets/SkillExpGainApi.kt`
- License: GNU Lesser General Public License v2.1 only

## Deferred Circle Rendering

Skysoft's deferred circle rendering, circle shaders, rounded vertex parameters, and picture-in-picture item blit path are adapted from SkyHanni's modern rendering work.

Credit to David Cole and Luna for the SkyHanni rendering implementations, with related work from the SkyHanni rendering contributors:

- Sources: `at/hannibal2/skyhanni/utils/render/SkyHanniVertexFormats.kt`, `at/hannibal2/skyhanni/utils/render/ShaderRenderUtils.kt`, `at/hannibal2/skyhanni/utils/render/SkyHanniRenderPipeline.kt`, `at/hannibal2/skyhanni/utils/render/states/SkyHanniCircleRenderState.kt`, `at/hannibal2/skyhanni/utils/render/states/AbstractSkyHanniRoundedShapeRenderState.kt`, `at/hannibal2/skyhanni/utils/render/item/SkyHanniRealtimeItemSlot.kt`, `assets/skyhanni/shaders/circle_deferred.vsh`, `assets/skyhanni/shaders/circle_deferred.fsh`
- Commits: `8fd6aee69` (`Backend: Deferred Rendering Pipelines`), `fdfb2423b` (`Backend + Improvement: Custom Modern Item Rendering`), `36814fc1d` (`Feature: Add 26.1 support`)
- Skysoft files:
  - `src/main/kotlin/com/skysoft/mixin/PictureInPictureRendererAccessor.kt`
  - `src/main/kotlin/com/skysoft/utils/render/item/ItemPictureBlitter.kt`
  - `src/main/kotlin/com/skysoft/utils/render/item/RotatingItemPicture.kt`
  - `src/main/kotlin/com/skysoft/utils/render/shader/RoundedRectShaderParams.kt`
  - `src/main/kotlin/com/skysoft/utils/render/shader/SkysoftCircleRenderState.kt`
  - `src/main/kotlin/com/skysoft/utils/render/shader/SkysoftCircleShaderRenderer.kt`
  - `src/main/resources/assets/skysoft/shaders/circle_deferred.fsh`
  - `src/main/resources/assets/skysoft/shaders/circle_deferred.vsh`
  - `src/target26_1/kotlin/com/skysoft/utils/render/SkysoftPipelineBuilder.kt`
  - `src/target26_1/kotlin/com/skysoft/utils/render/item/RotatingItemPictureRenderer.kt`
  - `src/target26_1/kotlin/com/skysoft/utils/render/item/SkysoftPipItemRenderers.kt`
  - `src/target26_1/kotlin/com/skysoft/utils/render/shader/SkysoftVertexFormats.kt`
  - `src/target26_2/kotlin/com/skysoft/utils/render/SkysoftPipelineBuilder.kt`
  - `src/target26_2/kotlin/com/skysoft/utils/render/item/RotatingItemPictureRenderer.kt`
  - `src/target26_2/kotlin/com/skysoft/utils/render/item/SkysoftPipItemRenderers.kt`
  - `src/target26_2/kotlin/com/skysoft/utils/render/shader/SkysoftVertexFormats.kt`
- License: GNU Lesser General Public License v2.1 only

## Input Click Hook Pattern

Skysoft's Minecraft input click hook structure is based on SkyHanni's click-cancelling hook and mixin pattern.

Credit to Bloxigus, NopoTheGamer, MTOnline, and Luna for the SkyHanni input hook work:

- Sources: `at/hannibal2/skyhanni/mixins/transformers/MixinMinecraftInputs.java`, `at/hannibal2/skyhanni/mixins/hooks/MinecraftInputHook.kt`
- Commit: `5c1948973` (`Backend: Move click cancelling from cancelling outbound packets to cancelling handling of the clicks`)
- Skysoft files:
  - `src/main/kotlin/com/skysoft/mixin/MinecraftInputMixin.kt`
  - `src/main/kotlin/com/skysoft/utils/input/InputEventInterceptor.kt`
- License: GNU Lesser General Public License v2.1 only
