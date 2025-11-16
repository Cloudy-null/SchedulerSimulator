import java.io.*;
import java.util.*;

public class SimulationController {

    private static long currentTime = 0L;

    // Kernel + PR
    private static OtherKerServices sys = null;
    private static PrManager pr = null;

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
                        int dReq   = a.get(3).intValue();
                        long  bt = a.get(4);
                        int  pri  = a.get(5).intValue();

                        // Advance PR time to the arrival instant (discrete-event)
                        if (at > currentTime) {
                            pr.cpuTimeAdvance(at - currentTime);
                            currentTime = at;
                        }

                        Process p = new Process(pid, at, bt, pri, mReq, dReq, 0); // TODO send the data not the object
                        pr.procArrivalRoutine(p);

                        // zero-time housekeeping (admit/schedule if possible)
                        pr.cpuTimeAdvance(0);
                        break;
                    }

                    case 'D': { // D t  — advance clock to t and print snapshot
                        long t = parseCmd(line).get(0);
                        if (t >= currentTime) {
                            pr.cpuTimeAdvance(t - currentTime);
                            currentTime = t;
                            pr.printSnapshot();
                        }
                        break;
                    }

                    default:
                        System.out.println("DEBUG -> Unknown line: " + line);
                }
            }

            // End of input: flush once (optional)
            pr.cpuTimeAdvance(0);

        } catch (IOException e) {
            System.out.println("File error: " + e.getMessage());
        }
    }

    // ---- per spec: interpret C line and build sys + PR + Scheduler
    private static void sysGen(long start, long memorySize, int numDevs) {
        sys = new OtherKerServices(memorySize, numDevs);
        pr  = new PrManager(start, sys);

        // Ask user which scheduler to use (DRR/SRR/FCFS)
        try (Scanner sc = new Scanner(System.in)) {
            System.out.print("Choose scheduler [DRR | SRR | FCFS]: ");
            String kind = sc.nextLine().trim().toUpperCase(Locale.ROOT);

            switch (kind) {
                case "SRR": {
                    System.out.print("Enter team number (for quantum = 10 + team): ");
                    int team = Integer.parseInt(sc.nextLine().trim());
                    int q = 10 + Math.max(0, team);
                    pr.setScheduler(new SRoundRobinScheduler(q));
                    System.out.println("Scheduler = Static RR, quantum = " + q);
                    break;
                }
                case "FCFS": {
                    pr.setScheduler(new FCFScheduler());
                    System.out.println("Scheduler = FCFS");
                    break;
                }
                case "DRR":
                default: {
                    pr.setScheduler(new DRoundRobinScheduler()); // default DRR
                    System.out.println("Scheduler = Dynamic RR (SR/AR)");
                }
            }
        } catch (Exception ignore) {
            // If stdin not available (e.g., tests), default to DRR
            pr.setScheduler(new DRoundRobinScheduler());
            System.out.println("Scheduler = Dynamic RR (default)");
        }

        currentTime = start;
        // Let PR settle at start time
        pr.cpuTimeAdvance(0);
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
