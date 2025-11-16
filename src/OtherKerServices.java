public class OtherKerServices {

    private final long memorySize; // total memory available
    private final int noDevs;      // total I/O devices available

    private long memInUse = 0;     // currently allocated memory
    private int devsInUse = 0;     // currently reserved devices

    public OtherKerServices(long memorySize, int noDevs) {
        this.memorySize = Math.max(0, memorySize);
        this.noDevs = Math.max(0, noDevs);
    }

    // ===== Memory =====
    public synchronized boolean allocateMemory(long amount) {
        if (amount <= 0) return false;
        if (memInUse + amount > memorySize) return false;
        memInUse += amount;
        return true;
    }

    public synchronized void deallocateMemory(long amount) {
        if (amount <= 0) return;
        memInUse = Math.max(0, memInUse - amount);
    }

    // ===== Devices =====
    public synchronized boolean reserveDevices(int n) {
        if (n <= 0) return false;
        if (devsInUse + n > noDevs) return false;
        devsInUse += n;
        return true;
    }

    public synchronized void releaseDevices(int n) {
        if (n <= 0) return;
        devsInUse = Math.max(0, devsInUse - n);
    }

    // ===== Atomic convenience for a Process =====
    // Assumes your Process has getters: getMemoryReq(), getDevReq()
    public synchronized boolean allocate(Process p) {
        long mem = p.getMemoryReq();
        int dev = p.getDevReq();

        if (!allocateMemory(mem)) return false;
        if (!reserveDevices(dev)) {
            // rollback memory if devices unavailable
            deallocateMemory(mem);
            return false;
        }
        return true;
    }

    public synchronized void release(Process p) {
        releaseDevices(p.getDevReq());
        deallocateMemory(p.getMemoryReq());
    }

    public boolean canEverFit(Process p) {
        // compare against TOTAL capacity, not current free values
        return p.getMemoryReq() <= memorySize && p.getDevReq() <= noDevs;
    }


    // ===== Introspection =====
    public synchronized long getFreeMemory() { return memorySize - memInUse; }
    public synchronized int getFreeDevices() { return noDevs - devsInUse; }
    public synchronized long getMemInUse()   { return memInUse; }
    public synchronized int getDevsInUse()   { return devsInUse; }
    public long getMemorySize()              { return memorySize; }
    public int getNoDevs()                   { return noDevs; }

    @Override
    public synchronized String toString() {
        return "OtherKerServices{mem=" + memInUse + "/" + memorySize +
                ", devs=" + devsInUse + "/" + noDevs + "}";
    }
}
