package cn.net.trc.connect.pojo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.kurento.client.MediaPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.PreDestroy;
import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


public class Room implements Closeable {
    private final Logger log = LoggerFactory.getLogger(Room.class);

    private final ConcurrentMap<String, UserSession> participants = new ConcurrentHashMap<>();
    private final MediaPipeline pipeline;
    private final String name;


    public Room(String roomName, MediaPipeline pipeline) {
        this.name = roomName;
        this.pipeline = pipeline;
    }


    /**
     * 用户加入房间
     */
    public UserSession join(String userName, WebSocketSession session) throws IOException {
        final UserSession participant = new UserSession(userName, this.name, session, this.pipeline);

        joinRoom(participant);

        participants.put(participant.getName(), participant);

        return participant;
    }


    /**
     * 用户离开房间
     */
    public void leave(UserSession user) throws IOException {
        this.removeParticipant(user.getName());
        user.close();
    }


    /**
     * 通知客户端有新成员加入
     */
    private void joinRoom(UserSession newParticipant) throws IOException {
        // 新用户加入通知
        final JsonObject newParticipantMsg = new JsonObject();
        newParticipantMsg.addProperty("id", "newParticipantArrived");
        newParticipantMsg.addProperty("name", newParticipant.getName());

        // 房间成员名单通知
        final JsonArray participantsArray = new JsonArray();

        for (final UserSession participant : participants.values()) {
            // 遍历老用户，通知老用户有新成员加入
            participant.sendMessage(newParticipantMsg);

            // 将老用户的名单组成数组
            final JsonElement participantName = new JsonPrimitive(participant.getName());
            participantsArray.add(participantName);
        }


        // 通知新成员该房间现在的成员名单
        final JsonObject existingParticipantsMsg = new JsonObject();
        existingParticipantsMsg.addProperty("id", "existingParticipants");
        existingParticipantsMsg.addProperty("name", newParticipant.getName());
        existingParticipantsMsg.add("data", participantsArray);
        newParticipant.sendMessage(existingParticipantsMsg);
    }


    /**
     * 用户离开房间，从房间成员里移除用户
     */
    private void removeParticipant(String name) {
        participants.remove(name);

        final JsonObject participantLeftJson = new JsonObject();
        participantLeftJson.addProperty("id", "participantLeft");
        participantLeftJson.addProperty("name", name);
        for (final UserSession participant : participants.values()) {
            try {
                participant.cancelVideoFrom(name);
                participant.sendMessage(participantLeftJson);
            } catch (final IOException e) {
                log.error(e.toString());
            }
        }

    }



    /**
     * 获取房间所有成员姓名
     */
    public Collection<UserSession> getParticipants() {
        return participants.values();
    }



    public String getName() {
        return name;
    }

    @PreDestroy
    private void shutdown() {
        this.close();
    }

    @Override
    public void close() {
        for (final UserSession user : participants.values()) {
            try {
                user.close();
            } catch (IOException e) {
                log.debug("ROOM {}: Could not invoke close on participant {}", this.name, user.getName(), e);
            }
        }

        participants.clear();

        pipeline.release();
    }

}
