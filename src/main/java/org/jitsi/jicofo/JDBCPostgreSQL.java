package org.jitsi.jicofo;

import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.ResultSet;
import java.util.logging.Level;
import java.util.logging.Logger;


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




     public Boolean getStatusFromDB(String roomName) {

        String query = "SELECT status FROM room_status WHERE name='"+roomName+"' LIMIT 1";

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

 

    public void setStatusToDB(String roomName, Boolean roomStatus) {

        String query = "INSERT INTO room_status(id, name, status) VALUES (DEFAULT,'"+roomName+"', "+roomStatus+")";

         try (Connection con = DriverManager.getConnection(DB_URL,USER,PASS)) {

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


    public void updateStatusToDB(String roomName, Boolean roomStatus) {

        String query = "UPDATE room_status SET status="+roomStatus+"  WHERE name='"+roomName+"'";

         try (Connection con = DriverManager.getConnection(DB_URL,USER,PASS)) {

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
}

