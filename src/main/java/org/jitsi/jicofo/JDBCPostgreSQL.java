package org.jitsi.jicofo;

import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jitsi.jicofo.db.ParticipantStatus;
import org.jitsi.jicofo.db.RoomStatus;

public class JDBCPostgreSQL {

    //  Database credentials
    static final String DB_URL = "jdbc:postgresql://127.0.0.1:5432/veazzy";
    static final String USER = "veazzy";
    static final String PASS = "veazzyauthpass";

    public static void main(String[] argv) {

        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return;
        }

        Connection connection = null;

        try {
            connection = DriverManager
                    .getConnection(DB_URL, USER, PASS);

        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }
    }

    public Connection getConnection() {

        Connection connection = null;

        try {
            connection = DriverManager
                    .getConnection(DB_URL, USER, PASS);

        } catch (SQLException ex) {

            Logger lgr = Logger.getLogger(JDBCPostgreSQL.class.getName());
            lgr.log(Level.SEVERE, ex.getMessage(), ex);
            return null;
        }
        return connection;
    }

    public Statement getReadStatement(Connection connection) {

        Statement statement = null;

        if (connection != null) {

            try {

                //https://stackoverflow.com/questions/1468036/java-jdbc-ignores-setfetchsize
                /*connection.setAutoCommit(false);
                
                Statement statement = connection.createStatement(
                        ResultSet.TYPE_FORWARD_ONLY,
                        ResultSet.CONCUR_READ_ONLY,
                        ResultSet.FETCH_FORWARD);
                
                statement.setFetchSize(1000);*/
                statement = connection.createStatement(
                        ResultSet.TYPE_FORWARD_ONLY,
                        ResultSet.CONCUR_READ_ONLY);

            } catch (SQLException ex) {
                Logger.getLogger(JDBCPostgreSQL.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return statement;
    }

    public PreparedStatement getWriteCreateStatement(Connection connection, String queryString) {

        PreparedStatement pStatement = null;

        if (connection != null) {

            try {

                pStatement = connection.prepareStatement(queryString, Statement.RETURN_GENERATED_KEYS);
                connection.setAutoCommit(false);

            } catch (SQLException ex) {
                Logger.getLogger(JDBCPostgreSQL.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return pStatement;
    }

    public PreparedStatement getWriteUpdateStatement(Connection connection, String queryString) {

        PreparedStatement pStatement = null;

        if (connection != null) {

            try {

                pStatement = connection.prepareStatement(queryString);
                connection.setAutoCommit(false);

            } catch (SQLException ex) {
                Logger.getLogger(JDBCPostgreSQL.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return pStatement;
    }
    
    public void executePreparedQueryToDb(Connection connection, PreparedStatement pStatement) {

        if (pStatement != null) {

            try {
                int counts[] = pStatement.executeBatch();
                connection.commit();
            } catch (SQLException ex) {

                if (connection != null) {
                    try {
                        connection.rollback();
                    } catch (SQLException ex1) {
                        Logger.getLogger(JDBCPostgreSQL.class.getName()).log(Level.WARNING, ex1.getMessage(), ex1);
                    }
                }
                Logger.getLogger(JDBCPostgreSQL.class.getName()).log(Level.WARNING, ex.getMessage(), ex);
            }
        }
    }
    
    /*public void executeQueryToDb(Connection connection, Statement statement, String queryString) {

        if (statement != null) {

            try {
                statement.addBatch(queryString);
                int counts[] = statement.executeBatch();
                connection.commit();
            } catch (SQLException ex) {

                if (connection != null) {
                    try {
                        connection.rollback();
                    } catch (SQLException ex1) {
                        Logger.getLogger(JDBCPostgreSQL.class.getName()).log(Level.WARNING, ex1.getMessage(), ex1);
                    }
                }
                Logger.getLogger(JDBCPostgreSQL.class.getName()).log(Level.WARNING, ex.getMessage(), ex);
            }
        }
    }*/
    
    private static java.sql.Date getCurrentDate() {
        java.util.Date today = new java.util.Date();
        return new java.sql.Date(today.getTime());
    }
    
    private static java.sql.Timestamp getCurrentTimestamp() {
        java.util.Date today = new java.util.Date();
        return new java.sql.Timestamp(today.getTime());
    }
    
    private static java.sql.Timestamp getTimestamp(java.util.Date date) {
        return new java.sql.Timestamp(date.getTime());
    }
    
    public String getStringValue(String value) {
        return  "'" + value + "'";
    }

    public RoomStatus getRoomStatusFromDB(String roomName) {

        int queryLimit = 1;
        String queryString = "SELECT " + RoomStatus.COLUMN_STATUS + " FROM " + RoomStatus.TABLE_ROOM_STATUS
                + " WHERE " + RoomStatus.COLUMN_NAME + "=" + getStringValue(roomName);
        if (queryLimit > 0) {
            queryString += " LIMIT " + String.valueOf(queryLimit);
        }

        RoomStatus roomStatus = null;
        Connection connection = getConnection();
        Statement statement = getReadStatement(connection);

        if (statement != null) {

            try {

                ResultSet resulSet = statement.executeQuery(queryString);
                if (resulSet != null) {
                    try {
                        while (resulSet.next()) {
                            Boolean status = resulSet.getBoolean(RoomStatus.COLUMN_STATUS);
                            roomStatus = new RoomStatus(roomName, status);
                        }
                    } catch (SQLException ex) {
                        Logger.getLogger(JDBCPostgreSQL.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            } catch (SQLException ex) {
                Logger.getLogger(JDBCPostgreSQL.class.getName()).log(Level.SEVERE, null, ex);
            }
            try {
                statement.close();
            } catch (SQLException ex) {
                Logger.getLogger(JDBCPostgreSQL.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        if(connection != null) {
            try {
                connection.close();
            } catch (SQLException ex) {
                Logger.getLogger(JDBCPostgreSQL.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        return roomStatus;
    }

    public void insertRoomStatusToDB(RoomStatus roomStatus) {

        String queryString = "INSERT INTO " + RoomStatus.TABLE_ROOM_STATUS + "("
                + RoomStatus.COLUMN_NAME + ", "
                + RoomStatus.COLUMN_STATUS + ")"
                + " VALUES (?,?)";

        Connection connection = getConnection();
        PreparedStatement pStatement = getWriteCreateStatement(connection, queryString);
        if(pStatement != null) {
            try {
                pStatement.setString(1, roomStatus.getRoomName());
                pStatement.setBoolean(2, roomStatus.getStatus());
                pStatement.addBatch();
            } catch (SQLException ex) {
                Logger.getLogger(JDBCPostgreSQL.class.getName()).log(Level.SEVERE, null, ex);
            }
            executePreparedQueryToDb(connection, pStatement);
            try {
                pStatement.close();
            } catch (SQLException ex) {
                Logger.getLogger(JDBCPostgreSQL.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        if(connection != null) {
            try {
                connection.close();
            } catch (SQLException ex) {
                Logger.getLogger(JDBCPostgreSQL.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public void updateRoomStatusToDB(RoomStatus roomStatus) {

        String queryString = "UPDATE " + RoomStatus.TABLE_ROOM_STATUS + " SET "
                + RoomStatus.COLUMN_STATUS + "= ?"
                + " WHERE " + RoomStatus.COLUMN_NAME + "=" + getStringValue(roomStatus.getRoomName());

        Connection connection = getConnection();
        PreparedStatement pStatement = getWriteUpdateStatement(connection, queryString);
        if(pStatement != null) {
            try {
                pStatement.setBoolean(1, roomStatus.getStatus());
                pStatement.addBatch();
            } catch (SQLException ex) {
                Logger.getLogger(JDBCPostgreSQL.class.getName()).log(Level.SEVERE, null, ex);
            }
            executePreparedQueryToDb(connection, pStatement);
            try {
                pStatement.close();
            } catch (SQLException ex) {
                Logger.getLogger(JDBCPostgreSQL.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        if(connection != null) {
            try {
                connection.close();
            } catch (SQLException ex) {
                Logger.getLogger(JDBCPostgreSQL.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private ParticipantStatus getParticantStatusFromDB(String participantJid) {

        int queryLimit = 1;
        String queryString = "SELECT " + "*" + " FROM " + ParticipantStatus.TABLE_PARTICIPANT_STATUS
                + " WHERE " + ParticipantStatus.COLUMN_JID + "=" + getStringValue(participantJid);
        if (queryLimit > 0) {
            queryString += " LIMIT " + String.valueOf(queryLimit);
        }

        ParticipantStatus participantStatus = null;
        Connection connection = getConnection();
        Statement statement = getReadStatement(connection);

        if (statement != null) {

            try {

                ResultSet resulSet = statement.executeQuery(queryString);
                if (resulSet != null) {
                    try {
                        while (resulSet.next()) {
                            
                            String roomName = resulSet.getString(ParticipantStatus.COLUMN_ROOM_NAME);
                            Date joinDate = resulSet.getDate(ParticipantStatus.COLUMN_JOIN_DATE);
                            Boolean active = resulSet.getBoolean(ParticipantStatus.COLUMN_ACTIVE);
                            
                            Date leaveDate = resulSet.getDate(ParticipantStatus.COLUMN_LEAVE_DATE);
                            String leaveReason = resulSet.getString(ParticipantStatus.COLUMN_LEAVE_REASON);
                            
                            participantStatus = new ParticipantStatus(participantJid, roomName, joinDate, active);
                            participantStatus.setLeaveDate(leaveDate);
                            participantStatus.setLeaveReason(leaveReason);
    
                        }
                    } catch (SQLException ex) {
                        Logger.getLogger(JDBCPostgreSQL.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            } catch (SQLException ex) {
                Logger.getLogger(JDBCPostgreSQL.class.getName()).log(Level.SEVERE, null, ex);
            }
            try {
                statement.close();
            } catch (SQLException ex) {
                Logger.getLogger(JDBCPostgreSQL.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        if(connection != null) {
            try {
                connection.close();
            } catch (SQLException ex) {
                Logger.getLogger(JDBCPostgreSQL.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return participantStatus;
    }
    
    private void insertParticipantStatusToDB(ParticipantStatus participantStatus) {

        String queryString = "INSERT INTO " + ParticipantStatus.TABLE_PARTICIPANT_STATUS + "("
                + ParticipantStatus.COLUMN_JID + ", "
                + ParticipantStatus.COLUMN_ROOM_NAME + ", "
                + ParticipantStatus.COLUMN_ACTIVE + ", "
                + ParticipantStatus.COLUMN_JOIN_DATE + ", "
                + ParticipantStatus.COLUMN_LEAVE_DATE + ", "
                + ParticipantStatus.COLUMN_LEAVE_REASON + ")"
                + " VALUES (?,?,?,?,?,?)";

        Connection connection = getConnection();
        PreparedStatement pStatement = getWriteCreateStatement(connection, queryString);
        if(pStatement != null) {
            try {
                pStatement.setString(1, participantStatus.getJid());
                pStatement.setString(2, participantStatus.getRoomName());
                pStatement.setBoolean(3, participantStatus.getActive());
                pStatement.setTimestamp(4, participantStatus.getJoinDate() != null ? getTimestamp(participantStatus.getJoinDate()) : null);
                pStatement.setTimestamp(5, participantStatus.getLeaveDate() != null ? getTimestamp(participantStatus.getLeaveDate()) : null);
                pStatement.setString(6, participantStatus.getLeaveReason());
                pStatement.addBatch();
            } catch (SQLException ex) {
                Logger.getLogger(JDBCPostgreSQL.class.getName()).log(Level.SEVERE, null, ex);
            }
            executePreparedQueryToDb(connection, pStatement);
            try {
                pStatement.close();
            } catch (SQLException ex) {
                Logger.getLogger(JDBCPostgreSQL.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        if(connection != null) {
            try {
                connection.close();
            } catch (SQLException ex) {
                Logger.getLogger(JDBCPostgreSQL.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public void participantEntersRoom(String participantJid, String roomName) {
        ParticipantStatus participantStatus = new ParticipantStatus(participantJid, roomName, new Date(), Boolean.TRUE);
        insertParticipantStatusToDB(participantStatus);
        
    }
    public void participantLeavesRoom(String participantJid, String reason) {
        
        ParticipantStatus participantStatus = getParticantStatusFromDB(participantJid);
        
        if(participantStatus != null) {
            participantStatus.setActive(Boolean.FALSE);
            participantStatus.setLeaveDate(new Date());
            participantStatus.setLeaveReason(reason);

            String queryString = "UPDATE " + ParticipantStatus.TABLE_PARTICIPANT_STATUS + " SET "
                    + ParticipantStatus.COLUMN_ACTIVE + "= ?" + ", "
                    + ParticipantStatus.COLUMN_LEAVE_DATE + "= ?" + ", "
                    + ParticipantStatus.COLUMN_LEAVE_REASON + "= ?"
                    + " WHERE " + ParticipantStatus.COLUMN_JID + "=" + getStringValue(participantStatus.getJid());

            Connection connection = getConnection();
            PreparedStatement pStatement = getWriteUpdateStatement(connection, queryString);
            
            if(pStatement != null) {
                try {
                    pStatement.setBoolean(1, participantStatus.getActive());
                    pStatement.setTimestamp(2, participantStatus.getLeaveDate() != null ? getTimestamp(participantStatus.getLeaveDate()) : null);
                    pStatement.setString(3, participantStatus.getLeaveReason());
                    pStatement.addBatch();
                } catch (SQLException ex) {
                    Logger.getLogger(JDBCPostgreSQL.class.getName()).log(Level.SEVERE, null, ex);
                }
                executePreparedQueryToDb(connection, pStatement);
                try {
                    pStatement.close();
                } catch (SQLException ex) {
                    Logger.getLogger(JDBCPostgreSQL.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            try {
                connection.close();
            } catch (SQLException ex) {
                Logger.getLogger(JDBCPostgreSQL.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
        
    /**
     * ***************************** OLD CODE ********************************
     */
    /*public Boolean getOldStatusFromDB(String roomName) {

        String query = "SELECT status FROM room_status WHERE name='" + roomName + "' LIMIT 1";

        try (Connection con = DriverManager.getConnection(DB_URL, USER, PASS);
                PreparedStatement pst = con.prepareStatement(query);
                ResultSet rs = pst.executeQuery()) {
            while (rs.next()) {
                return rs.getBoolean(1);
            }

        } catch (SQLException ex) {

            Logger lgr = Logger.getLogger(JDBCPostgreSQL.class.getName());
            lgr.log(Level.SEVERE, ex.getMessage(), ex);
        }
        return true;
    }

    public void setOldStatusToDB(String roomName, Boolean roomStatus) {

        String query = "INSERT INTO room_status(id, name, status) VALUES (DEFAULT,'" + roomName + "', " + roomStatus + ")";

        try (Connection con = DriverManager.getConnection(DB_URL, USER, PASS)) {

            try (Statement st = con.createStatement()) {
                con.setAutoCommit(false);
                st.addBatch(query);
                int counts[] = st.executeBatch();
                con.commit();
            } catch (SQLException ex) {

                if (con != null) {
                    try {
                        con.rollback();
                    } catch (SQLException ex1) {
                        Logger lgr = Logger.getLogger(
                                JDBCPostgreSQL.class.getName());
                        lgr.log(Level.WARNING, ex1.getMessage(), ex1);
                    }
                }

                Logger lgr = Logger.getLogger(
                        JDBCPostgreSQL.class.getName());
                lgr.log(Level.SEVERE, ex.getMessage(), ex);
            }

        } catch (SQLException ex) {

            Logger lgr = Logger.getLogger(JDBCPostgreSQL.class.getName());
            lgr.log(Level.SEVERE, ex.getMessage(), ex);
        }

    }

    public void updateOldStatusToDB(String roomName, Boolean roomStatus) {

        String query = "UPDATE room_status SET status=" + roomStatus + "  WHERE name='" + roomName + "'";

        try (Connection con = DriverManager.getConnection(DB_URL, USER, PASS)) {

            try (Statement st = con.createStatement()) {
                con.setAutoCommit(false);
                st.addBatch(query);
                int counts[] = st.executeBatch();
                con.commit();
            } catch (SQLException ex) {

                if (con != null) {
                    try {
                        con.rollback();
                    } catch (SQLException ex1) {
                        Logger lgr = Logger.getLogger(
                                JDBCPostgreSQL.class.getName());
                        lgr.log(Level.WARNING, ex1.getMessage(), ex1);
                    }
                }

                Logger lgr = Logger.getLogger(
                        JDBCPostgreSQL.class.getName());
                lgr.log(Level.SEVERE, ex.getMessage(), ex);
            }

        } catch (SQLException ex) {

            Logger lgr = Logger.getLogger(JDBCPostgreSQL.class.getName());
            lgr.log(Level.SEVERE, ex.getMessage(), ex);
        }

    }*/
}
