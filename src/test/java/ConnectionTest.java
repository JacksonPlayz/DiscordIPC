import com.jagrosh.discordipc.IPCClient;
import com.jagrosh.discordipc.IPCListener;
import com.jagrosh.discordipc.entities.RichPresence;
import com.jagrosh.discordipc.exceptions.NoDiscordClientException;

public class ConnectionTest {

    public static void main(String[] args) throws NoDiscordClientException {
        IPCClient client = new IPCClient(842150772178944081L);
        client.setListener(new IPCListener() {
            @Override
            public void onReady(IPCClient client) {
                client.sendRichPresence(new RichPresence.Builder().setDetails("Testing!").setState("Java 16 Test").build());
                System.out.println("Connected!");
            }
        });
        client.connect();
    }

}
