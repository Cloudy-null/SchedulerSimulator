public class SRoundRobinScheduler extends Scheduler {
    private final int quantum;

    public SRoundRobinScheduler(int quantum) {
        this.quantum = Math.max(1, quantum);
    }

    @Override
    public Process selectNextProcess(Queue readyQ) {
        return readyQ.dequeue(); // FIFO among READY
    }

    @Override
    public int computeTimeSlice(Process selected, Queue readyQ) {
        return quantum; // fixed
    }
}
