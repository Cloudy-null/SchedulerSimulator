public class FCFScheduler extends Scheduler {

    @Override
    public Process selectNextProcess(Queue readyQ) {
        return readyQ.dequeue(); // FIFO
    }

    @Override
    public int computeTimeSlice(Process selected, Queue readyQ) {
        // Non-preemptive: run to completion
        long rem = selected.getBurstTime();
        return (int)Math.max(1, rem);
    }
}