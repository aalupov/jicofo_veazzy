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
import org.jitsi.jicofo.db.VeazzyParticipantStatus;
import org.jitsi.jicofo.db.VeazzyRoomStatus;

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

    public VeazzyRoomStatus getRoomStatusFromDB(String roomName) {

        int queryLimit = 1;
        String queryString = "SELECT " + VeazzyRoomStatus.COLUMN_STATUS + " FROM " + VeazzyRoomStatus.TABLE_ROOM_STATUS
                + " WHERE " + VeazzyRoomStatus.COLUMN_NAME + "=" + getStringValue(roomName);
        if (queryLimit > 0) {
            queryString += " LIMIT " + String.valueOf(queryLimit);
        }

        VeazzyRoomStatus roomStatus = null;
        Connection connection = getConnection();
        Statement statement = getReadStatement(connection);

        if (statement != null) {

            try {

                ResultSet resulSet = statement.executeQuery(queryString);
                if (resulSet != null) {
                    try {
                        while (resulSet.next()) {
                            int status = resulSet.getInt(VeazzyRoomStatus.COLUMN_STATUS);
                            roomStatus = new VeazzyRoomStatus(roomName, status);
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

    public void insertRoomStatusToDB(VeazzyRoomStatus roomStatus) {

        String queryString = "INSERT INTO " + VeazzyRoomStatus.TABLE_ROOM_STATUS + "("
                + VeazzyRoomStatus.COLUMN_NAME + ", "
                + VeazzyRoomStatus.COLUMN_STATUS + ")"
                + " VALUES (?,?)";

        Connection connection = getConnection();
        PreparedStatement pStatement = getWriteCreateStatement(connection, queryString);
        if(pStatement != null) {
            try {
                pStatement.setString(1, roomStatus.getRoomName());
                pStatement.setInt(2, roomStatus.getStatus());
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

    public void updateRoomStatusToDB(VeazzyRoomStatus roomStatus) {

        String queryString = "UPDATE " + VeazzyRoomStatus.TABLE_ROOM_STATUS + " SET "
                + VeazzyRoomStatus.COLUMN_STATUS + "= ?"
                + " WHERE " + VeazzyRoomStatus.COLUMN_NAME + "=" + getStringValue(roomStatus.getRoomName());

        Connection connection = getConnection();
        PreparedStatement pStatement = getWriteUpdateStatement(connection, queryString);
        if(pStatement != null) {
            try {
                pStatement.setInt(1, roomStatus.getStatus());
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

    private VeazzyParticipantStatus getParticantStatusFromDB(String participantJid) {

        int queryLimit = 1;
        String queryString = "SELECT " + "*" + " FROM " + VeazzyParticipantStatus.TABLE_PARTICIPANT_STATUS
                + " WHERE " + VeazzyParticipantStatus.COLUMN_JID + "=" + getStringValue(participantJid);
        if (queryLimit > 0) {
            queryString += " LIMIT " + String.valueOf(queryLimit);
        }

        VeazzyParticipantStatus participantStatus = null;
        Connection connection = getConnection();
        Statement statement = getReadStatement(connection);

        if (statement != null) {

            try {

                ResultSet resulSet = statement.executeQuery(queryString);
                if (resulSet != null) {
                    try {
                        while (resulSet.next()) {
                            
                            String roomName = resulSet.getString(VeazzyParticipantStatus.COLUMN_ROOM_NAME);
                            Date joinDate = resulSet.getDate(VeazzyParticipantStatus.COLUMN_JOIN_DATE);
                            int status = resulSet.getInt(VeazzyParticipantStatus.COLUMN_STATUS);
                            
                            Date leaveDate = resulSet.getDate(VeazzyParticipantStatus.COLUMN_LEAVE_DATE);
                            String leaveReason = resulSet.getString(VeazzyParticipantStatus.COLUMN_LEAVE_REASON);
                            
                            participantStatus = new VeazzyParticipantStatus(participantJid, roomName, joinDate, status);
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
    
    private void insertParticipantStatusToDB(VeazzyParticipantStatus participantStatus) {

        String queryString = "INSERT INTO " + VeazzyParticipantStatus.TABLE_PARTICIPANT_STATUS + "("
                + VeazzyParticipantStatus.COLUMN_JID + ", "
                + VeazzyParticipantStatus.COLUMN_ROOM_NAME + ", "
                + VeazzyParticipantStatus.COLUMN_STATUS + ", "
                + VeazzyParticipantStatus.COLUMN_JOIN_DATE + ", "
                + VeazzyParticipantStatus.COLUMN_LEAVE_DATE + ", "
                + VeazzyParticipantStatus.COLUMN_LEAVE_REASON + ")"
                + " VALUES (?,?,?,?,?,?)";

        Connection connection = getConnection();
        PreparedStatement pStatement = getWriteCreateStatement(connection, queryString);
        if(pStatement != null) {
            try {
                pStatement.setString(1, participantStatus.getJid());
                pStatement.setString(2, participantStatus.getRoomName());
                pStatement.setInt(3, participantStatus.getStatus());
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
        VeazzyParticipantStatus participantStatus = new VeazzyParticipantStatus(participantJid, roomName, 
                new Date(), VeazzyParticipantStatus.PARTICIPANT_STATUS_ACTIVE);
        insertParticipantStatusToDB(participantStatus);
        
    }
    public void participantLeavesRoom(String participantJid, String reason) {
        
        VeazzyParticipantStatus participantStatus = getParticantStatusFromDB(participantJid);
        
        if(participantStatus != null) {
            participantStatus.setStatus(VeazzyParticipantStatus.PARTICIPANT_STATUS_INACTIVE);
            participantStatus.setLeaveDate(new Date());
            participantStatus.setLeaveReason(reason);

            String queryString = "UPDATE " + VeazzyParticipantStatus.TABLE_PARTICIPANT_STATUS + " SET "
                    + VeazzyParticipantStatus.COLUMN_STATUS + "= ?" + ", "
                    + VeazzyParticipantStatus.COLUMN_LEAVE_DATE + "= ?" + ", "
                    + VeazzyParticipantStatus.COLUMN_LEAVE_REASON + "= ?"
                    + " WHERE " + VeazzyParticipantStatus.COLUMN_JID + "=" + getStringValue(participantStatus.getJid());

            Connection connection = getConnection();
            PreparedStatement pStatement = getWriteUpdateStatement(connection, queryString);
            
            if(pStatement != null) {
                try {
                    pStatement.setInt(1, participantStatus.getStatus());
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
