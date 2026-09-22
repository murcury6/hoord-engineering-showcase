# Hoord: locked, playable generated-asset demo

**LLM-generated game assets, made with proprietary Hoord game models of 8 million parameters or fewer each, using less than 8 GB of VRAM.** These generation figures are reported by creator Jordyn Wood; model internals and hardware telemetry are not published or independently benchmarked here. They describe the private generation setup, not this demo's runtime requirements. The 8-million limit is per model, not a claimed total across the pipeline.

The demo plays back a fixed selection of completed outputs from Hoord's development pipeline. It does **not** run an LLM, generate replacements, train a model or download weights. Existing rendering/shadow and enemy-tracking logic is adapted into a small standalone Java sandbox. This is a curated presentation, not the full game or full development panel.

[Download the approved demo](https://github.com/murcury6/hoord-engineering-showcase/releases/tag/v0.1.0-locked-demo).

## The demo in numbers

| Component | Fixed, inspectable quantity |
| --- | --- |
| Ships | **7 unique assets**: 1 player and 6 enemies |
| Saved ship bank poses | **65 per ship; 455 PNGs total** |
| Asteroids | **72 distinct saved appearances and approved collision polygons**, across 3 material groups |
| Saved directional shadows | **1,620 masks**: 864 asteroid masks (72 x 12 directions) and 756 ship masks (7 x 9 bank samples x 12 directions) |
| Background | **3 saved depth planes**, composited with the existing renderer; not three separate worlds |
| Exhaust | **7 saved gas profiles/textures**, one per ship asset |
| Presentation asset lock | **2,157 PNGs**, individually SHA-256 checked at startup; missing, modified or extra assets fail verification |
| Field dimensions | **2,000 x 1,400 world units** |
| Default window | **1,280 x 800 pixels** |
| Physics updates | **120 Hz**, with render interpolation between fixed steps |
| Enemy tracking updates | **45 Hz**, using original deterministic tracking/pathfinding logic, not neural inference |
| Shadow storage | **68,247,360 bytes (about 65.1 MiB)** of prepared mask pixels; all 1,620 masks loaded before play |
| Shaded image cache | **256 entries maximum**, with recycled buffers |
| Runtime AI inference / asset generation | **0** |

The fixed asset lock identifier is `79518972df1d`. Reset restores identical ship/rock assignments, starting placements, asteroid drift and spin. Saved silhouettes are distinct; shared material families are intentional. No rock geometry or textures are synthesized at runtime.

## Small combat sandbox

- Player health: **10 hits**. Enemy health: **3 hits**.
- Enemy kill: **+1 score** and **1 blue healing orb**; each collected orb restores **1 health**, capped at 10.
- **Player death resets the score to 0**. Nonfatal hits do not reset it. Manual scene reset also clears the score.
- Player fire interval: **0.5 seconds**; projectile speed **300 world units/second**.
- Enemy fire interval: **1.0 second per enemy**; projectile speed **200 world units/second**. Six enemies fire independently, not one shot per second across the field.
- Pellets: **2 x 2 screen pixels**. Enemy shots aim at the player's current position and travel straight, not homing.
- Enemy fire requires the production tracker's visibility flag, fresh clear line of sight, an on-screen enemy and distance below **850 world units**.
- Respawn delay: **1.5 seconds**, with **1 second** of protection after successful respawn. Blocked enemy spawns retry after **0.25 seconds**; opposite-side respawns are at least **700 world units** from the player.
- Enemy/enemy collisions are enabled; enemy bullets do not inflict friendly fire.

Controls: **Shift** forward thrust, **Ctrl** reverse, **W / S** fast / slow speed mode, **A / D** turn, mouse aim, **Space / left mouse** fire, **P** pause, **R** reset, **H** help, **Esc** exit.

## Measured performance, with scope

The final cache-regression test ran locally on Windows, Java 21, Java2D's D3D path and a **768 MiB maximum Java heap**. The heap is CPU memory, not VRAM. It repeatedly visited 15 map locations at half-second intervals while thrusting, turning and firing, for approximately **62 seconds**, with profiling enabled. After startup, displayed frame-rate readings were approximately **64–65 FPS**. This measurement preceded the small score-on-death rule change; it is not a new benchmark of every subsequent release change.

| AWT event-dispatch work | Measured duration |
| --- | ---: |
| 95th percentile | **7.11 ms** |
| 99th percentile | **9.29 ms** |
| Maximum | **36.50 ms** |
| Tasks over 50 ms | **0 out of 8,001 events** |

These timings measure UI-thread tasks, **not display presentation intervals, input latency or guaranteed minimum FPS**. Startup and the first half-second were excluded; other applications were not controlled. A shorter, approximately 21-second prior-cache test recorded a maximum task of **82.67 ms** and **4 tasks over 50 ms**. Different run lengths and desktop variability make this a local regression check, not a standardized cross-hardware benchmark or zero-lag promise.

The optimization moves PNG decoding and mask sampling out of gameplay, replaces a growing double-array cache with byte data plus a **256-entry lookup table**, and recycles shaded image storage. It does not remove shadows or alter the selected assets.

## Verification and privacy

- **72** distinct alpha silhouettes and **72** distinct saved polygons checked.
- **845** polygon vertices matched to the original image/hitbox placement transform.
- **864** exported asteroid shadow masks matched saved originals byte-for-byte.
- **864** rotated collision/projectile cases passed.
- **864** compact asteroid masks were sampling-identical; **272** ship/asteroid shaded frames were pixel-identical to the previous compositor.
- Cache bounds/reuse, interpolation, angle wrapping, pause/reset/respawn, controls, combat, visibility/range gating and a **2-minute simulated bounds test** passed.

The release contains a prebuilt demo runtime and explicitly selected finished presentation assets. The repository contains reviewed engineering examples. **No proprietary model architecture, weights, tensors, checkpoints, adapters, training code, datasets, private prompts, API keys or full private-game runtime are included.** Selected PNG outputs and saved collision outlines are the deliberate exception to keeping the larger proprietary asset collection private. Runtime classes implement presentation/game logic, not the generating models.

The ZIP includes a startup-verified asset manifest; the release has a separate ZIP SHA-256 checksum. The repository's `release-manifest.json` covers repository files, not the downloadable ZIP. Pattern checks supplement rather than replace review.

## Run

Download `Hoord-Locked-Demo-v0.1.0.zip`, extract the entire folder, and run `Launch Demo.bat` on Windows with **Java 17 or newer** on PATH. Tested with Java 21. Do not run only the JAR inside the ZIP: its adjacent `assets` folder is required.

Alternatively, from the extracted folder:

```text
java -Xmx768m -jar Hoord-Locked-Demo.jar
```

Optional packaged checks (writes verification screenshots to the given directory):

```text
java -Djava.awt.headless=true -Xmx768m -jar Hoord-Locked-Demo.jar --self-test verification-output
```

No login, network connection, API key, private installation or model download is required to play. This is a prebuilt presentation release, not a claim that the entire private game can be rebuilt from this repository. Copyright remains with Jordyn Wood; see LICENSE. No open-source license is granted.
