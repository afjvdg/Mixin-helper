# Minecraft Mixin Helper

> ⚠️ This project was developed with the assistance of AI (Arena.ai Agent). Features are fully functional; see below.

**🌐 [阅读中文版 README](README.md)**

## About

**Minecraft Mixin Helper** is an Android tool for Minecraft mod developers. It downloads and parses the major mapping sets (Mojang / Fabric Yarn / Forge / NeoForge / MCP / Parchment), stores them locally offline, and provides millisecond-level real-time search over mapping names.

Supported mappings and how they are fetched:

| Loader | Mappings used | Notes |
| --- | --- | --- |
| Fabric | Yarn | Always uses Yarn (`meta.fabricmc.net`) |
| Forge (≥1.17) | Mojmap + Parchment | Official mojmap + Parchment (param names / Javadoc), bundled |
| Forge (<1.17) | MCP | joined.srg + MCP stable CSV from Forge Maven (incl. param names / Javadoc) |
| NeoForge | Mojmap + Parchment | Same as Forge ≥1.17 |

Core capabilities:

- Multi-source version list (Fabric / Forge / NeoForge), cross-checked against Mojang's official release list to drop previews and junk versions; sorted by version descending.
- Real download & parse: Yarn (Tiny v1/v2) / Mojang client_mappings / MCP (joined.srg + CSV) / Parchment (parchment.json).
- Offline storage (Room) with v1→v2→v3 database migration.
- **In-memory prefix autocomplete**: sorted array + binary search over readable names / obfuscated names / class names (incl. simple names and package paths, e.g. `client.`), millisecond-level; filterable by type / version / loader, with result truncation hints.
- Result detail: descriptor / params / param names / return type / Javadoc + one-tap copy.
- Background download + global download lock + live progress (bar / downloaded / total / speed).
- Persistent download status feedback and retry.

---

## Build

### Local build

```bash
chmod +x gradlew
./gradlew clean assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

> Note: `gradle-wrapper.jar` is a binary; the workflow downloads the official v8.5.0 via `curl`.

---

## Tech Stack

- Kotlin + Jetpack Compose (Material 3)
- Room + FTS4
- Hilt (DI)
- Ktor Client (OkHttp) + kotlinx.serialization
- Gradle 8.5

---

## Testing

- Unit tests: parsers (AsmDescriptor / Mojmap / Tiny v1&v2 / Parchment / MCP) + version comparison + in-memory search index.
- CI runs `testDebugUnitTest` on every build.

---

## Known behavior (not bugs)

- From MC 26.x Mojang stopped publishing `client_mappings`; downloading Mojmap clearly errors and suggests Fabric / Yarn instead.
- Parchment data is not available for every version; when missing, Forge / NeoForge gracefully fall back to plain Mojmap.
- Forge 1.16.x is listed as MCP, but MCP is officially published only up to 1.15; downloads automatically fall back to official mappings (and error if that version has no official mappings).

---

## Notes

- First build downloads the Android SDK (~5–15 min).
- Produces a Debug APK; enable "Install unknown apps" when installing.

---

## License

The source code of this project is licensed under the Apache License 2.0.

## Third-party Dependencies

· This project uses junit:junit:4.13.2 (for testing only) for unit testing, which is licensed under the
  Eclipse Public License 1.0 (EPL-1.0), https://www.eclipse.org/legal/epl-v10.html .
  The Apache-2.0 license of this project does not conflict with the EPL-1.0 test dependency.
