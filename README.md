<div align="center">

# Skysoft

A modern Fabric mod with quality-of-life features for Hypixel SkyBlock.

[![Download on Modrinth](https://img.shields.io/badge/Download-Modrinth-00AF5C?logo=modrinth&logoColor=white)](https://modrinth.com/mod/skysoft)
[![Discord](https://img.shields.io/discord/384340624524050432?label=Discord&logo=discord&logoColor=white&color=5865f2)](https://discord.gg/akin)
[![License](https://img.shields.io/badge/License-LGPL--3.0-blue)](LICENSE)

</div>

Skysoft improves everyday SkyBlock gameplay with inventory tools, market tracking, interface customization, and other focused utilities. It draws inspiration from projects such as [SkyHanni](https://modrinth.com/mod/skyhanni) and [Firmament](https://modrinth.com/mod/firmament).

## Main Features

### Item List

Browse SkyBlock items, recipes, usages, obtain sources, and market data directly from inventory screens. Search the catalog, explore Crafting, Forge, and Kat recipes, inspect Bazaar history and order depth, and create waypoints to relevant NPCs.

<p align="center">
  <img src="docs/images/item-list/catalog.png" alt="Skysoft Item List catalog browser" width="35%">
  <img src="docs/images/item-list/bazaar-history.png" alt="Skysoft Item List Bazaar price history" width="60%">
</p>
<p align="center">
  <img src="docs/images/item-list/npc-waypoint.png" alt="Skysoft Item List NPC obtain source and waypoint" width="70%">
</p>

### Bazaar Tracker

Follow Bazaar order progress in real time without repeatedly opening the Bazaar. Filling estimates approximate how much of each order has completed, while Flipping Mode tracks total or per-session profit.

![Screenshot of Bazaar Tracker](https://cdn.modrinth.com/data/cached_images/fddeef355a04ca9cd68dd09880b9024b13e99c84.png)

### Inventory Buttons

Create fully customizable buttons for quick access to your favorite menus and commands.

<p align="center">
  <img src="https://cdn.modrinth.com/data/cached_images/eb7dfdd06d00a2741ec35f465b0413a3b1ed53c1_0.webp" alt="Main Inventory" width="32%">
  <img src="https://cdn.modrinth.com/data/cached_images/d00d850e9a70b0a3ebdd4c5c76b2d166612132e6.png" alt="Editing Mode" width="32%">
  <img src="https://cdn.modrinth.com/data/cached_images/b33bdafa22bc2a1f630542c489d1a4528bf6bdf3.png" alt="Position Editor" width="32%">
</p>

### Storage Overlay

Browse and manage all of your SkyBlock storage pages from one interface.

![Screenshot of Storage Overlay](https://cdn.modrinth.com/data/cached_images/08b3725738dd9643c6c1245a3e63ba7cbae8f167.png)

## More Features

<details>
<summary>Preserve Cursor Position</summary>

Keeps the mouse in place while moving between SkyBlock inventories and menus.

![Setting screenshot for Preserve Cursor Position](https://cdn.modrinth.com/data/cached_images/e4806f4742c284856881986059dc62d4703bdab7.png)

</details>

<details>
<summary>Customizable Full Inventory Warning</summary>

Warns when your inventory reaches a configurable fullness threshold.

![Full Inventory Warning screenshot](https://cdn.modrinth.com/data/cached_images/885f19188dbe0cda9777a724b589a52fc203fa24.png)

</details>

<details>
<summary>Slot Binding</summary>

Binds two inventory slots so their held items can be swapped with a shift-click.

![Slot Binding example](https://cdn.modrinth.com/data/cached_images/2e5a29112d26a278a553959535b2b5b29a9acec7.gif)

</details>

<details>
<summary>Auto-Sprint</summary>

Automatically keeps the player sprinting without holding a key.

![Auto-Sprint screenshot](https://cdn.modrinth.com/data/cached_images/9a3ed5e3229380e7611f467641964a1961966d13.png)

</details>

<details>
<summary>Action Bar Background</summary>

Draws a configurable background behind the action bar.

![Action Bar Background screenshot](https://cdn.modrinth.com/data/cached_images/46d29ba7467dc960498ca370ecb9abb3190737c4.png)

</details>

<details>
<summary>Lotum Helper</summary>

Draws a line to clicked Lotums.

![Lotum Helper screenshot](https://cdn.modrinth.com/data/cached_images/6480705fc4c2996959daea15d4da7e47b117d153_0.webp)

</details>

<details>
<summary>Separate Inventory and Tooltip GUI Scale</summary>

Uses independent sizes for inventories and tooltips without changing the rest of the GUI.

![Separate Inventory and Tooltip GUI Scale screenshot](https://cdn.modrinth.com/data/cached_images/536ffa413cd27a2cb9813be298e39f4a51e39f2b.png)

</details>

<details>
<summary>Smooth Chat</summary>

Animates new messages and the chat screen opening.

![Smooth Chat example](https://cdn.modrinth.com/data/cached_images/fe8c96c803146b4aa500a7af6e51ae781d2f9c90.gif)

</details>

## Installation

Skysoft supports Minecraft 26.1, 26.2 and 26.3 and requires [Java 25](https://adoptium.net/temurin/releases/?version=25).

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for a supported Minecraft version.
2. Install [Fabric API](https://modrinth.com/mod/fabric-api), [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin), and the [Hypixel Mod API](https://modrinth.com/mod/hypixel-mod-api).
3. Download Skysoft from [Modrinth](https://modrinth.com/mod/skysoft).
4. Place the mods in your Minecraft `mods` folder and launch the game.

Use `/ss` or `/skysoft` to open the configuration screen. [Mod Menu](https://modrinth.com/mod/modmenu) is supported but optional.

## Support and Bug Reports

Support and bug reports are handled in the [official Discord server](https://discord.gg/akin).

## Building

Build every supported Minecraft version and run all checks:

```powershell
.\gradlew.bat build
```

```bash
./gradlew build
```

The distributable jars are written to `build/libs`.

The 26.3 target consumes a separately built SoftConfig platform. See [PORTING_26_3.md](PORTING_26_3.md) for the caller-selected local repository, strict dependency verification and validation scope.

See [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request.

## License

Except for specifically marked files, Skysoft's original code is licensed under the [GNU Lesser General Public License v3.0 only](LICENSE), with its incorporated [GNU General Public License v3.0 terms](LICENSE-GPL-3.0). Files adapted from SkyHanni contain an `SPDX-License-Identifier: LGPL-2.1-only` notice and remain under the [GNU Lesser General Public License v2.1 only](LICENSE-LGPL-2.1). Exact file paths and attribution are listed in [credits.md](credits.md).

Third-party software retains its own license terms; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Skysoft is not an official Minecraft service and is not approved by or associated with Mojang, Microsoft, or Hypixel.
