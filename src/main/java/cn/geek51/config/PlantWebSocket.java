package cn.geek51.config;

import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
@ServerEndpoint("/ws/plant")
public class PlantWebSocket {

    private static final CopyOnWriteArraySet<PlantWebSocket> CLIENTS = new CopyOnWriteArraySet<>();
    private Session session;

    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        CLIENTS.add(this);
    }

    @OnMessage
    public void onMessage(String message) {
        // no-op: clients only receive pushes
    }

    @OnClose
    public void onClose() {
        CLIENTS.remove(this);
    }

    public static void broadcast(String message) {
        for (PlantWebSocket client : CLIENTS) {
            try {
                if (client.session != null && client.session.isOpen()) {
                    client.session.getBasicRemote().sendText(message);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
