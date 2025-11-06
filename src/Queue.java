import java.util.LinkedList;
import java.util.ListIterator;

public class Queue {
    // schType matches the UML and project wording:
    //   "READY"  -> FIFO ready queue
    //   "HQ1"    -> Hold Queue 1 (sorted ascending by memoryReq; break ties FIFO by arrivalTime)
    //   "HQ2"    -> FIFO
    //   "SUBMIT" -> FIFO
    private final String schType;
    private final LinkedList<Process> q = new LinkedList<>();

    public Queue(String schType) {
        this.schType = schType == null ? "FIFO" : schType.toUpperCase();
    }

    public String getSchType() { return schType; }

    // +enqueue()
    public void enqueue(Process p) {
        if (p == null) return;

        if ("HQ1".equals(schType)) {
            // Insert in ascending order of requested memory; ties -> arrivalTime FIFO
            ListIterator<Process> it = q.listIterator();
            while (it.hasNext()) {
                Process cur = it.next();
                if (p.getMemoryReq() < cur.getMemoryReq() ||
                        (p.getMemoryReq() == cur.getMemoryReq()
                                && p.getArrivalTime() < cur.getArrivalTime())) {
                    it.previous();
                    it.add(p);
                    return;
                }
            }
            q.addLast(p);
        } else {
            // READY, HQ2, SUBMIT -> FIFO
            q.addLast(p);
        }
    }

    // dequeue()
    public Process dequeue() {
        return q.isEmpty() ? null : q.removeFirst();
    }

    // helpers
    public Process peek() { return q.peekFirst(); }
    public boolean isEmpty() { return q.isEmpty(); }
    public int size() { return q.size(); }
    public LinkedList<Process> snapshot() { return new LinkedList<>(q); }

    @Override
    public String toString() {
        return schType + q.toString();
    }
}
