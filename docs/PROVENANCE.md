# Source provenance

## Reviewed production source

The following seven Java files are copied from Hoord and retained without logic changes:

- `Enemy/PathFinder.java` — geometric detour selection.
- `Cannon/CannonExpression.java` — deterministic numeric expression evaluation.
- `Sounds/SoundEffectGenerator.java` — procedural audio synthesis and WAV output.
- `Sounds/SoundsDevPanel.java` — actual Swing sound-authoring interface.
- `hoordGame/BoundedCache.java` — bounded map utility.
- `hoordGame/ContentHash.java` — SHA-256 utility using the Java standard library.
- `hoordGame/ProcessLifecycle.java` — bounded process waiting and descendant shutdown.

All are under `src/main/java`. These files contain no AI model implementation or learned parameters. Arrays in audio synthesis hold generated PCM samples, not model tensors or weights.

`reference/Graphics/GAiQueue.java` is an additional unmodified production file that shows AI application orchestration. Its model-facing dependencies and prompt/data files are withheld, so it is a reading reference and is not compiled into the demo. It contains calls and parameter names, not learned parameters or actual prompts. The production logging strategy is discussed in the integration guide.

## Portfolio-only scaffolding

`src/demo/java` is new demonstration code. `Obstacle.Obstacle` supplies only the bounds/alive contract required by the real pathfinder. `hoordGame.ProjectPaths` supplies only the demo output directory required by the real Sounds panel. Neither substitutes for the full game's implementation outside this showcase.

`Showcase` supplies an eight-stage tab shell, synthetic navigation scene and formula evaluator. `ApprovalDemo` supplies a small asynchronous provider-boundary example with synthetic results and explicit approval. Other private stages are static descriptions. No demo loads private project directories, calls model services or claims synthetic output is AI-generated.

`src/test/java` contains isolated tests using synthetic inputs. No private fixtures are copied.

## Publication

The public history starts with this reviewed selection. A prior, broader local preparation draft was quarantined before publication and is not part of this repository or its Git objects. Updating the showcase requires a fresh review and explicit update to the per-file release manifest; bulk synchronization from the private project is not supported.
