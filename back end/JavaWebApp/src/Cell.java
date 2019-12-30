/**
 * Helper Data class for cells
 */
public class Cell {
    private String id;
    private int power;

    // Getters
    public String getId() {
        return id;
    }
    public Integer getPower() {
        return power;
    }

    // Setters
    public void setId(String newId) {
        this.id = newId;
    }
    public void setPower(Integer newPower) {
        this.power = newPower;
    }
}
