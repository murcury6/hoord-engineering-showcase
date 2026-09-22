package hoordGame;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/** Bounded process shutdown shared by image and text generation. */
public final class ProcessLifecycle {
	private ProcessLifecycle() {}

	public static boolean waitForExit(Process p, long timeoutMs) {
		if (p == null) return true;
		long safeTimeout = Math.max(1L, timeoutMs);
		try {
			return p.waitFor(safeTimeout, TimeUnit.MILLISECONDS);
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
			return !p.isAlive();
		} catch (Throwable ignore) {
			return !p.isAlive();
		}
	}

	public static void destroyProcessTree(Process p, boolean force) {
		if (p == null) return;
		try {
			ProcessHandle root = p.toHandle();
			ArrayList<ProcessHandle> descendants = new ArrayList<>();
			try {
				root.descendants().forEach(descendants::add);
			} catch (Throwable ignore) {}

			for (int i = descendants.size() - 1; i >= 0; i--) {
				ProcessHandle ph = descendants.get(i);
				try {
					if (force) ph.destroyForcibly();
					else ph.destroy();
				} catch (Throwable ignore) {}
			}

			try {
				if (force) root.destroyForcibly();
				else root.destroy();
			} catch (Throwable ignore) {}
		} catch (Throwable ignore) {
			try {
				if (force) p.destroyForcibly();
				else p.destroy();
			} catch (Throwable ignore2) {}
		}
	}

}
