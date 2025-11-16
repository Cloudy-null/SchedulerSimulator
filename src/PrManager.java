public class PrManager {

    private long internalClock;
    private final OtherKerServices oks;

    // Queues managed here (per UML)
    private final Queue SUBMIT = new Queue("SUBMIT");
    private final Queue HQ1    = new Queue("HQ1");   // ascending mem
    private final Queue HQ2    = new Queue("HQ2");   // FIFO
    private final Queue READY  = new Queue("READY"); // FIFO for RR

    // Running state
    private Process running = null;
    private long runningUntil = Long.MAX_VALUE;
    private long lastDispatchAt = 0;

    // Plug-in scheduler (Strategy)
    private Scheduler scheduler = new DRoundRobinScheduler(); // default per spec

    public PrManager(long startTime, OtherKerServices oks) {
        this.internalClock = startTime;
        this.oks = oks;
    }

    public void setScheduler(Scheduler s) { this.scheduler = s; }

    // === PUBLIC API (UML) ===

    // new arrival from SimulationController
    public void procArrivalRoutine(Process p) {
        // ====== IMPOSSIBLE PROCESS CHECK ======
        // assumes: oks.canEverFit(p) returns false if p needs more
        // memory/devices than the system TOTAL capacity.
        if (!oks.canEverFit(p)) {
            // just ignore it; optionally print a message for debugging
            System.out.println("t=" + internalClock + " IGNORE P" + p.getPID()
                    + " (needs M=" + p.getMemoryReq()
                    + ", R=" + p.getDevReq()
                    + " > system capacity)");
            return;
        }
        // land first in SUBMIT; admission occurs when time advances
        SUBMIT.enqueue(p);
    }

    // advance PR’s internal clock by duration
    public void cpuTimeAdvance(long duration) {
        if (duration < 0) return;
        long target = internalClock + duration;
        dispatch(target); // private engine
    }

    // how long until next CPU decision (slice end/finish) from *now*
    public long getNextDecisionTime() {
        if (running == null || runningUntil == Long.MAX_VALUE) return 0L;
        long dt = runningUntil - internalClock;
        return Math.max(0L, dt);
    }

    public long getRunningProcId() {
        return (running == null) ? -1L : running.getPID();
    }

    public void printSnapshot() {
        System.out.println("---- PR Snapshot @ " + internalClock + " ----");
        System.out.println("READY : " + READY);
        System.out.println("HQ1   : " + HQ1);
        System.out.println("HQ2   : " + HQ2);
        System.out.println("SUBMIT: " + SUBMIT);
        System.out.println("RUN   : " + (running == null
                ? "idle"
                : ("PID " + running.getPID() + " until " + runningUntil)));
        System.out.println(oks);
        System.out.println("-----------------------------------");
    }

    // === PRIVATE engine ===

    private void dispatch(long target) {
        if (target < internalClock) return;

        // Admit anything pending at the current time before we start
        drainSubmitToSystem();
        tryAdmitFromHolds();

        // Ensure something is running if CPU idle
        if (running == null && !READY.isEmpty()) {
            scheduleNext();
            long rem = getRemainingBurst(running);
            int slice = scheduler.computeTimeSlice(running, READY);
            runningUntil = internalClock + Math.max(1, (int) Math.min(rem, slice));
        }

        // Walk from event to event until we reach target
        while (true) {
            // If there is no running proc or the next decision happens after target,
            // just advance the clock to target and stop.
            if (running == null || runningUntil > target) {
                internalClock = target;
                break;
            }

            // A CPU decision occurs at runningUntil (<= target):
            // jump time there, close this slice, admit again, and (re)schedule.
            internalClock = runningUntil;

            // Close the slice (finish or time-slice expiry)
            completeOrPreemptRunning();

            // Try to admit from SUBMIT/HQs at this exact time
            drainSubmitToSystem();
            tryAdmitFromHolds();

            // If we can run something next, do it and compute a new runningUntil
            if (running == null && !READY.isEmpty()) {
                scheduleNext();
                long rem = getRemainingBurst(running);
                int slice = scheduler.computeTimeSlice(running, READY);
                runningUntil = internalClock + Math.max(1, (int) Math.min(rem, slice));
            } else if (running == null) {
                // Nothing to run; we can fast-forward to target.
                // (Loop will exit on next iteration.)
            }
        }
    }

    private void drainSubmitToSystem() {
        while (!SUBMIT.isEmpty()) {
            Process p = SUBMIT.dequeue();

            // at this point we already filtered impossible jobs in procArrivalRoutine
            if (oks.allocate(p)) {
                READY.enqueue(p);
            } else {
                if (p.getPriority() == 1) {
                    HQ1.enqueue(p);
                } else {
                    HQ2.enqueue(p);
                }
            }
        }
    }

    private void tryAdmitFromHolds() {
        boolean moved;
        do {
            moved = false;
            if (!HQ1.isEmpty()) {
                Process h1 = HQ1.peek();
                if (oks.allocate(h1)) {
                    HQ1.dequeue();
                    READY.enqueue(h1);
                    moved = true;
                }
            }
            if (!moved && !HQ2.isEmpty()) {
                Process h2 = HQ2.peek();
                if (oks.allocate(h2)) {
                    HQ2.dequeue();
                    READY.enqueue(h2);
                    moved = true;
                }
            }
        } while (moved);
    }

    private void scheduleNext() {
        running = scheduler.selectNextProcess(READY);
        lastDispatchAt = internalClock;
    }

    private void completeOrPreemptRunning() {
        if (running == null) return;

        long elapsed = runningUntil - lastDispatchAt;
        long rem = getRemainingBurst(running) - elapsed;
        setRemainingBurst(running, Math.max(0, rem));

        boolean finished = (getRemainingBurst(running) <= 0);
        if (finished) {
            oks.release(running);
            // could push to a Completed queue for stats if needed
            running = null;
        } else {
            // quantum expired → RR: requeue
            READY.enqueue(running);
            running = null;
        }
    }

    // PCB helpers (adapt to your Process API if names differ)
    private long getRemainingBurst(Process p) { return p.getBurstTime(); }
    private void setRemainingBurst(Process p, long v) { p.setBurstTime(v); }
}
