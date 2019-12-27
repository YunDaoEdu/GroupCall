package cn.net.trc.connect;

import cn.net.trc.connect.pojo.Room;
import cn.net.trc.connect.pojo.RoomManager;
import cn.net.trc.connect.pojo.UserRegistry;
import cn.net.trc.connect.pojo.UserSession;
import cn.net.trc.pojo.UserInfo;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.kurento.client.IceCandidate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;


public class CallHandler extends TextWebSocketHandler {

    private static final Gson gson = new GsonBuilder().create();

    @Autowired
    private RoomManager roomManager;

    @Autowired
    private UserRegistry registry;


    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        final JsonObject jsonMessage = gson.fromJson(message.getPayload(), JsonObject.class);

        //final UserSession user = registry.getBySession(session);


        final Map<String, Object> attributes = session.getAttributes();
        final UserInfo userInfo = (UserInfo) attributes.get("UserInfo");
        final String userName = userInfo.getUserName();
        final String roomName = userInfo.getUserRoom();
        final UserSession user = roomManager.getRoom(roomName).getUserSession(userName);

        switch (jsonMessage.get("id").getAsString()) {
            case "joinRoom":
                joinRoom(userName, roomName, session);
                break;
            case "receiveVideoFrom":
                final String senderName = jsonMessage.get("sender").getAsString();
                final UserSession sender = registry.getByName(senderName);
                final String sdpOffer = jsonMessage.get("sdpOffer").getAsString();
                user.receiveVideoFrom(sender, sdpOffer);
                break;
            case "leaveRoom":
                leaveRoom(user);
                break;
            case "onIceCandidate":
                JsonObject candidate = jsonMessage.get("candidate").getAsJsonObject();

                if (user != null) {
                    IceCandidate cand = new IceCandidate(candidate.get("candidate").getAsString(),
                            candidate.get("sdpMid").getAsString(), candidate.get("sdpMLineIndex").getAsInt());
                    user.addCandidate(cand, jsonMessage.get("name").getAsString());
                }
                break;
            default:
                break;
        }
    }


    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        UserSession user = registry.removeBySession(session);
        roomManager.getRoom(user.getRoomName()).leave(user);
    }


    private void joinRoom(String userName, String roomName, WebSocketSession session) throws IOException {
        Room room = roomManager.getRoom(roomName);
        final UserSession user = room.join(userName, session);
        registry.register(user);
    }


    private void leaveRoom(UserSession user) throws IOException {
        final Room room = roomManager.getRoom(user.getRoomName());
        room.leave(user);
        if (room.getParticipants().isEmpty()) {
            roomManager.removeRoom(room);
        }
    }
}
