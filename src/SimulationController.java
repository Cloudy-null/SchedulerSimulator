import java.io.*;
import java.util.*;

/**
 * SimulationController
 * ---------------------
 * Reads input commands from the input file,
 * drives the global simulation time, manages configuration resets,
 * and produces formatted output into "group7_output.txt".
 *
 * This class does NOT perform scheduling logic itself.
 * It delegates all process management to the PrManage.
 */
public class SimulationController {

    private static long currentTime = 0L;

    // Core system components Used Later
    private static OtherKerServices sys = null;
    private static PrManager pr = null;

    // Tracks which scheduler type is used and whether the user already selected it
    private static String schedulerName = "DRR";
    private static boolean schedulerChosen = false;

    // Stores the chosen scheduler so it can be reused across multiple C commands
    private static Scheduler GLOBAL_SCHEDULER = null;

    // Shared scanner
    private static final Scanner SC = new Scanner(System.in);

    // Output goes into this file
    private static PrintWriter out;

    public static void main(String[] args) {

        // The simulator reads commands from this file
        String fileName = "src/input.txt";

        try {
            // Create output file writer
            out = new PrintWriter(new FileWriter("group7_output.txt"));
        } catch (IOException e) {
            System.out.println("Cannot create output file.");
            return;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {

            String line;

            // Read each line of input and dispatch based on first character
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                char type = line.charAt(0);

                switch (type) {

                    // ----- C COMMAND -------------------------------------------------
                    case 'C': {
                        List<Long> v = parseCmd(line);
                        long start   = v.get(0);
                        long memSize = v.get(1);
                        int  devs    = v.get(2).intValue();

                        sysGen(start, memSize, devs);
                        break;
                    }

                    // ----- A COMMAND ------------------------------------------------
                    case 'A': {
                        List<Long> a = parseCmd(line);
                        long at   = a.get(0);
                        long pid  = a.get(1);
                        long mReq = a.get(2);
                        int  dReq = a.get(3).intValue();
                        long bt   = a.get(4);
                        int  pri  = a.get(5).intValue();

                        // If this arrival is in the future, advance time to that moment
                        if (at > currentTime) {
                            pr.cpuTimeAdvance(at - currentTime);
                            currentTime = at;
                        }

                        // Let PrManager create the Process and enqueue to SUBMIT
                        pr.procArrivalRoutine(pid, at, bt, pri, mReq, dReq);

                        // Allow admissions
                        pr.cpuTimeAdvance(0);
                        break;
                    }


                    // ----- D COMMAND ------------------------------------------------
                    case 'D': {
                        long t = parseCmd(line).get(0);

                        // Jump to the requested display time
                        if (t >= currentTime) {
                            pr.cpuTimeAdvance(t - currentTime);
                            currentTime = t;
                            printSystemStatus(t);
                        }
                        break;
                    }

                    // ----- UNKNOWN LINE ---------------------------------------------------
                    default:
                        out.println("DEBUG -> Unknown line: " + line);
                }
            }

            // final internal events finish
            pr.cpuTimeAdvance(0);

            out.println();
            out.println("--- Simulation finished at time " + (double) currentTime + " ---");

        } catch (IOException e) {
            out.println("File error: " + e.getMessage());
        }

        out.close();
    }

    /**
     * sysGen()
     * -------
     * Called whenever the input file provides a new configuration (C line).
     * Reinitializes memory and device settings, creates a new PrManager,
     * and assigns the scheduler. Scheduler is selected only once.
     */
    private static void sysGen(long start, long memorySize, int numDevs) {

        // Create new kernel system + process manager
        sys = new OtherKerServices(memorySize, numDevs);
        pr  = new PrManager(start, sys);

        // Only ask the user for the scheduler the FIRST time a C command appears.
        if (!schedulerChosen) {

            try {
                System.out.print("Choose scheduler [DRR | SRR | FCFS]: ");
                String kind = SC.nextLine().trim().toUpperCase(Locale.ROOT);

                Scheduler chosen;

                switch (kind) {
                    case "SRR": {
                        System.out.print("Enter team number (for quantum = 10 + team) (WE ARE TEAM 7): ");
                        int team = Integer.parseInt(SC.nextLine().trim());
                        int q = 10 + team;

                        chosen = new SRoundRobinScheduler(q);
                        schedulerName = "StaticRR";

                        System.out.println("Scheduler = Static RR, quantum = " + q);
                        break;
                    }
                    case "FCFS": {
                        chosen = new FCFScheduler();
                        schedulerName = "FCFS";

                        System.out.println("Scheduler = FCFS");
                        break;
                    }
                    case "DRR":
                    default: {
                        chosen = new DRoundRobinScheduler();
                        schedulerName = "DynamicRR";

                        System.out.println("Scheduler = Dynamic RR (SR/AR)");
                    }
                }

                GLOBAL_SCHEDULER = chosen;
                pr.setScheduler(GLOBAL_SCHEDULER);
                schedulerChosen = true;

            } catch (Exception e) {
                // If input fails, default to dynamic RR
                GLOBAL_SCHEDULER = new DRoundRobinScheduler();
                pr.setScheduler(GLOBAL_SCHEDULER);
                schedulerName = "DynamicRR";

                System.out.println("Scheduler = Dynamic RR (default)");
                schedulerChosen = true;
            }

        } else {
            // For future configurations, reuse the already chosen scheduler
            pr.setScheduler(GLOBAL_SCHEDULER);
        }

        currentTime = 0;

        // Print formatted configuration header to output file
        out.printf("%nCONFIG at %.2f: mem=%d devices=%d scheduler=%s%n%n",
                (double) start, memorySize, numDevs, schedulerName);

        // Allow immediate internal processing
        pr.cpuTimeAdvance(0);
    }
    /**
     * parseCmd()
     * ----------
     * Parses numbers and values from input lines.
     * Example: A 10 J=1 M=5 S=4 R=8 P=1
     * Returns: [10, 1, 5, 4, 8, 1]
     */
    public static List<Long> parseCmd(String line) {
        List<Long> values = new ArrayList<>();
        String[] parts = line.trim().split(" ");

        for (int i = 1; i < parts.length; i++) {
            String p = parts[i];

            if (p.contains("="))
                values.add(Long.parseLong(p.substring(p.indexOf('=') + 1)));
            else
                values.add(Long.parseLong(p));
        }
        return values;
    }

    /**
     * printSystemStatus()
     * -------------------
     * Prints a formatted snapshot of the system state:
     * - memory and device usage
     * - ready and hold queues
     * - finished process table
     */
    private static void printSystemStatus(long t) {
        double time = (double) t;

        long totalMem   = sys.getMemorySize();
        long usedMem    = sys.getMemInUse();
        long availMem   = totalMem - usedMem;

        int  totalDevs  = sys.getNoDevs();
        int  usedDevs   = sys.getDevsInUse();
        int  availDevs  = totalDevs - usedDevs;

        out.println("-------------------------------------------------------");
        out.println("System Status:                                         ");
        out.println("-------------------------------------------------------");
        out.printf("          Time: %.2f%n", time);
        out.printf("  Total Memory: %d%n", totalMem);
        out.printf(" Avail. Memory: %d%n", availMem);
        out.printf(" Total Devices: %d%n", totalDevs);
        out.printf("Avail. Devices: %d%n", availDevs);
        out.println();

        // Ready queue
        out.println("Jobs in Ready List                                      ");
        out.println("--------------------------------------------------------");
        List<Process> ready = pr.getReadySnapshot();
        if (ready.isEmpty()) {
            out.println("  EMPTY");
        } else {
            for (Process p : ready) {
                out.printf("Job ID %d , %.2f Cycles left to completion.%n",
                        p.getPID(), (double) p.getBurstTime());
            }
        }
        out.println();

        // Submit queue
        out.println("Jobs in Long Job List                                   ");
        out.println("--------------------------------------------------------");
        List<Process> longList = pr.getSubmitSnapshot();
        if (longList.isEmpty()) {
            out.println("  EMPTY");
        } else {
            for (Process p : longList) {
                out.printf("Job ID %d , %.2f Cycles left to completion.%n",
                        p.getPID(), (double) p.getBurstTime());
            }
        }
        out.println();

        // Hold List 1
        out.println("Jobs in Hold List 1                                     ");
        out.println("--------------------------------------------------------");
        List<Process> hq1 = pr.getHQ1Snapshot();
        if (hq1.isEmpty()) {
            out.println("  EMPTY");
        } else {
            for (Process p : hq1) {
                out.printf("Job ID %d , %.2f Cycles left to completion.%n",
                        p.getPID(), (double) p.getBurstTime());
            }
        }
        out.println();

        // Hold List 2
        out.println("Jobs in Hold List 2                                     ");
        out.println("--------------------------------------------------------");
        List<Process> hq2 = pr.getHQ2Snapshot();
        if (hq2.isEmpty()) {
            out.println("  EMPTY");
        } else {
            for (Process p : hq2) {
                out.printf("Job ID %d , %.2f Cycles left to completion.%n",
                        p.getPID(), (double) p.getBurstTime());
            }
        }
        out.println();
        out.println();

        // Finished jobs table
        out.println("Finished Jobs (detailed)                                ");
        out.println("--------------------------------------------------------");
        out.println("  Job    ArrivalTime     CompleteTime     TurnaroundTime    WaitingTime");
        out.println("------------------------------------------------------------------------");

        List<long[]> finished = pr.getFinishedJobsSnapshot(t);

        if (finished.isEmpty()) {
            out.println("  EMPTY");
        } else {
            finished.sort(Comparator.comparingLong(a -> a[0]));

            for (long[] row : finished) {
                long pid = row[0];
                double at  = row[1];
                double ct  = row[2];
                double tat = row[3];
                double wt  = row[4];

                out.printf("  %-6d %-14.2f %-15.2f %-18.2f %-14.2f%n",
                        pid, at, ct, tat, wt);
            }

            // Only print the total at the final display
            if (t == 999999L) {
                int total = pr.getTotalFinishedCount();
                out.printf("Total Finished Jobs:             %d%n", total);
            }
        }

        out.println();
        out.println();
    }

}
