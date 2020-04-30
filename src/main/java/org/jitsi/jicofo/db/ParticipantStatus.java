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
public class ParticipantStatus {
    
    static public final String TABLE_PARTICIPANT_STATUS = "participant_status";
    
    static public final String COLUMN_ID = "id";
    static public final String COLUMN_JID = "jid";
    static public final String COLUMN_ROOM_NAME = "room_name";
    static public final String COLUMN_ACTIVE = "active";
    static public final String COLUMN_JOIN_DATE = "join_date";
    static public final String COLUMN_LEAVE_DATE = "leave_date";
    static public final String COLUMN_LEAVE_REASON = "leave_reason";
    
    static public final String REASON_LEFT = "LEFT";
    static public final String REASON_KICKED = "KICKED";
    static public final String REASON_BAN = "BAN";
    
    private String jid;
    private String roomName;
    private Boolean active;
    private Date joinDate;
    private Date leaveDate;
    private String leaveReason;

    public ParticipantStatus() {
    }
    
    public ParticipantStatus(String jid, String roomName, Date joinDate, Boolean active) {
        this.jid = jid;
        this.roomName = roomName;
        this.joinDate = joinDate;
        this.active = active;
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
     * @return the active
     */
    public Boolean getActive() {
        return active;
    }

    /**
     * @param active the active to set
     */
    public void setActive(Boolean active) {
        this.active = active;
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
