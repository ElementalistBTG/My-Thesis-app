# My-Thesis-app
## With this system you can photograph a point of interest and then find it using wifi and cellular signals via its fingerprint

This repository consists of a front end which is an android app and a backend which needs to be set up in a local machine and using an online repository. Instructions below:

### For the front end
the only thing that needs to be changed for the app to work as intended is at "MyApplication.kt" the ip address of the server.

### For the back end
The main server runs at a local machine with XAMPP installed (including phpmyadmin, MySQL and Tomcat). The user servers (or just repositories) are created at restdb.io. Below are the steps to recreate the backend

1. There must be a MySQL database with name "your-db" with the following tables:

- table "users" with fields "userId" (type: varchar(20)) and "Url" (type: text)
- table "data" with fields "userId" (type: varchar(20)), Gps_longitude (type: double), Gps_latitude (type:double), router_bssid (type: varchar(17), "image_url" (type: text), "Wifis" (type: longtext), "Cells" (type: longtext)
- table "groupeddata" with fields router_bssid (type: varchar(17), "image_url" (type: text), "Wifis" (type: longtext), "Cells" (type: longtext)

2. The java code can be compiled to a .war file in intellij IDEA ultimate edition at the menu bar -> Build->Build Artifacts... then selecting JavaWebApp:war>Build . The existing .war file is available to be copied to the <wherever_you_have_installed_xampp>\xampp\tomcat\webapps

3. For the app to work correctly you must allow the external port 9090 from the router's settings to be mapped to the internal port 8080

4. For the user's online repository you can use restdb.io (as i have used) but there must be the following collections:
- collection "routers" with fields: "userId" (type:text), "GPS-latitude" (type:float_number), "GPS-longitude" (type:float_number), "router-bssid" (type:text), "photo" (type:image), "wifis" (type:wifis), "cells" (type: cells)
- collection "Cells" with fields "Cell-id" (type:text) and "Power" (type:text)
- collection "Wifis" with fields "Bssid" (type:text), "Ssid" (type:text), "Power" (type:text)

For example i had the the following databases "https://homedatabase2-4b1c.restdb.io" and "https://homedatabase-060e.restdb.io/" and in the login screen of the app i typed the following "https://homedatabase2-4b1c.restdb.io/media?&apikey=5dd0fa8964e7774913b6ed97" or "https://homedatabase-060e.restdb.io/media?&apikey=5dc5641764e7774913b6ea76".
There is always a default url but it may not work in the future so make sure you have created your own online repository. These repositories need to have an apikey with access to methods GET,POST

For a tutorial of using the app see the video below:
https://youtu.be/MCIE3K3IjZs
