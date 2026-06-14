package io.mutator.samples;
import java.sql.*;
public class JDBCSample {
    public static ResultSet query(Connection conn, String userId) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE id = ?");
        ps.setString(1, userId);
        return ps.executeQuery();
    }
}
