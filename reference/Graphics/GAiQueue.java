// File: src/Graphics/GAiQueue.java
package Graphics;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import hoordGame.AiHandlerClass;

public final class GAiQueue {

    private GAiQueue() {}

    private static volatile String active = "idle";
    private static volatile long activeStartMs = 0L;
    private static volatile long lastBlockedLogMs = 0L;

    public static void runLoop(long workerGeneration) {
        AiHandlerClass.notifyImageMessage("[AIQ-GAI] started | gai{active=idle, t=0s, q(ship/ast/bg/fx/thruster/particle)=0/0/0/0}");

        long lastStatus = System.currentTimeMillis();
        int id = 0;

        while (GAiAccess.isWorkerCurrent(workerGeneration)) {
            String envIssue = GAiAccess.preflightIssue();
            if (!envIssue.isBlank()) {
                long now = System.currentTimeMillis();
                if (now - lastBlockedLogMs >= 5000L) {
                    lastBlockedLogMs = now;
                    AiHandlerClass.notifyImageMessage("[AIQ-GAI] blocked | " + envIssue);
                }
                try {
                    AiHandlerClass.waitForQueueSignal(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
					break;
                }
                continue;
            }

            String kind = AiHandlerClass.consumeNextImageQueueKind();
            if (kind.isBlank()) {
                long now = System.currentTimeMillis();
                if (now - lastStatus >= 5000L) {
                    printStatus();
                    lastStatus = now;
                }
                try {
                    AiHandlerClass.waitForQueueSignal(250);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
					break;
                }
                continue;
            }

            id++;
            long seed = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);

            try {
                workOne(kind, id, seed);
            } catch (Throwable t) {
                AiHandlerClass.onImageJobFail(id, kind, seed, null, t.getMessage());
                AiHandlerClass.notifyImageMessage("[AIQ-GAI] FAIL " + kind + " id=" + id + " seed=" + seed + " err=" + t.getMessage());
            }

            long now = System.currentTimeMillis();
            if (now - lastStatus >= 5000) {
                printStatus();
                lastStatus = now;
            }
        }

        AiHandlerClass.notifyImageMessage("[AIQ-GAI] stopped");
    }

    private static void workOne(String kind, int id, long seed) throws IOException {
        long prepareStartMs = System.currentTimeMillis();
        AiHandlerClass.notifyImageMessage("[AIQ-GAI] PREP  " + kind
                + " id=" + id + " seed=" + seed);
        Random rng = new Random(seed ^ 0x9E3779B97F4A7C15L);

        GAiPromptUtil.PromptPack pack = GAiPromptUtil.loadForKind(GAiAccess.INPUTS_DIR, kind, rng);
        long prepareMs = Math.max(0L, System.currentTimeMillis() - prepareStartMs);
        AiHandlerClass.notifyImageMessage("[AIQ-GAI] READY " + kind
                + " id=" + id + " prepMs=" + prepareMs);

        String r1 = GAiPromptUtil.sanitizeFileSeg(pack.r1);
        String r2 = GAiPromptUtil.sanitizeFileSeg(pack.r2);

        String kindName = kind;
        if ("astroid".equalsIgnoreCase(kindName) || "asteroid".equalsIgnoreCase(kindName)) kindName = "astroid";

        String fileName = GAiTuning.buildOutputFileName(kindName, seed, r1, r2);

        Path outDir = GAiAccess.OUTPUT_DIR.resolve(GAiTuning.outputSubdir(kindName));
        Path outFile = outDir.resolve(fileName);

		int w = GAiTuning.wFor(kindName);
		int h = GAiTuning.hFor(kindName);
		int steps = GAiTuning.stepsFor(kindName);
		float cfg = GAiTuning.cfgFor(kindName);

		AiHandlerClass.onImageJobStart(
				id,
				kindName,
				seed,
				outFile,
				pack.r1,
				pack.r2,
				pack.positive,
				pack.negative,
				w,
				h,
				steps,
				cfg);

        AiHandlerClass.notifyImageMessage("[AIQ-GAI] START " + kindName
				+ " id=" + id
				+ " seed=" + seed
				+ " size=" + w + "x" + h
				+ " steps=" + steps
				+ " cfg=" + cfg
				+ " out=" + outFile);
		AiHandlerClass.notifyImageMessage("[AIQ-GAI] PROMPT " + kindName
				+ " id=" + id
				+ " r1=" + safeLog(pack.r1)
				+ " r2=" + safeLog(pack.r2));
		AiHandlerClass.notifyImageMessage("[AIQ-GAI] POS    " + kindName + " id=" + id + " " + safeLog(pack.positive));
		AiHandlerClass.notifyImageMessage("[AIQ-GAI] NEG    " + kindName + " id=" + id + " " + safeLog(pack.negative));

		setActive("#" + id + ":" + kindName, System.currentTimeMillis());
		AiHandlerClass.notifyImageMessage("[AIQ-GAI] WORK  " + kindName + " id=" + id + " seed=" + seed);

		try {
			final String posFinal = pack.positive;
			final String negFinal = pack.negative;

			/* Tab 1 is the approval authority. Do not preselect, reroll, score-reject,
			 * or hide a successfully generated image before a person reviews it. */
			long bytes = GAiAccess.runPythonForHumanApproval(
					kindName, seed, posFinal, negFinal,
					outFile, w, h, steps, cfg);

			GraphicsAiFeedback.recordCandidate(kindName, outFile, pack.r1, pack.r2,
					pack.positive, pack.negative, "queue");
			AiHandlerClass.onImageJobDone(id, kindName, seed, outFile, bytes);
			AiHandlerClass.notifyImageMessage("[AIQ-GAI] DONE  " + kindName + " id=" + id + " seed=" + seed + " bytes=" + bytes + " out=" + outFile);
		} finally {
			setActive("idle", 0L);
		}
	}

	private static String safeLog(String text) {
		if (text == null) return "";
		return text.replace('\r', ' ').replace('\n', ' ').trim();
	}

    private static void setActive(String a, long startMs) {
        active = a;
        activeStartMs = startMs;
    }

    private static void printStatus() {
        long now = System.currentTimeMillis();
        long t = (activeStartMs <= 0) ? 0 : Math.max(0, (now - activeStartMs) / 1000);
        AiHandlerClass.QueueSnapshot queues = AiHandlerClass.getQueueSnapshot();
        AiHandlerClass.notifyImageMessage("[AIQ-GAI] STATUS gai{active=" + active + ", t=" + t + "s, q(ship/ast/bg/fx/thruster/particle)=" + queues.formatImageCounts() + "}");
    }
}
