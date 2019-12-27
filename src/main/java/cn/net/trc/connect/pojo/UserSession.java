package cn.net.trc.connect.pojo;

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
    private final String roomName;

    private final WebSocketSession session;
    private final MediaPipeline pipeline;
    private final WebRtcEndpoint outgoingMedia;

    private final ConcurrentMap<String, WebRtcEndpoint> incomingMedia = new ConcurrentHashMap<>();

    public UserSession(final String name, String roomName, final WebSocketSession session, MediaPipeline pipeline) {

        this.pipeline = pipeline;
        this.name = name;
        this.session = session;
        this.roomName = roomName;
        this.outgoingMedia = new WebRtcEndpoint.Builder(pipeline).build();

        this.outgoingMedia.addIceCandidateFoundListener(new MyIceFoundListener(session, name));
    }


    /**
     * 从某用户处接收视频
     */
    public void receiveVideoFrom(UserSession sender, String sdpOffer) throws IOException {
        final String ipSdpAnswer = this.getEndpointForUser(sender).processOffer(sdpOffer);
        final JsonObject scParams = new JsonObject();
        scParams.addProperty("id", "receiveVideoAnswer");
        scParams.addProperty("name", sender.getName());
        scParams.addProperty("sdpAnswer", ipSdpAnswer);

        this.sendMessage(scParams);
        this.getEndpointForUser(sender).gatherCandidates();
    }


    /**
     * 为某用户获取EndPoint
     */
    private WebRtcEndpoint getEndpointForUser(final UserSession sender) {
        final String senderName = sender.getName();

        if (senderName.equals(name)) {
            return outgoingMedia;
        }

        WebRtcEndpoint incoming = incomingMedia.get(senderName);
        if (incoming == null) {
            incoming = new WebRtcEndpoint.Builder(pipeline).build();

            incoming.addIceCandidateFoundListener(new MyIceFoundListener(this.session, senderName));

            incomingMedia.put(sender.getName(), incoming);
        }
        sender.getOutgoingWebRtcPeer().connect(incoming);

        return incoming;
    }


    /**
     * 取消从某用户接收视频
     */
    public void cancelVideoFrom(final String senderName) {
        final WebRtcEndpoint incoming = incomingMedia.remove(senderName);

        incoming.release();
    }


    /**
     * 添加IceCandidate
     */
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


    /**
     * 发送信息至客户端
     */
    public void sendMessage(JsonObject message) throws IOException {
        synchronized (session) {
            session.sendMessage(new TextMessage(message.toString()));
        }
    }


    /**
     * Ice查找监听
     */
    private static class MyIceFoundListener implements EventListener<IceCandidateFoundEvent> {
        private final WebSocketSession session;
        private final String senderName;


        public MyIceFoundListener(WebSocketSession session, String senderName) {
            this.session = session;
            this.senderName = senderName;
        }

        @Override
        public void onEvent(IceCandidateFoundEvent event) {
            JsonObject response = new JsonObject();
            response.addProperty("id", "iceCandidate");
            response.addProperty("name", senderName);
            response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));

            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage(response.toString()));
                }
            } catch (IOException e) {
                log.error(e.toString());
            }
        }
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


    @Override
    public void close() throws IOException {
        for (final String remoteParticipantName : incomingMedia.keySet()) {
            final WebRtcEndpoint ep = this.incomingMedia.get(remoteParticipantName);
            ep.release();
        }

        outgoingMedia.release();
    }


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


    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + name.hashCode();
        result = 31 * result + roomName.hashCode();
        return result;
    }
}
