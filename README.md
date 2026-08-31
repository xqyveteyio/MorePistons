# More Pistons for Fabric 1.20.1

This branch is a clean Fabric rewrite of More Pistons. The old Forge 1.8.8
project is preserved under [`tmp/legacy-1.8.8`](tmp/legacy-1.8.8).

## Features

- Long pistons with fixed extension lengths from 2 to 8 blocks.
- Long sticky pistons with fixed extension lengths from 2 to 8 blocks.
- Segment-by-segment extension and retraction.
- Vanilla piston structure resolution: 12-block push limit, slime/honey branching,
  push-reaction rules, moving-block animation, and entity collision handling.
- English and Simplified Chinese names, recipes, item models, and creative-tab entries.

The moving rules intentionally follow vanilla: blocks with block entities, such as
chests, are not movable.

## Recipes

- Combine a piston with an iron ingot to make a 2-block long piston.
- Combine the previous long-piston tier with an iron ingot to increase its length.
- Combine any long piston with a slime ball to make its sticky counterpart.

## Build

Minecraft 1.20.1 requires Java 17 or newer.

```bash
./gradlew build
./gradlew runGametest
```

The distributable JAR is written to `build/libs/morepistons-3.0.0-alpha.1.jar`.
Install it together with Fabric Loader and Fabric API for Minecraft 1.20.1.

## VS Code quick launch

Open this repository as the VS Code workspace, wait for the Java/Gradle import to
finish, and press **F5**. The default `Minecraft Client` configuration launches the
Fabric development client with Java 17. It compiles the mod first and explicitly
adds the project classes and resources to the launch classpath. The development
client also includes Mod Menu. `Minecraft Server` and `Game Test` are available
from the Run and Debug configuration picker.

## Current alpha limitations

- Each segment takes four game ticks; the base only plays one sound per complete
  extension or retraction.
- Automated GameTests cover a complete push/retract cycle for both normal and sticky
  long pistons. More multiplayer and unusual redstone timing tests are still needed
  before calling the release stable.
- Changing power while a segment is animating takes effect at the next segment boundary.
