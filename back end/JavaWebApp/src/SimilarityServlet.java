import org.json.HTTP;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.logging.Logger;

/**
 * This controller is responsible for running the similarity search.
 */

@WebServlet(name = "Similarityservlet", urlPatterns = {"/similarity"})
public class SimilarityServlet extends HttpServlet {
    private static Logger logger = Logger.getLogger("classname");
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
            throws ServletException, IOException { ;

        response.setContentType("text/html;charset=UTF-8");

        try (PrintWriter out = response.getWriter()) {
            out.println("Please use the POST method!");
            //the get method didn't work with the volley request when implemented on android so i use the post instead
        } catch (Exception ex) {
            System.out.println("Error" + ex);
        }
    }

    private int getMax(ArrayList<Integer> list){
        int max = Integer.MIN_VALUE;
        for (Integer integer : list) {
            if (integer > max) {
                max = integer;
            }
        }
        return max;
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
        StringBuilder jb = new StringBuilder();
        String line;
        try {
            BufferedReader reader = request.getReader();
            while ((line = reader.readLine()) != null)
                jb.append(line);
        } catch (Exception e) { /*report an error*/ }

        logger.info("message post: "+jb.toString());

        JSONObject jsonObject;
        try {
            jsonObject =  new JSONObject(jb.toString());
        } catch (JSONException e) {
            // crash and burn
            throw new IOException("Error parsing JSON request string");
        }

        //get the data from request and compare them to get the suitable access point

        JSONArray requestWifis = jsonObject.getJSONArray("Wifis");
        JSONArray requestCells = jsonObject.getJSONArray("Cells");

        //open connection and get parameters first
        Connection conn;
        String url = "jdbc:mysql://localhost:3306/your-db"; //Database -> your-db
        String user = "root";
        String pass = "";
        try {
            Class.forName("com.mysql.jdbc.Driver");
            conn = DriverManager.getConnection(url, user, pass);
            // the mysql select statement
            String query = "SELECT * FROM groupeddata";
            // create the mysql insert preparedstatement
            PreparedStatement preparedStmt = conn.prepareStatement(query);
            ResultSet resultSet = preparedStmt.executeQuery();
            //score for getting the best access point
            ArrayList<Integer> scoreArray = new ArrayList<>();
            int count =0;
            ArrayList<String> bssids = new ArrayList<String>();//store the values we need
            ArrayList<String> urls = new ArrayList<String>();
            while (resultSet.next()) {
                bssids.add(resultSet.getString("router_bssid"));
                urls.add(resultSet.getString("image_url"));
                JSONArray databaseWifis = new JSONArray(resultSet.getString("Wifis"));
                ArrayList<String> databaseBssids = new ArrayList<String>();
                ArrayList<Integer> databaseWifiPowers= new ArrayList<Integer>();
                ArrayList<String> databaseSsids= new ArrayList<String>();
                for (int i=0;i<databaseWifis.length();i++){
                    databaseBssids.add(databaseWifis.getJSONObject(i).getString("bssid"));
                    databaseWifiPowers.add(databaseWifis.getJSONObject(i).getInt("power"));
                    databaseSsids.add(databaseWifis.getJSONObject(i).getString("ssid"));
                }

                JSONArray databaseCells = new JSONArray(resultSet.getString("Cells"));
                ArrayList<String> databaseCellids = new ArrayList<String>();
                ArrayList<Integer> databaseCellSignalStrengths= new ArrayList<Integer>();
                for (int i=0;i<databaseCells.length();i++){
                    databaseCellids.add(databaseCells.getJSONObject(i).getString("id"));
                    databaseCellSignalStrengths.add(databaseCells.getJSONObject(i).getInt("power"));
                }

                scoreArray.add(0);
                for (int i=0; i < requestWifis.length(); i++) {
                    JSONObject jsonWifiObject = requestWifis.getJSONObject(i);
                    String testBssid = jsonWifiObject.getString("bssid");
                    int testPower = jsonWifiObject.getInt("power");
                    if(databaseBssids.contains(testBssid)){
                        int value = scoreArray.get(count);
                        value += 10;
                        int powerWifi = databaseWifiPowers.get(databaseBssids.indexOf(testBssid));
                        int difference = Math.abs(powerWifi-testPower);
                        if(difference<30){
                            value += 10 - (difference/3);
                        }
                        scoreArray.set(count,value);
                    }
                }

                for (int i=0;i < requestCells.length();i++){
                    JSONObject jsonCellObject = requestCells.getJSONObject(i);
                    String testId = jsonCellObject.getString("id");
                    int testSignal = jsonCellObject.getInt("power");
                    if(databaseCellids.contains(testId)){
                        int value = scoreArray.get(count);
                        value += 5;
                        int signalCell = databaseCellSignalStrengths.get(databaseCellids.indexOf(testId));
                        int difference = Math.abs(signalCell-testSignal);
                        if(difference<30){
                            value += 5 - 0.5*(difference/3);
                        }
                        scoreArray.set(count,value);
                    }
                }
                //after each resultset we increase the count
                count++;
            }
            conn.close();

            //out.println(scoreArray);
            int max = getMax(scoreArray);
            int index = scoreArray.indexOf(max);

            //out.println("Router with: "+ bssids.get(index) +" and url: "+ urls.get(index));
            //create the JSON response
            response.setContentType("application/json");
            JSONObject responseJsonObject = new JSONObject();

            if(max == 0){
                responseJsonObject.put("bssid","There is no router to match your data.");
                responseJsonObject.put("image_url","No URL available");
            }else{
                responseJsonObject.put("bssid",bssids.get(index));
                responseJsonObject.put("image_url",urls.get(index));
            }

            PrintWriter out = response.getWriter();
            out.println(responseJsonObject);

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
