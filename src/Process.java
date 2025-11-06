public class Process {
    // «PCB» fields (from UML)
    private long PID;
    private long arrivalTime;
    private long burstTime;
    private int  priority;
    private long memoryReq;
    private int  devReq;
    private int  state; // (0=new,1=ready,2=running,3=blocked,4=finished)

    public Process(long PID, long arrivalTime, long burstTime,
                   int priority, long memoryReq, int devReq, int state) {
        this.PID = PID;
        this.arrivalTime = arrivalTime;
        this.burstTime = burstTime;
        this.priority = priority;
        this.memoryReq = memoryReq;
        this.devReq = devReq;
        this.state = state;
    }

    // Getters / setters
    public long getPID() { return PID; }
    public void setPID(long PID) { this.PID = PID; }

    public long getArrivalTime() { return arrivalTime; }
    public void setArrivalTime(long arrivalTime) { this.arrivalTime = arrivalTime; }

    public long getBurstTime() { return burstTime; }
    public void setBurstTime(long burstTime) { this.burstTime = burstTime; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public long getMemoryReq() { return memoryReq; }
    public void setMemoryReq(long memoryReq) { this.memoryReq = memoryReq; }

    public int getDevReq() { return devReq; }
    public void setDevReq(int devReq) { this.devReq = devReq; }

    public int getState() { return state; }
    public void setState(int state) { this.state = state; }

    @Override
    public String toString() {
        return "P{PID=" + PID +
                ", at=" + arrivalTime +
                ", bt=" + burstTime +
                ", prio=" + priority +
                ", mem=" + memoryReq +
                ", dev=" + devReq +
                ", state=" + state + "}";
    }
}
