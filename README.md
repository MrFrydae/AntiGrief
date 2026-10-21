# AntiGrief

A lightweight PaperMC plugin that prevents Endermen from picking up player-placed blocks while allowing them to pick up naturally generated blocks.

AntiGrief works automatically for all players, game modes, and worlds. No commands, permissions, configuration, or database setup are required.

**Protection starts when the plugin is enabled.** Blocks placed before installation cannot be identified retroactively; break and replace them to protect them.

## Features

- Prevents Endermen from stealing successfully placed player blocks.
- Preserves protection across chunk unloads and normal server restarts.
- Tracks blocks moved by pistons, sticky pistons, and gravity, including sand and gravel.
- Clears protection when blocks are broken or destroyed, and retains it through transformations such as grass becoming dirt.
- Respects cancelled placement, break, and movement events from other plugins.
- Leaves the `mobGriefing` game rule and other mobs' behavior unchanged.

## Requirements

- **Server:** PaperMC 26.3 running Java 25 or newer.
- **Building from source:** JDK 25 and an internet connection for the first build.

Folia is not supported. Compatibility with other server implementations or Minecraft versions has not been verified.

## Build

From the project directory, use the included Gradle wrapper:

```sh
./gradlew clean build
```

On Windows:

```powershell
.\gradlew.bat clean build
```

The wrapper downloads Gradle automatically; a global Gradle installation is not needed. Set `JAVA_HOME` to your JDK 25 installation if the wrapper cannot find it.

The build compiles against the pinned Paper API `26.3.build.211-stable`, runs the automated tests, and creates:

```text
build/libs/AntiGrief-1.0.0.jar
```

## Installation

1. Build the plugin using the instructions above.
2. Stop your Paper 26.3 server.
3. Copy `AntiGrief-1.0.0.jar` into the server's `plugins/` directory.
4. Start the server and confirm this message appears in the console:

   ```text
   [AntiGrief] Enderman protection enabled for newly placed player blocks.
   ```

Player placements are now tracked automatically. When updating, stop the server and replace the old AntiGrief JAR so only one version is installed.

## Storage and backups

AntiGrief stores a compact bitset in each chunk's persistent data container. Protection is saved alongside the world through normal chunk saves, with no separate database or periodic scan of the world.

Back up your world files to back up protection data. Uninstalling the plugin disables protection; it does not actively remove stored chunk data. Placements and edits made while the plugin is disabled are not tracked.

## Limitations

- **Existing builds:** Minecraft does not record block placement history, so blocks placed before AntiGrief was enabled are not automatically protected.
- **World editing:** `/setblock`, `/fill`, structure placement, and tools or plugins that bypass normal block events are not tracked. Such edits can leave protection attached to an old location until a normal gameplay event updates it.
- **Derived blocks:** Protection follows tracked blocks; it does not automatically apply to every block produced by growth, spreading, or generation.
- **Server saves:** Protection follows the world's normal saving behavior. An abrupt shutdown can lose unsaved world changes and their protection data.

## Testing

Run the automated tests separately with:

```sh
./gradlew test
```

On Windows, use `.\gradlew.bat test`. The HTML report is written to `build/reports/tests/test/index.html`.

The tests cover pickup decisions, persistent-data reloads, cancelled events, multiple-block placements, piston movement, falling blocks, explosions, and destruction. They use isolated Paper API fixtures; they do not replace testing on a live server.

For an in-game check with `mobGriefing` enabled:

1. Place dirt near Endermen and compare it with naturally generated dirt. Player-placed dirt should remain; natural dirt should still be eligible for pickup.
2. Repeat after unloading and reloading the chunk, and after restarting the server.
3. Push and pull protected blocks with pistons, and let player-placed sand fall. Check protection at the new positions.
4. Break a protected block and let a natural block occupy its former position. The old position should no longer confer protection.
5. If using another protection plugin, verify that cancelled placements, breaks, and piston moves leave tracking unchanged.

## Contributing

Keep changes focused and run `./gradlew clean build` before submitting them. Include a regression test for behavior changes where practical.

When reporting a problem, include the Paper build, Java version, AntiGrief version, reproduction steps, relevant console output, and any plugins involved in block placement or protection.

## References

- [Paper project setup](https://docs.papermc.io/paper/dev/project-setup/)
- [Entity block change events](https://jd.papermc.io/paper/26.3/org/bukkit/event/entity/EntityChangeBlockEvent.html)
- [Chunk API and persistent data](https://jd.papermc.io/paper/26.3/org/bukkit/Chunk.html)

## License

Released under the [MIT License](LICENSE). Copyright © 2026 MrFrydae.
