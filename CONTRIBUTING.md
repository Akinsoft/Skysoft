# Contributing

Pull requests are welcome. For larger changes, please discuss the idea in the [official Discord server](https://discord.gg/akin) first.

## Pull Requests

- Use Java 25 and keep each pull request focused.
- Make new features and behavior optional and off by default.
- Use original or compatibly licensed code and assets.
- Explain what changed, why, and how you tested it. Include screenshots for visual changes.
- Please read and understand all of the code you submit.
- Keep AI attribution, such as `Co-authored-by` lines, out of commits. If you used AI, mention it in the pull request description instead.
- Do not change `skysoft.version` unless requested by a maintainer.
- Run the full build and fix any reported lint errors before submitting.

On Windows:

```powershell
.\gradlew.bat build
```

On Linux or macOS:

```bash
./gradlew build
```

The build validates every supported Minecraft version and runs detekt.

Bug reports and support are handled in the [official Discord server](https://discord.gg/akin).

By submitting a contribution, you agree that it may be distributed under the [GNU Lesser General Public License v3.0](LICENSE).
