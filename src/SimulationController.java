import java.io.*;
import java.util.*;

public class SimulationController {

    private static long currentTime = 0L;

    // Kernel + PR
    private static OtherKerServices sys = null;
    private static PrManager pr = null;

    // For pretty "CONFIG at t: ... scheduler=..."
    private static String schedulerName = "DRR";

    // ONE global Scanner on System.in (do NOT close it)
    private static final Scanner SC = new Scanner(System.in);

    public static void main(String[] args) {
        String fileName = "src/input.txt";

        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                char type = line.charAt(0);

                switch (type) {
                    case 'C': { // C 9 M=45 S=12  (start, memory, devices)
                        List<Long> v = parseCmd(line);
                        long start   = v.get(0);
                        long memSize = v.get(1);
                        int  devs    = v.get(2).intValue();
                        sysGen(start, memSize, devs); // ← per spec
                        break;
                    }

                    case 'A': { // A 10 J=1 M=5 S=4 R=8 P=1
                        List<Long> a = parseCmd(line);
                        long at   = a.get(0);
                        long pid  = a.get(1);
                        long mReq = a.get(2);
                        int  dReq = a.get(3).intValue();
                        long bt   = a.get(4);
                        int  pri  = a.get(5).intValue();

                        // Advance PR time to the arrival instant (discrete-event)
                        if (at > currentTime) {
                            pr.cpuTimeAdvance(at - currentTime);
                            currentTime = at;
                        }

                        Process p = new Process(pid, at, bt, pri, mReq, dReq, 0);
                        pr.procArrivalRoutine(p);

                        // zero-time housekeeping (admit/schedule if possible)
                        pr.cpuTimeAdvance(0);
                        break;
                    }

                    case 'D': { // D t  — advance clock to t and print formatted system status
                        long t = parseCmd(line).get(0);
                        if (t >= currentTime) {
                            pr.cpuTimeAdvance(t - currentTime);
                            currentTime = t;
                            printSystemStatus(t);
                        }
                        break;
                    }

                    default:
                        System.out.println("DEBUG -> Unknown line: " + line);
                }
            }

            // End of input: flush once (optional)
            pr.cpuTimeAdvance(0);

            System.out.println();
            System.out.println("--- Simulation finished at time " + (double) currentTime + " ---");

        } catch (IOException e) {
            System.out.println("File error: " + e.getMessage());
        }
    }

    // ---- per spec: interpret C line and build sys + PR + Scheduler
    private static void sysGen(long start, long memorySize, int numDevs) {
        sys = new OtherKerServices(memorySize, numDevs);
        pr  = new PrManager(start, sys);

        // Ask user which scheduler to use (DRR/SRR/FCFS)
        try {
            System.out.print("Choose scheduler [DRR | SRR | FCFS]: ");
            String kind = SC.nextLine().trim().toUpperCase(Locale.ROOT);

            switch (kind) {
                case "SRR": {
                    System.out.print("Enter team number (for quantum = 10 + team): ");
                    int team = Integer.parseInt(SC.nextLine().trim());
                    int q = 10 + Math.max(0, team);
                    pr.setScheduler(new SRoundRobinScheduler(q));
                    schedulerName = "StaticRR";
                    System.out.println("Scheduler = Static RR, quantum = " + q);
                    break;
                }
                case "FCFS": {
                    pr.setScheduler(new FCFScheduler());
                    schedulerName = "FCFS";
                    System.out.println("Scheduler = FCFS");
                    break;
                }
                case "DRR":
                default: {
                    pr.setScheduler(new DRoundRobinScheduler()); // default DRR
                    schedulerName = "DynamicRR";
                    System.out.println("Scheduler = Dynamic RR (SR/AR)");
                }
            }
        } catch (Exception e) {
            // If something goes wrong reading input, fall back safely
            pr.setScheduler(new DRoundRobinScheduler());
            schedulerName = "DynamicRR";
            System.out.println("Scheduler = Dynamic RR (default)");
        }

        currentTime = start;
        // CONFIG line like sample:
        System.out.printf("%nCONFIG at %.2f: mem=%d devices=%d scheduler=%s%n%n",
                (double) start, memorySize, numDevs, schedulerName);

        // Let PR settle at start time
        pr.cpuTimeAdvance(0);
    }

    // Pretty system printout matching the spec style
    private static void printSystemStatus(long t) {
        double time = (double) t;

        long totalMem   = sys.getMemorySize();
        long usedMem    = sys.getMemInUse();
        long availMem   = totalMem - usedMem;
        int  totalDevs  = sys.getNoDevs();
        int  usedDevs   = sys.getDevsInUse();
        int  availDevs  = totalDevs - usedDevs;

        System.out.println("-------------------------------------------------------");
        System.out.println("System Status:                                         ");
        System.out.println("-------------------------------------------------------");
        System.out.printf("          Time: %.2f%n", time);
        System.out.printf("  Total Memory: %d%n", totalMem);
        System.out.printf(" Avail. Memory: %d%n", availMem);
        System.out.printf(" Total Devices: %d%n", totalDevs);
        System.out.printf("Avail. Devices: %d%n", availDevs);
        System.out.println();

        // ===== Ready List =====
        System.out.println("Jobs in Ready List                                      ");
        System.out.println("--------------------------------------------------------");
        List<Process> ready = pr.getReadySnapshot();
        if (ready.isEmpty()) {
            System.out.println("  EMPTY");
        } else {
            for (Process p : ready) {
                System.out.printf("Job ID %d , %.2f Cycles left to completion.%n",
                        p.getPID(), (double) p.getBurstTime());
            }
        }
        System.out.println();

        // ===== Long Job List (SUBMIT) =====
        System.out.println("Jobs in Long Job List                                   ");
        System.out.println("--------------------------------------------------------");
        List<Process> longList = pr.getSubmitSnapshot();
        if (longList.isEmpty()) {
            System.out.println("  EMPTY");
        } else {
            for (Process p : longList) {
                System.out.printf("Job ID %d , %.2f Cycles left to completion.%n",
                        p.getPID(), (double) p.getBurstTime());
            }
        }
        System.out.println();

        // ===== Hold List 1 (HQ1) =====
        System.out.println("Jobs in Hold List 1                                     ");
        System.out.println("--------------------------------------------------------");
        List<Process> hq1 = pr.getHQ1Snapshot();
        if (hq1.isEmpty()) {
            System.out.println("  EMPTY");
        } else {
            for (Process p : hq1) {
                System.out.printf("Job ID %d , %.2f Cycles left to completion.%n",
                        p.getPID(), (double) p.getBurstTime());
            }
        }
        System.out.println();

        // ===== Hold List 2 (HQ2) =====
        System.out.println("Jobs in Hold List 2                                     ");
        System.out.println("--------------------------------------------------------");
        List<Process> hq2 = pr.getHQ2Snapshot();
        if (hq2.isEmpty()) {
            System.out.println("  EMPTY");
        } else {
            for (Process p : hq2) {
                System.out.printf("Job ID %d , %.2f Cycles left to completion.%n",
                        p.getPID(), (double) p.getBurstTime());
            }
        }
        System.out.println();
        System.out.println();

        // ===== Finished Jobs (detailed) =====
        System.out.println("Finished Jobs (detailed)                                ");
        System.out.println("--------------------------------------------------------");
        System.out.println("  Job    ArrivalTime     CompleteTime     TurnaroundTime    WaitingTime");
        System.out.println("------------------------------------------------------------------------");

        List<long[]> finished = pr.getFinishedJobsSnapshot(t);
        if (finished.isEmpty()) {
            System.out.println("  EMPTY");
        } else {
            finished.sort(Comparator.comparingLong(a -> a[0]));
            for (long[] row : finished) {
                long pid = row[0];
                double at  = row[1];
                double ct  = row[2];
                double tat = row[3];
                double wt  = row[4];

                System.out.printf("  %-6d %-14.2f %-15.2f %-18.2f %-14.2f%n",
                        pid, at, ct, tat, wt);
            }
            if (t == 999999L) {
                int total = pr.getTotalFinishedCount();
                System.out.printf("Total Finished Jobs:             %d%n", total);
            }
        }
        System.out.println();
        System.out.println();
    }

    public static List<Long> parseCmd(String line) {
        List<Long> values = new ArrayList<>();
        String[] parts = line.trim().split(" ");
        for (int i = 1; i < parts.length; i++) {
            String p = parts[i];
            if (p.contains("=")) values.add(Long.parseLong(p.substring(p.indexOf('=') + 1)));
            else values.add(Long.parseLong(p));
        }
        return values;
    }
}
