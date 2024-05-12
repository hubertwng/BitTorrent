
import com.google.gson.Gson;

import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    private static final Gson gson = new Gson();

    public static void main(String[] args) throws Exception {
        // You can use print statements as follows for debugging, they'll be visible when running tests
        String command = args[0];
        if("decode".equals(command)) {
            //  Uncomment this block to pass the first stage
            String bencodedValue = args[1];
            Bencode bencode = null;
            try {
                bencode = Bencode.fromString(bencodedValue);
            } catch(RuntimeException e) {
                System.out.println(e.getMessage());
                return;
            }
            System.out.println(bencode.toSimpleJsonString());
            System.out.println(bencode.encodeToString());
        } if("info".equals(command)) {
            byte [] bytes = Files.readAllBytes(Paths.get(args[1]));
            Bencode bencode = Bencode.fromBytes(bytes);
            System.out.println(bencode.toSimpleJsonString());
        } else {
            throw new Exception("Unknown command: " + command);
        }

    }
}
