/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jitsi.jicofo.db;

/**
 *
 * @author micka_3tyuvpx
 */
public class RoomStatus {
    
    static public final String TABLE_ROOM_STATUS = "room_status";
    
    static public final String COLUMN_ID = "id";
    static public final String COLUMN_NAME = "name";
    static public final String COLUMN_STATUS = "status";
    
    private String roomName;
    private Boolean status;

    public RoomStatus() {
    }
    
    public RoomStatus(String roomName, Boolean status) {
        this.roomName = roomName;
        this.status = status;
    }
    
    /**
     * @return the roomName
     */
    public String getRoomName() {
        return roomName;
    }

    /**
     * @param roomName the roomName to set
     */
    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    /**
     * @return the status
     */
    public Boolean getStatus() {
        return status;
    }

    /**
     * @param status the status to set
     */
    public void setStatus(Boolean status) {
        this.status = status;
    }
    
}
