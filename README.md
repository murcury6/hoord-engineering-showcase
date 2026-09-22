# Hoord · Compact proprietary game models

**LLM-generated game assets using proprietary Hoord models with 8 million parameters or fewer each, on less than 8 GB of VRAM.**

By Jordyn Wood. The project explores how small, specialized generative models can fit into a complete game-development workflow—not simply produce isolated images.

The private models feed a custom **8-stage development panel** for generation, human approval, asset finishing, scene review, weapons, music and sound. Approved outputs are prepared for the game's existing rendering, lighting, collision and animation systems. Generation stays in the authoring pipeline, separate from gameplay.

The parameter and VRAM figures are creator-reported for the private generation setup. The parameter limit is **per model**, not a combined pipeline total.

## What you can inspect

- [Development pipeline](docs/PIPELINE.md): how the authoring and review stages fit together.
- [AI integration](docs/AI_INTEGRATION.md): production job queue, provider boundary and process lifecycle.
- [Engineering source](docs/PROVENANCE.md): enemy navigation, weapon-expression parsing, procedural sound and reviewed runtime utilities.
- [Remote review tools](reference/remote-review/README.md): video transport and structured human feedback.

**The actual model architectures, weights, tensors, checkpoints, training code, datasets, private prompts and keys are not distributed.** This repository shares selected engineering work and approved finished outputs, not the proprietary AI systems.

[Download the playable outputs](https://github.com/murcury6/hoord-engineering-showcase/releases/tag/v0.1.0-locked-demo) — extract the ZIP and run with Java 17+. This is a desktop download, not a browser game. It performs no runtime generation or inference.

[Technical appendix and controls](docs/LOCKED_DEMO.md) · [Source viewer instructions](tools/run.ps1) · [License](LICENSE)

All rights reserved.
