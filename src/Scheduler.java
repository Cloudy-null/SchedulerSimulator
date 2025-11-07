public abstract class Scheduler {

    // Pick the next process from READY (usually FIFO)
    public abstract Process selectNextProcess(Queue readyQ);

    // Decide the time slice (in ms or ticks) for the selected process.
    // READY is provided so Dynamic RR can compute SR/AR including everything currently in READY.
    public abstract int computeTimeSlice(Process selected, Queue readyQ);
}
