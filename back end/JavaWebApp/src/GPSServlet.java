import org.json.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;

/**
 * This controller is responsible for running the gps search.
 */

@WebServlet(name = "Gpsservlet", urlPatterns = {"/gps"})
public class GPSServlet extends HttpServlet {

    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request  the servlet request.
     * @param response the servlet response.
     * @throws ServletException if a servlet-specific error occurs.
     * @throws IOException      if an I/O error occurs.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();

        double gps_longitude = Double.parseDouble(request.getParameter("longitude"));
        double gps_latitude = Double.parseDouble(request.getParameter("latitude"));
        Connection conn;
        String url = "jdbc:mysql://localhost:3306/your-db"; //Database -> your-db
        String user = "root";
        String pass = "";
        try {
            Class.forName("com.mysql.jdbc.Driver");
            conn = DriverManager.getConnection(url, user, pass);
            // the mysql select statement
            String query = "SELECT Gps_longitude,Gps_latitude,router_bssid,image_url FROM data";
            // create the mysql insert preparedstatement
            PreparedStatement preparedStmt = conn.prepareStatement(query);
            ResultSet resultSet = preparedStmt.executeQuery();
            // Create ArrayLists to store values
            ArrayList<String> bssids = new ArrayList<String>();
            ArrayList<String> urls = new ArrayList<String>();
            ArrayList<Double> latitudes = new ArrayList<Double>();
            ArrayList<Double> longitudes = new ArrayList<Double>();
            while (resultSet.next()) {
                bssids.add(resultSet.getString("router_bssid"));
                urls.add(resultSet.getString("image_url"));
                latitudes.add(resultSet.getDouble("Gps_latitude"));
                longitudes.add(resultSet.getDouble("Gps_longitude"));
            }
            conn.close();

            //test distance with the data sent
            double min = 10001;//maximum distance that can happen
            String resultBssid = "";
            String resultUrl = "";
            Double resultLongitude = 0d;
            Double resultLatitude = 0d;

            for (int i = 0; i < latitudes.size(); i++) {
                double distanceMeasured = calculateDistance(gps_latitude, latitudes.get(i), gps_longitude, longitudes.get(i));
                if (distanceMeasured < min) {
                    min = distanceMeasured;
                    resultBssid = bssids.get(i);
                    resultUrl = urls.get(i);
                    resultLongitude = longitudes.get(i);
                    resultLatitude = latitudes.get(i);
                }
            }

            JSONObject responseJSON = new JSONObject();
            responseJSON.put("latitude",resultLatitude);
            responseJSON.put("longitude",resultLongitude);
            if(!resultBssid.equals("")){
                responseJSON.put("bssid",resultBssid);
                responseJSON.put("image_url",resultUrl);
            }else{
                //this case happens when no data is available at all or at distance > min
                responseJSON.put("bssid","no bssid");
                responseJSON.put("image_url","no image");
            }

            out.println(responseJSON);
        } catch (Exception ex) {
            System.out.println("Error" + ex);
        }
    }

    private static double calculateDistance(double lat1, double lat2, double lon1,
                                            double lon2) {

        final int R = 6371; // Radius of the earth

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return (R * c * 1000);
    }


    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request  the servlet request.
     * @param response the servlet response.
     * @throws ServletException if a servlet-specific error occurs.
     * @throws IOException      if an I/O error occurs.
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");

        try (PrintWriter out = response.getWriter()) {
            out.println("Please use the GET method!");
        } catch (Exception ex) {
            System.out.println("Error" + ex);
        }
    }


    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "This servlet does the hard work.";
    }

}