# Qubeauth — Minecraft 26.3 / Fabric

Project name and mod id: **Qubeauth** / `qubeauth`.

Target: Minecraft Java 26.3, Fabric Loader 0.19.5, Java 25. Server-only; vanilla clients do not need the mod.

## Features intended
- Registration on first join and password prompt for returning players.
- Password chat attempts are consumed by a pre-broadcast chat event (must be verified on the exact Fabric API build).
- PBKDF2-HMAC-SHA256 password hashes with random per-account salts.
- Login holding position: Overworld `0 82 0`.
- Server-side movement/rotation correction, command blocking mixin, and damage prevention while unauthenticated.
- Restore saved dimension, coordinates and rotation on successful login.
- Orange prompt `#EC5800`, red incorrect-password title `#FF0000` for 3 seconds, success title for 2 seconds.
- Wrong-password feedback uses lightning sound; this starter does not yet implement a visible lightning animation.

## Important status
This is a **source starter, not a compiled or tested release JAR**. The current execution environment has Java 21 and no Gradle installed, while this target requires JDK 25. Minecraft 26.3 API/mapping signatures and Fabric API coordinates can change; the source must be compiled and tested against the exact 26.3 toolchain before installation. In server-only mode, a server cannot disable the client's physical mouse; it can only correct rotation server-side, which may appear as snapping.

## Build
With JDK 25 and a compatible Gradle installation, run `gradle build`. If the Loom/Fabric API versions in `gradle.properties` have changed, update them to the versions published for your exact 26.3 environment first. The intended artifact name is `build/libs/qubeauth-1.0.0.jar` after a successful build.

## Install after verified build
1. Stop the server and back up the world.
2. Put the built `qubeauth-1.0.0.jar` and matching Fabric API jar in the server's `mods` directory.
3. Start and test on a private test world before enabling on the live server.
4. Account hashes are intended to be saved under `config/qubeauth/users.json`.
