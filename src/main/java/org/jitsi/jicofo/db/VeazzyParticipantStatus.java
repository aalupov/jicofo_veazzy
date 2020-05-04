/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jitsi.jicofo.db;

import java.util.Date;

/**
 *
 * @author micka_3tyuvpx
 */
public class VeazzyParticipantStatus {
    
    static public final String TABLE_PARTICIPANT_STATUS = "participant_status";
    
    public static final int PARTICIPANT_STATUS_INACTIVE = 0;
    public static final int PARTICIPANT_STATUS_ACTIVE = 1;
    
    static public final String COLUMN_ID = "id";
    static public final String COLUMN_JID = "jid";
    static public final String COLUMN_ROOM_NAME = "room_name";
    static public final String COLUMN_STATUS = "status";
    static public final String COLUMN_JOIN_DATE = "join_date";
    static public final String COLUMN_LEAVE_DATE = "leave_date";
    static public final String COLUMN_LEAVE_REASON = "leave_reason";
    
    static public final String REASON_LEFT = "LEFT";
    static public final String REASON_KICKED = "KICKED";
    static public final String REASON_BAN = "BAN";
    
    private String jid;
    private String roomName;
    private int status = PARTICIPANT_STATUS_ACTIVE;
    private Date joinDate;
    private Date leaveDate;
    private String leaveReason;

    public VeazzyParticipantStatus() {
    }
    
    public VeazzyParticipantStatus(String jid, String roomName, Date joinDate, int status) {
        this.jid = jid;
        this.roomName = roomName;
        this.joinDate = joinDate;
        this.status = status;
    }

    /**
     * @return the jid
     */
    public String getJid() {
        return jid;
    }

    /**
     * @param jid the jid to set
     */
    public void setJid(String jid) {
        this.jid = jid;
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
     * @return the participantStatus
     */
    public int getStatus() {
        return status;
    }

    /**
     * @param participantStatus the participantStatus to set
     */
    public void setStatus(int status) {
        this.status = status;
    }

    /**
     * @return the joinDate
     */
    public Date getJoinDate() {
        return joinDate;
    }

    /**
     * @param joinDate the joinDate to set
     */
    public void setJoinDate(Date joinDate) {
        this.joinDate = joinDate;
    }

    /**
     * @return the leaveDate
     */
    public Date getLeaveDate() {
        return leaveDate;
    }

    /**
     * @param leaveDate the leaveDate to set
     */
    public void setLeaveDate(Date leaveDate) {
        this.leaveDate = leaveDate;
    }

    /**
     * @return the leaveReason
     */
    public String getLeaveReason() {
        return leaveReason;
    }

    /**
     * @param leaveReason the leaveReason to set
     */
    public void setLeaveReason(String leaveReason) {
        this.leaveReason = leaveReason;
    }
    
}
