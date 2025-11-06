import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SimulationController {
    public static void main(String[] args) {
        String fileName = "src/input.txt"; // change to your file path

        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            String line;

            while ((line = br.readLine()) != null) {

                // Trim spaces
                line = line.trim();
                if (line.isEmpty()) continue; // skip empty lines

                // Get the first letter
                char type = line.charAt(0);

                switch (type) {
                    case 'C':
                        List<Long> cVals = parseCmd(line);

                        long arTime = cVals.get(0);
                        long memSize = cVals.get(1);
                        int numDevs = cVals.get(2).intValue();

                        OtherKerServices sys = new OtherKerServices(memSize,numDevs);

                        break;

                    case 'A':
                        List<Long> aVals = parseCmd(line);

                        long arrivalTime = aVals.get(0);
                        long PID         = aVals.get(1);
                        long memoryReq   = aVals.get(2);
                        long burstTime   = aVals.get(3);
                        int devReq       = aVals.get(4).intValue();
                        int priority     = aVals.get(5).intValue();

                        Process p = new Process(PID, arrivalTime, burstTime, priority, memoryReq, devReq, 0);



                        break;

                    case 'D':
                        List<Long> dVals = parseCmd(line);
                        System.out.println("D values: " + dVals);
                        break;

                    default:
                        System.out.println("DEBUG -> Unknown Line: " + line);
                        break;
                }
            }

        } catch (IOException e) {
            System.out.println("File error: " + e.getMessage());
        }
    } // end of main :)

    public static List<Long> parseCmd(String line) {
        List<Long> values = new ArrayList<>();

        // Split by spaces
        String[] parts = line.trim().split(" ");

        // First part has only the command type (C / A / D), skip it
        for (int i = 1; i < parts.length; i++) {
            String p = parts[i];

            // Cases:
            //     "9"           -> pure number
            //     "M=45"        -> key=value, get value
            //     "D 36"        -> same logic will capture 36
            if (p.contains("=")) {
                // Split "M=45" -> ["M","45"] → take 45
                String value = p.substring(p.indexOf('=') + 1);
                values.add(Long.parseLong(value));
            } else {
                // Pure number like "9" or "36"
                values.add(Long.parseLong(p));
            }
        }

        return values;
    } // end of parseCmd
}
