/**
 * Helper Data class for wifis
 */

class Wifi {
    private String bssid;
    private String ssid;
    private int power;

    // Getters
    public String getbssid() {
        return bssid;
    }
    public String getSsid() {
        return ssid;
    }
    public Integer getPower() {
        return power;
    }

    // Setters
    public void setBssid(String newBssid) {
        this.bssid = newBssid;
    }
    public void setSsid(String newSsid) {
        this.ssid = newSsid;
    }
    public void setPower(Integer newPower) {
        this.power = newPower;
    }
}
