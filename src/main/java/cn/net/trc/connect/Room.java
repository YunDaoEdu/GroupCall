package cn.net.trc.connect;

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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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

    public String getName() {
        return name;
    }

    @PreDestroy
    private void shutdown() {
        this.close();
    }

    public UserSession join(String userName, WebSocketSession session) throws IOException {
        final UserSession participant = new UserSession(userName, this.name, session, this.pipeline);
        joinRoom(participant);
        participants.put(participant.getName(), participant);
        sendParticipantNames(participant);
        return participant;
    }

    public void leave(UserSession user) throws IOException {
        this.removeParticipant(user.getName());
        user.close();
    }

    private Collection<String> joinRoom(UserSession newParticipant) throws IOException {
        final JsonObject newParticipantMsg = new JsonObject();
        newParticipantMsg.addProperty("id", "newParticipantArrived");
        newParticipantMsg.addProperty("name", newParticipant.getName());

        final List<String> participantsList = new ArrayList<>(participants.values().size());

        for (final UserSession participant : participants.values()) {
            try {
                participant.sendMessage(newParticipantMsg);
            } catch (final IOException e) {
                log.debug("ROOM {}: participant {} could not be notified", name, participant.getName(), e);
            }
            participantsList.add(participant.getName());
        }

        return participantsList;
    }

    private void removeParticipant(String name) throws IOException {
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

    public void sendParticipantNames(UserSession user) throws IOException {

        final JsonArray participantsArray = new JsonArray();
        for (final UserSession participant : this.getParticipants()) {
            if (!participant.equals(user)) {
                final JsonElement participantName = new JsonPrimitive(participant.getName());
                participantsArray.add(participantName);
            }
        }

        final JsonObject existingParticipantsMsg = new JsonObject();
        existingParticipantsMsg.addProperty("id", "existingParticipants");
        existingParticipantsMsg.add("data", participantsArray);
        user.sendMessage(existingParticipantsMsg);
    }

    public Collection<UserSession> getParticipants() {
        return participants.values();
    }

    public UserSession getParticipant(String name) {
        return participants.get(name);
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
