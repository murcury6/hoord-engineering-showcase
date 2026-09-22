# The development pipeline

Hoord's desktop development tools organize content work into eight main stages. The public shell preserves that top-level organization while keeping the AI systems and content collections private.

| Dev Panel stage | Responsibility in the private project | Public representation |
| --- | --- | --- |
| 1 · AI Approval | Review candidate assets and make explicit human approval decisions. | Production queue reference plus a synthetic provider/approval demo. No models, prompts, ratings or training implementation. |
| 2 · Asset + Rounded 3D | Inspect and finish ship, background, asteroid, effect and exhaust assets. | Workflow description only. No model, geometry-learning code or private assets. |
| 3 · Pre-Weapon | Preview the scene and gameplay environment before weapon integration. | Separate interactive navigation demonstration with synthetic rectangles. |
| 4 · Finishing | Flight checks, animation review and upgrade presentation. | Workflow description only. |
| 5 · Weapons | Author, evaluate and inspect weapon behavior and presentation. | The actual numeric expression evaluator with a synthetic formula. The compiler, full weapon engine, corpus and AI are withheld. |
| 6 · Music | Compose, audition, review and prepare music. | Workflow description only. No composer, inference adapter, recordings or score collection. |
| 7 · Sounds | Author and audition procedural effects; explicitly export a WAV. | Production Swing panel and deterministic synthesis code, connected to a demo-local output path. |
| 8 · Weapon AI | Manage private weapon-generation and review workflows. | Private-stage notice only. No implementation or learned artifacts. |

The key application boundary is **authoring → review → explicit promotion → gameplay**. Development candidates remain separate from approved game content. Build outputs and writable session state are separate from durable project content. This page documents the responsibilities, not the private build machinery, deployment settings or model internals.

## Enemy navigation

The included `PathFinder` is a geometric steering helper, not a trained model or a global A* search. It first checks the direct segment against live obstacle bounds. When blocked, it groups nearby obstacles that also obstruct the direct path, expands their merged bounds, considers six detour candidates and prefers the shortest candidate with clear segments both to and from the waypoint. If no complete detour is available, it tries forward progress and a side-step fallback.

The private enemy update loop caches a waypoint and recomputes it when its refresh timer expires, the waypoint is reached, it falls behind the target direction or the route becomes blocked. A clear direct route clears that cached waypoint. The complete enemy class has gameplay and private asset dependencies, so the public demo exercises the actual pathfinder independently.

This method is intentionally lightweight: it uses axis-aligned bounds and a small candidate set. It does not guarantee a globally shortest route or successful escape from every arrangement. The fallback can return the target even when still obstructed. The demo highlights an obstructed fallback in amber instead of claiming it found a safe route.
