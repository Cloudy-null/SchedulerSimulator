import java.util.LinkedList;

public class DRoundRobinScheduler extends Scheduler {

    private boolean firstSlice = true; // first process uses its full remaining burst
    private long SR = 0;               // sum of remaining bursts in ready queue (spec)
    private int  AR = 0;               // average of bursts (spec)

    @Override
    public Process selectNextProcess(Queue readyQ) {
        return readyQ.dequeue(); // FIFO pick; time slice is computed next
    }

    @Override
    public int computeTimeSlice(Process selected, Queue readyQ) {
        // 1) First-ever slice: run the first process for its full remaining time
        long remSel = selected.getBurstTime();
        if (firstSlice) {
            firstSlice = false;
            SR = 0; AR = 0; // reset book keeping
            return (int)Math.max(1, remSel);
        }

        // 2) For subsequent decisions: AR = average of the remaining bursts
        long sum = remSel;
        int  cnt = 1;

        // READY snapshot (we DON'T remove selected here; it's already dequeued in PrManager)
        LinkedList<Process> snap = readyQ.snapshot();
        for (Process p : snap) {
            sum += p.getBurstTime();
            cnt++;
        }

        SR = sum;
        AR = (cnt == 0) ? 0 : (int)Math.max(1, Math.round((double)sum / cnt));

        return AR;
    }

    public long getSR() { return SR; }
    public int  getAR() { return AR; }
}
