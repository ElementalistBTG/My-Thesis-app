import org.json.JSONArray;

import javax.servlet.ServletContext;
import java.io.PrintWriter;
import java.sql.*;

/**
 * Class for inserting data into the data DB
 */
class databaseInsertEntry {

    private Connection conn;
    private Statement stmt;
    private ResultSet rs;

    databaseInsertEntry(String userId, double longitude, double latitude, String bssid, String imageUrl, JSONArray wifis, JSONArray cells, ServletContext ctx) {
        String url = "jdbc:mysql://localhost:3306/your-db";
        String user = "root";
        String pass = "";
        try {
            Class.forName("com.mysql.jdbc.Driver");
            conn = DriverManager.getConnection(url, user, pass);

            String query = " INSERT INTO data (userId, Gps_longitude, Gps_latitude, router_bssid, image_url, Wifis, Cells)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?)";

            // create the mysql insert preparedstatement
            PreparedStatement preparedStmt = conn.prepareStatement(query);
            preparedStmt.setString(1, userId);
            preparedStmt.setDouble(2, longitude);
            preparedStmt.setDouble(3, latitude);
            preparedStmt.setString(4, bssid);
            preparedStmt.setString(5, imageUrl);
            preparedStmt.setString(6, wifis.toString());
            preparedStmt.setString(7, cells.toString());
            // execute the preparedstatement
            preparedStmt.executeUpdate();
            //System.out.println("executed correctly");
            conn.close();

            ctx.log("Successfully Inserted entry to data DB");

        } catch (Exception ex) {
            //System.out.println("Error" + ex);
            ctx.log("Error" + ex);
        }
    }



}
