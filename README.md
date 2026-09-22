# Hoord · Engineering showcase

Selected engineering work from **Hoord**, a Java space game and its desktop development tools, by **Jordyn Wood**.

This repository exposes a reviewed set of real non-ML algorithms, AI integration code and a runnable presentation of the development workflow. Hoord's actual models and learned artifacts remain private.

## Explore the work

| Area | Included implementation | What to look for |
| --- | --- | --- |
| Enemy navigation | [PathFinder.java](src/main/java/Enemy/PathFinder.java) | Line-of-sight rejection, clustering nearby blockers, scored detours, bounded candidate search, fallback steering. |
| Weapon expression language | [CannonExpression.java](src/main/java/Cannon/CannonExpression.java) | Recursive-descent parsing, precedence, lazy conditionals, scoped sampling, distance fields and procedural noise. No learned parameters. |
| Sound authoring | [SoundsDevPanel.java](src/main/java/Sounds/SoundsDevPanel.java) and [SoundEffectGenerator.java](src/main/java/Sounds/SoundEffectGenerator.java) | The actual Swing editor and deterministic oscillator/noise synthesis, background work, playback cancellation, waveform preview and explicit WAV export. |
| Runtime utilities | [BoundedCache.java](src/main/java/hoordGame/BoundedCache.java), [ContentHash.java](src/main/java/hoordGame/ContentHash.java) | Entry-bounded caching and streaming content identities. These use standard cache/hash techniques. |
| Development pipeline | [Pipeline walkthrough](docs/PIPELINE.md) | All eight Dev Panel stages, their responsibilities, review boundaries and the handoff to gameplay. |
| Using AI safely in the workflow | [Production image queue](reference/Graphics/GAiQueue.java), [process lifecycle](src/main/java/hoordGame/ProcessLifecycle.java), [integration guide](docs/AI_INTEGRATION.md) | Request scheduling, lifecycle events, private-provider calls and human approval without shipping models. |
| Remote human review | [Private PC video connection](reference/remote-review/README.md) | Phone-to-PC video streaming over Tailscale, Google Sheets ratings, permanent output identities, full-duration evidence and archival before removal. Transport source only; no model or training implementation. |

## Run the showcase

Requires a JDK **17 or newer** and PowerShell. No network, account, API key, model download or private project installation is required.

```powershell
./tools/run.ps1 -Test
./tools/run.ps1
```

If Java is not on PATH:

```powershell
./tools/run.ps1 -JdkRoot '/path/to/jdk' -Test
./tools/run.ps1 -JdkRoot '/path/to/jdk'
```

The demo includes an interactive enemy-detour view, a weapon-expression evaluator and the real procedural Sounds panel. Click the pathfinding canvas to move its target. Sound export writes only beneath this checkout's ignored `.build/demo-output` folder. Playback retains the production editor's shared-audio-device policy; when no compatible output is present, WAV export still works.

The Approval tab demonstrates a provider boundary using an explicitly synthetic result and a human approval gate. Other private pipeline tabs are clearly labeled workflow descriptions. No demo performs AI inference or training. The navigation canvas and tab shell were created for this portfolio; they are not presented as the original full Dev Panel. [Source provenance](docs/PROVENANCE.md) distinguishes production code from demo scaffolding.

## Private boundary

AI application integration is included; the actual model implementation, training code, architecture, tensor, learned weight, checkpoint, trained adapter, dataset, prompt collection, feedback record and proprietary assets are excluded. No private Git history was imported. Each production file was reviewed individually; the public repository is not a copy of the private project.

Every publishable file is explicitly listed with its SHA-256 in `release-manifest.json`. `tools/verify-release.ps1` rejects unexpected files, changed reviewed content, links, disallowed types, credential patterns and private ML dependencies. It is a guardrail alongside code review, not a guarantee that arbitrary additions are safe.

Source is shared for portfolio inspection. See [LICENSE](LICENSE).
