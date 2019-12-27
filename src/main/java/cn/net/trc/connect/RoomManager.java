package cn.net.trc.connect;

import org.kurento.client.KurentoClient;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


public class RoomManager {

    private final ConcurrentMap<String, Room> rooms = new ConcurrentHashMap<>();


    @Autowired
    private KurentoClient kurento;


    public Room getRoom(String roomName) {
        Room room = rooms.get(roomName);

        if (room == null) {
            room = new Room(roomName, kurento.createMediaPipeline());
            rooms.put(roomName, room);
        }
        return room;
    }


    public void removeRoom(Room room) {
        this.rooms.remove(room.getName());
        room.close();
    }

}
