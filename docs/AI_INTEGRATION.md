# Showing AI use without distributing the models

The public boundary includes application orchestration: queueing work, checking readiness, passing a request to a private provider, reporting progress, handling failure, and collecting a result for human review. It excludes the actual AI implementation and learned artifacts.

## Production reference

[`GAiQueue.java`](../reference/Graphics/GAiQueue.java) is the actual image-work queue. Read it in this order:

1. `runLoop` checks the worker generation and environment readiness, then waits for queue signals when work is unavailable.
2. It consumes a content kind and chooses a variation seed.
3. `workOne` obtains request text from a private prompt provider and resolves output metadata.
4. It announces a job start and calls `GAiAccess.runPythonForHumanApproval`. The provider implementation, models and prompt/data files are absent from this repo.
5. It records a candidate and reports completion. A generated image is not silently rejected or promoted before human review.
6. A `finally` block resets active state; failures are reported to the queue owner.

The source logs request text in the private application. Those logs are not included here. In a shared or hosted deployment, keep request bodies and provider error details out of public logs; record job identifiers and sanitized status instead. No real API credentials, prompts or data are embedded in this reference.

[`ProcessLifecycle.java`](../src/main/java/hoordGame/ProcessLifecycle.java) shows the existing bounded process-wait and descendant-shutdown helper. It is integration infrastructure, not model code.

## Runnable boundary example

[`ApprovalDemo.java`](../src/demo/java/ApprovalDemo.java) illustrates the same separation at a smaller scale. A `Provider` interface accepts a request and returns a candidate. A background Swing worker keeps work off the UI thread, and explicit Approve/Reject controls govern the candidate. The included provider returns a clearly labeled synthetic receipt, not generated art. It performs no networking, process launch, model loading, training or asset promotion.

Real integrations can implement that boundary inside the private application. Credentials belong in the private runtime's environment or secret store, never in the public desktop UI or repository. This example needs no credentials and does not ask reviewers to supply any.
