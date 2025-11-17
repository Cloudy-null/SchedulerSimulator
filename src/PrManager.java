import java.util.*;

public class PrManager {

    private long internalClock;
    private final OtherKerServices oks;

    // Queues per UML
    private final Queue SUBMIT = new Queue("SUBMIT");
    private final Queue HQ1    = new Queue("HQ1");   // ascending mem
    private final Queue HQ2    = new Queue("HQ2");   // FIFO
    private final Queue READY  = new Queue("READY"); // FIFO for RR

    // CPU running state
    private Process running = null;
    private long runningUntil = Long.MAX_VALUE;
    private long lastDispatchAt = 0;

    private Scheduler scheduler = new DRoundRobinScheduler();

    // ============================================================
    //                 FINISHED JOB ACCOUNTING
    // ============================================================

    // store original burst for waiting-time calculation
    private final Map<Long, Long> totalBurstByPid = new HashMap<>();

    private static class FinishedJob {
        long pid;
        long arrivalTime;
        long completionTime;
        long turnaroundTime;
        long waitingTime;

        FinishedJob(long pid, long at, long ct, long tat, long wt) {
            this.pid = pid;
            this.arrivalTime = at;
            this.completionTime = ct;
            this.turnaroundTime = tat;
            this.waitingTime = wt;
        }
    }

    private final List<FinishedJob> finishedJobs = new ArrayList<>();

    // ============================================================

    public PrManager(long startTime, OtherKerServices oks) {
        this.internalClock = startTime;
        this.oks = oks;
    }

    public void setScheduler(Scheduler s) {
        this.scheduler = s;
    }

    // ============================================================
    //                     PUBLIC API
    // ============================================================

    public void procArrivalRoutine(Process p) {

        // store original burst once
        totalBurstByPid.put(p.getPID(), p.getBurstTime());

        // reject impossible
        if (!oks.canEverFit(p)) {
            System.out.println("t=" + internalClock + " IGNORE P" + p.getPID()
                    + " (needs M=" + p.getMemoryReq()
                    + ", R=" + p.getDevReq() + " > system capacity)");
            return;
        }

        // Try admission immediately → READY or HOLD queue
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

    public void cpuTimeAdvance(long duration) {
        if (duration < 0) return;
        long target = internalClock + duration;
        dispatch(target);
    }

    public long getNextDecisionTime() {
        if (running == null || runningUntil == Long.MAX_VALUE) return 0;
        long dt = runningUntil - internalClock;
        return Math.max(0, dt);
    }

    public long getRunningProcId() {
        return (running == null) ? -1 : running.getPID();
    }

    // OLD debug snapshot (you can still use it if needed)
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

    // ============================================================
    //                      DISPATCH ENGINE
    // ============================================================

    private void dispatch(long target) {
        if (target < internalClock) return;

        drainSubmitToSystem();
        tryAdmitFromHolds();

        if (running == null && !READY.isEmpty()) {
            scheduleNext();
            long rem = getRemainingBurst(running);
            int slice = scheduler.computeTimeSlice(running, READY);
            runningUntil = internalClock + Math.max(1, (int) Math.min(rem, slice));
        }

        while (true) {

            if (running == null || runningUntil > target) {
                internalClock = target;
                break;
            }

            internalClock = runningUntil;

            completeOrPreemptRunning();

            drainSubmitToSystem();
            tryAdmitFromHolds();

            if (running == null && !READY.isEmpty()) {
                scheduleNext();
                long rem = getRemainingBurst(running);
                int slice = scheduler.computeTimeSlice(running, READY);
                runningUntil = internalClock + Math.max(1, (int) Math.min(rem, slice));
            }
        }
    }

    private void drainSubmitToSystem() {
        while (!SUBMIT.isEmpty()) {
            Process p = SUBMIT.dequeue();
            if (oks.allocate(p)) {
                READY.enqueue(p);
            } else {
                if (p.getPriority() == 1) HQ1.enqueue(p);
                else HQ2.enqueue(p);
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
        if (running != null) {
            running.setState(2);  // running
        }
        lastDispatchAt = internalClock;
    }

    private void completeOrPreemptRunning() {
        if (running == null) return;

        long elapsed = runningUntil - lastDispatchAt;
        long rem = getRemainingBurst(running) - elapsed;
        setRemainingBurst(running, Math.max(0, rem));

        boolean finished = (getRemainingBurst(running) <= 0);

        if (finished) {
            long pid = running.getPID();
            long at  = running.getArrivalTime();
            long ct  = internalClock;

            long originalBurst = totalBurstByPid.getOrDefault(pid, 0L);

            long turnaround = ct - at;
            long waiting    = turnaround - originalBurst;

            finishedJobs.add(new FinishedJob(pid, at, ct, turnaround, waiting));

            oks.release(running);
            running.setState(4);
            running = null;

        } else {
            running.setState(1); // ready
            READY.enqueue(running);
            running = null;
        }
    }

    // ============================================================
    //                      HELPERS
    // ============================================================

    private long getRemainingBurst(Process p) {
        return p.getBurstTime();
    }

    private void setRemainingBurst(Process p, long v) {
        p.setBurstTime(v);
    }

    // expose finished jobs for printing
    public List<long[]> getFinishedJobsSnapshot(long upToTime) {
        List<long[]> out = new ArrayList<>();
        for (FinishedJob fj : finishedJobs) {
            if (fj.completionTime <= upToTime) {
                out.add(new long[]{
                        fj.pid,
                        fj.arrivalTime,
                        fj.completionTime,
                        fj.turnaroundTime,
                        fj.waitingTime
                });
            }
        }
        return out;
    }

    public int getTotalFinishedCount() {
        return finishedJobs.size();
    }

    // === NEW: expose queue snapshots for formatted printing ===

    public List<Process> getReadySnapshot() {
        return READY.snapshot();
    }

    public List<Process> getHQ1Snapshot() {
        return HQ1.snapshot();
    }

    public List<Process> getHQ2Snapshot() {
        return HQ2.snapshot();
    }

    public List<Process> getSubmitSnapshot() {
        return SUBMIT.snapshot();
    }
}
