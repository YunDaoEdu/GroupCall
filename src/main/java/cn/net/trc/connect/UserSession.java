package cn.net.trc.connect;

import com.google.gson.JsonObject;
import org.kurento.client.EventListener;
import org.kurento.client.IceCandidate;
import org.kurento.client.IceCandidateFoundEvent;
import org.kurento.client.MediaPipeline;
import org.kurento.client.WebRtcEndpoint;
import org.kurento.jsonrpc.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


public class UserSession implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(UserSession.class);

    private final String name;
    private final WebSocketSession session;

    private final MediaPipeline pipeline;

    private final String roomName;
    private final WebRtcEndpoint outgoingMedia;
    private final ConcurrentMap<String, WebRtcEndpoint> incomingMedia = new ConcurrentHashMap<>();

    public UserSession(final String name, String roomName, final WebSocketSession session,
                       MediaPipeline pipeline) {

        this.pipeline = pipeline;
        this.name = name;
        this.session = session;
        this.roomName = roomName;
        this.outgoingMedia = new WebRtcEndpoint.Builder(pipeline).build();

        this.outgoingMedia.addIceCandidateFoundListener(new EventListener<IceCandidateFoundEvent>() {

            @Override
            public void onEvent(IceCandidateFoundEvent event) {
                JsonObject response = new JsonObject();
                response.addProperty("id", "iceCandidate");
                response.addProperty("name", name);
                response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
                try {
                    synchronized (session) {
                        session.sendMessage(new TextMessage(response.toString()));
                    }
                } catch (IOException e) {
                    log.debug(e.getMessage());
                }
            }
        });
    }

    public WebRtcEndpoint getOutgoingWebRtcPeer() {
        return outgoingMedia;
    }

    public String getName() {
        return name;
    }

    public WebSocketSession getSession() {
        return session;
    }


    public String getRoomName() {
        return this.roomName;
    }

    public void receiveVideoFrom(UserSession sender, String sdpOffer) throws IOException {
        final String ipSdpAnswer = this.getEndpointForUser(sender).processOffer(sdpOffer);
        final JsonObject scParams = new JsonObject();
        scParams.addProperty("id", "receiveVideoAnswer");
        scParams.addProperty("name", sender.getName());
        scParams.addProperty("sdpAnswer", ipSdpAnswer);

        this.sendMessage(scParams);
        this.getEndpointForUser(sender).gatherCandidates();
    }

    private WebRtcEndpoint getEndpointForUser(final UserSession sender) {
        if (sender.getName().equals(name)) {
            return outgoingMedia;
        }

        WebRtcEndpoint incoming = incomingMedia.get(sender.getName());
        if (incoming == null) {
            incoming = new WebRtcEndpoint.Builder(pipeline).build();

            incoming.addIceCandidateFoundListener(new EventListener<IceCandidateFoundEvent>() {

                @Override
                public void onEvent(IceCandidateFoundEvent event) {
                    JsonObject response = new JsonObject();
                    response.addProperty("id", "iceCandidate");
                    response.addProperty("name", sender.getName());
                    response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
                    try {
                        synchronized (session) {
                            session.sendMessage(new TextMessage(response.toString()));
                        }
                    } catch (IOException e) {
                        log.debug(e.getMessage());
                    }
                }
            });

            incomingMedia.put(sender.getName(), incoming);
        }
        sender.getOutgoingWebRtcPeer().connect(incoming);

        return incoming;
    }

    public void cancelVideoFrom(final UserSession sender) {
        this.cancelVideoFrom(sender.getName());
    }

    public void cancelVideoFrom(final String senderName) {
        final WebRtcEndpoint incoming = incomingMedia.remove(senderName);

        incoming.release();
    }

    @Override
    public void close() throws IOException {
        for (final String remoteParticipantName : incomingMedia.keySet()) {
            final WebRtcEndpoint ep = this.incomingMedia.get(remoteParticipantName);

            ep.release();
        }

        outgoingMedia.release();
    }

    public void sendMessage(JsonObject message) throws IOException {
        synchronized (session) {
            session.sendMessage(new TextMessage(message.toString()));
        }
    }

    public void addCandidate(IceCandidate candidate, String name) {
        if (this.name.compareTo(name) == 0) {
            outgoingMedia.addIceCandidate(candidate);
        } else {
            WebRtcEndpoint webRtc = incomingMedia.get(name);
            if (webRtc != null) {
                webRtc.addIceCandidate(candidate);
            }
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {

        if (this == obj) {
            return true;
        }
        if (obj == null || !(obj instanceof UserSession)) {
            return false;
        }
        UserSession other = (UserSession) obj;
        boolean eq = name.equals(other.name);
        eq &= roomName.equals(other.roomName);
        return eq;
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + name.hashCode();
        result = 31 * result + roomName.hashCode();
        return result;
    }
}
