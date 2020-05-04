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
public class VeazzyRoomStatus {
    
    static public final String TABLE_ROOM_STATUS = "room_status";
    
    public static final int ROOM_STATUS_CLOSED = 0;
    public static final int ROOM_STATUS_OPENED = 1;
    
    static public final String COLUMN_ID = "id";
    static public final String COLUMN_NAME = "name";
    static public final String COLUMN_STATUS = "status";
    
    private String roomName;
    private int status = ROOM_STATUS_OPENED;

    public VeazzyRoomStatus() {
    }
    
    public VeazzyRoomStatus(String roomName, int status) {
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
    public int getStatus() {
        return status;
    }

    /**
     * @param status the status to set
     */
    public void setStatus(int status) {
        this.status = status;
    }
    
}
