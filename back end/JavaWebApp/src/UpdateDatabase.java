import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.servlet.ServletContext;
import java.net.URL;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * This class is executed at the start of the tomcat server and periodically to
 * update the 'data' database and later the 'groupeddata' database
 */

class UpdateDatabase {

    private final CloseableHttpClient httpClient = HttpClients.createDefault();
    private static int size;
    private ServletContext ctx;
    Connection conn;

    UpdateDatabase(ServletContext servletContext) {
        ctx = servletContext;
        ctx.log("UpdateDatabase run!");
        size = DBGetCount();
        try {
            updateDataDB();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try{
            updateGroupedDataDb();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void updateDataDB() throws Exception {
        deleteTableData();
        insertNewData();
    }

    private void deleteTableData() throws Exception {
        Connection conn;
        //first connection to database/users to get the Urls
        String url = "jdbc:mysql://localhost:3306/your-db";
        String user = "root";
        String pass = "";
        try {
            Class.forName("com.mysql.jdbc.Driver");
            conn = DriverManager.getConnection(url, user, pass);
            //empty the data table
            String deleteQuery = "DELETE FROM data";
            PreparedStatement preparedStmt2 = conn.prepareStatement(deleteQuery);
            preparedStmt2.executeUpdate();
        } catch (Exception ex) {
            System.out.println("Error" + ex);
        }
        ctx.log("Data DB deleted!");
    }

    private void insertNewData() throws Exception {
        Connection conn;
        //first connection to database/users to get the Urls
        String url = "jdbc:mysql://localhost:3306/your-db";
        String user = "root";
        String pass = "";
        ArrayList<String> urlsOfDatabases = new ArrayList<>();
        ArrayList<String> apiKeys = new ArrayList<>();
        try {
            Class.forName("com.mysql.jdbc.Driver");
            conn = DriverManager.getConnection(url, user, pass);
            String query = " SELECT Url FROM users";
            PreparedStatement preparedStmt = conn.prepareStatement(query);
            ResultSet resultSet = preparedStmt.executeQuery();
            while (resultSet.next()) {
                URL new_url = new URL(resultSet.getString("Url"));
                String host = new_url.getHost();
                System.out.println("host is " + host);
                urlsOfDatabases.add("https://" + host + "/rest/routers?fetchchildren=true");
                String apiKey = new_url.getQuery();
                apiKey = apiKey.replaceAll("&apikey=", "");
                apiKeys.add(apiKey);
            }
        } catch (Exception ex) {
            System.out.println("Error" + ex);
        }
        //get the information from the urls
        for (int i = 0; i < urlsOfDatabases.size(); i++) {
            HttpGet request = new HttpGet(urlsOfDatabases.get(i));
            request.addHeader("x-apikey", apiKeys.get(i));
            request.addHeader("content-type", "application/json");
            request.addHeader("cache-control", "no-cache");

            JSONArray jsonarray;//array to store the values
            try (CloseableHttpResponse response = httpClient.execute(request)) {

                // Get HttpResponse Status
                System.out.println(response.getStatusLine().toString());
                //get headers
                HttpEntity entity = response.getEntity();
                Header headers = entity.getContentType();
                System.out.println(headers);
                // convert it to a String
                String result = EntityUtils.toString(entity);
                //and then to an array
                jsonarray = new JSONArray(result);
            }

            //compare the arraylength to the size of the database
            //if (size < jsonarray.length()) {
            //if smaller add the new values only
            for (int n = 0; n < jsonarray.length(); n++) {
                JSONObject obj = jsonarray.getJSONObject(n);
                //System.out.println(obj);

                //get wifis values of interest
                JSONArray myWifiJSONArray = new JSONArray();

                JSONArray jsonarrayWifis = obj.getJSONArray("wifis");
                for (int m = 0; m < jsonarrayWifis.length(); m++) {
                    JSONObject wifiObj = jsonarrayWifis.getJSONObject(m);
                    String wifiBssid = wifiObj.getString("Bssid");
                    String wifiSsid = wifiObj.getString("Ssid");
                    Integer wifiPower = wifiObj.getInt("Power");
                    JSONObject wifiObject = new JSONObject();//create object with values
                    wifiObject.put("bssid", wifiBssid);
                    wifiObject.put("ssid", wifiSsid);
                    wifiObject.put("power", wifiPower);
                    myWifiJSONArray.put(wifiObject);//add it to the array
                }
                //System.out.println("we have wifis :" + myWifiJSONArray);

                //get cells values of interest
                JSONArray myCellJSONArray = new JSONArray();

                JSONArray jsonarrayCells = obj.getJSONArray("cells");
                for (int m = 0; m < jsonarrayCells.length(); m++) {
                    JSONObject wifiObj = jsonarrayCells.getJSONObject(m);
                    String cellId = wifiObj.getString("Cell-id");
                    Integer cellPower = wifiObj.getInt("Power");
                    JSONObject cellObject = new JSONObject();//create object with values
                    cellObject.put("id", cellId);
                    cellObject.put("power", cellPower);
                    myCellJSONArray.put(cellObject);
                }
                //System.out.println("we have cells :" + myCellJSONArray);

                String userId = obj.getString("userId");
                double latitude = obj.getDouble("GPS-latitude");
                double longitude = obj.getDouble("GPS-longitude");
                String bssid = obj.getString("router-bssid");
                String image_url = obj.getString("photo");

                //System.out.println("we have :" + "  " + imsi+ "  " +imei+ "  " +latitude+ "  " +longitude+ "  " +bssid+ "  " +image_url);

                //store the data to the data table
                new databaseInsertEntry(userId, longitude, latitude, bssid, image_url, myWifiJSONArray, myCellJSONArray,ctx);
            }//end of array values
            //}//if statement (we don't do anything for else
        }
    }

    private void updateGroupedDataDb() throws Exception {
        //empty the grouped data table
        deleteTableGroupedData();
        //fill the groupeddata DB with the average data of Wifis and Cells
        createGroups();
    }

    private void deleteTableGroupedData() throws Exception {
        Connection conn;
        //first connection to database/users to get the Urls
        String url = "jdbc:mysql://localhost:3306/your-db";
        String user = "root";
        String pass = "";
        try {
            Class.forName("com.mysql.jdbc.Driver");
            conn = DriverManager.getConnection(url, user, pass);
            //empty the data table
            String deleteQuery = "DELETE FROM groupeddata";
            PreparedStatement preparedStmt2 = conn.prepareStatement(deleteQuery);
            preparedStmt2.executeUpdate();
        } catch (Exception ex) {
            System.out.println("Error" + ex);
        }
        ctx.log("GroupedData DB deleted!");
    }

    private void createGroups() throws Exception {
        Connection conn;
        //first connection to database/users to get the Urls
        String url = "jdbc:mysql://localhost:3306/your-db";
        String user = "root";
        String pass = "";
        try {
            Class.forName("com.mysql.jdbc.Driver");
            conn = DriverManager.getConnection(url, user, pass);
            //execute an SQL command to group our data based on the common bssid
            String query = "SELECT router_bssid,image_url,GROUP_CONCAT(Wifis) AS aggregatedWifis,GROUP_CONCAT(Cells) as aggregatedCells FROM `data` GROUP BY router_bssid";
            PreparedStatement preparedStmt = conn.prepareStatement(query);
            ResultSet resultSet = preparedStmt.executeQuery();

            //read the query
            while (resultSet.next()) {
                //read the wifi data and extract the data
                String aggregatedWifis = resultSet.getString("aggregatedWifis");
                JSONArray myWifiJSONArray = new JSONArray();//store the new values here
                //if the Wifis have more than one array that means that the GROUP_CONCAT worked and we need to take these values and apply some average metric
                if (aggregatedWifis.contains("],[")) {
                    // Split the aggregatedWifis String to multiple JSON arrays
                    String[] parts = aggregatedWifis.split(Pattern.quote("],"));
                    ArrayList<JSONArray> arrayJSONWifis = new ArrayList<>();
                    for (int i = 0; i < parts.length; i++) {
                        String part = parts[i];
                        if (i != parts.length - 1) {
                            part += "]";//we add the bracket that the split removed
                            //couldn't find a better way to do it :(
                        }
                        arrayJSONWifis.add(new JSONArray(part));//add each JSON array to the arraylist
                    }
                    //for each array get the wifis (for later editing)
                    ArrayList<Wifi> wifiObject = new ArrayList<>();//get each wifi in the array
                    for (JSONArray jsonarray : arrayJSONWifis) {
                        for (int i = 0; i < jsonarray.length(); i++) {
                            String bssidExtracted = jsonarray.getJSONObject(i).getString("bssid");
                            String ssidExtracted = jsonarray.getJSONObject(i).getString("ssid");
                            int powerExtracted = jsonarray.getJSONObject(i).getInt("power");
                            Wifi wifiExtracted = new Wifi();
                            wifiExtracted.setBssid(bssidExtracted);
                            wifiExtracted.setSsid(ssidExtracted);
                            wifiExtracted.setPower(powerExtracted);
                            wifiObject.add(wifiExtracted);
                        }
                    }

                    //group by the bssids of each wifi
                    Map<String, List<Wifi>> groupedMap = wifiObject.stream().collect(Collectors.groupingBy(Wifi::getbssid));
                    ArrayList<Wifi> newWifiObject = new ArrayList<>();//store the aggregated values
                    for (Map.Entry<String, List<Wifi>> entry : groupedMap.entrySet()) {
                        String k = entry.getKey();
                        List<Wifi> v = entry.getValue();

                        //compute metric here
                        //average
                        int averagePower = 0;
                        for (Wifi wifi : v) {
                            averagePower += wifi.getPower();
                        }
                        averagePower = averagePower / v.size();
                        Wifi newAggregatedWifi = new Wifi();
                        newAggregatedWifi.setBssid(k);
                        newAggregatedWifi.setSsid(v.get(0).getSsid());
                        newAggregatedWifi.setPower(averagePower);
                        newWifiObject.add(newAggregatedWifi);
                    }
                    //we now have an arraylist of Wifis with new values (average here)
                    //we create now the JSON array of the values to store them to the database

                    for (Wifi wifi : newWifiObject) {
                        JSONObject storeWifiObject = new JSONObject();//create object with values
                        storeWifiObject.put("bssid", wifi.getbssid());
                        storeWifiObject.put("ssid", wifi.getSsid());
                        storeWifiObject.put("power", wifi.getPower());
                        myWifiJSONArray.put(storeWifiObject);
                    }
                    //we now have an array with the new list of Wifis
                }

                //same process with aggregatedCells
                String aggregatedCells = resultSet.getString("aggregatedCells");
                JSONArray myCellJSONArray = new JSONArray();//store the final Cell values here
                //if the Cells have more than one array that means that the GROUP_CONCAT worked and we need to take these values and apply some average metric
                if (aggregatedCells.contains("],[")) {
                    // Split the aggregatedCells String to multiple JSON arrays
                    String[] parts = aggregatedCells.split(Pattern.quote("],"));
                    ArrayList<JSONArray> arrayJSONCells = new ArrayList<>();
                    for (int i = 0; i < parts.length; i++) {
                        String part = parts[i];
                        if (i != parts.length - 1) {
                            part += "]";//we add the bracket that the split removed
                            //couldn't find a better way to do it :(
                        }
                        arrayJSONCells.add(new JSONArray(part));//add each JSON array to the arraylist
                    }
                    //for each array get the cells (for later editing) and store them to an object
                    ArrayList<Cell> cellObject = new ArrayList<>();//get each cell in the array
                    for (JSONArray jsonarray : arrayJSONCells) {
                        for (int i = 0; i < jsonarray.length(); i++) {
                            String idExtracted = jsonarray.getJSONObject(i).getString("id");
                            int powerExtracted = jsonarray.getJSONObject(i).getInt("power");
                            Cell cellExtracted = new Cell();
                            cellExtracted.setId(idExtracted);
                            cellExtracted.setPower(powerExtracted);
                            cellObject.add(cellExtracted);
                        }
                    }

                    //group by the ids of each cell
                    Map<String, List<Cell>> groupedMap = cellObject.stream().collect(Collectors.groupingBy(Cell::getId));
                    ArrayList<Cell> newCellObject = new ArrayList<>();//store the aggregated values
                    for (Map.Entry<String, List<Cell>> entry : groupedMap.entrySet()) {
                        String k = entry.getKey();//the id that we did the grouping
                        List<Cell> v = entry.getValue();//the whole cell object

                        /***
                         * compute metric here
                         */

                        /***
                         * compute average and keep this value
                         */
                        int averagePower = 0;
                        for (Cell cell : v) {
                            averagePower += cell.getPower();
                        }
                        averagePower = averagePower / v.size();
                        Cell newAggregatedCell = new Cell();
                        newAggregatedCell.setId(k);
                        newAggregatedCell.setPower(averagePower);
                        newCellObject.add(newAggregatedCell);


                        /***
                         * compute median
                         * then dismiss all values with 20db distance
                         * and then compute average
                         */
                        /*
                        int median;
                        int[] powerArray = new int[v.size()];
                        for (int i=0;i<=v.size();i++) {
                            powerArray[i] = v.get(i).getPower();
                        }
                        Arrays.sort(powerArray);
                        if(powerArray.length % 2 ==0){
                            median = (powerArray[powerArray.length/2] + powerArray[powerArray.length/2 -1]) /2;
                        }else{
                            median = powerArray[powerArray.length/2];
                        }
                        int averagePower = 0;
                        for (Cell cell : v) {
                            if( (cell.getPower()<median+20) || (cell.getPower()>median-20) ){
                                averagePower += cell.getPower();
                            }
                        }
                        averagePower = averagePower / v.size();
                        Cell newAggregatedCell = new Cell();
                        newAggregatedCell.setId(k);
                        newAggregatedCell.setPower(averagePower);
                        newCellObject.add(newAggregatedCell);
                         */


                    }
                    //we now have an arraylist of Cells with new values (average here)
                    //we store them to the JSON array myCellJSONArray

                    for (Cell cell : newCellObject) {
                        JSONObject storeCellObject = new JSONObject();//create object to put values
                        storeCellObject.put("id", cell.getId());
                        storeCellObject.put("power", cell.getPower());
                        myCellJSONArray.put(storeCellObject);
                    }
                    //we now have an array with the new list of Cells
                }

                System.out.println("so far so good");
                //Now we must write the data to our other Database -> groupeddata

                String queryInsertGroupedData = " INSERT INTO groupeddata (router_bssid, image_url, Wifis, Cells)"
                        + " VALUES (?, ?, ?, ?)";

                // create the mysql insert preparedstatement
                PreparedStatement preparedStmt2 = conn.prepareStatement(queryInsertGroupedData);
                preparedStmt2.setString(1, resultSet.getString("router_bssid"));
                preparedStmt2.setString(2, resultSet.getString("image_url"));
                if(myWifiJSONArray.isEmpty()){
                    preparedStmt2.setString(3,resultSet.getString("aggregatedWifis"));
                }else{
                    preparedStmt2.setString(3, myWifiJSONArray.toString());
                }
                if(myCellJSONArray.isEmpty()){
                    preparedStmt2.setString(4,resultSet.getString("aggregatedCells"));
                }else{
                    preparedStmt2.setString(4, myCellJSONArray.toString());
                }

                // execute the preparedstatement
                preparedStmt2.executeUpdate();
                ctx.log("Entry added to GroupedData DB successfully!");
            }
            ctx.log("Insertion to groupeddata finished");
            conn.close();
        } catch (Exception ex) {
            System.out.println("Error" + ex);
        }
    }

    private Integer DBGetCount() {
        String url = "jdbc:mysql://localhost:3306/your-db";
        String user = "root";
        String pass = "";
        int count = 0;

        try {
            Class.forName("com.mysql.jdbc.Driver");
            Connection conn = DriverManager.getConnection(url, user, pass);
            Statement stmt = conn.createStatement();
            String query = " SELECT COUNT(*) AS total FROM data";
            ResultSet rs = stmt.executeQuery(query);
            //Extact result from ResultSet rs
            while (rs.next()) {
                count = rs.getInt("total");
                ctx.log("COUNT(*)=" + count);
            }
            // close ResultSet rs
            rs.close();
            conn.close();
            stmt.close();

        } catch (Exception ex) {
            ctx.log("Error" + ex);
        }
        return count;
    }
}
