package com.ankita.h2sdosimeter.model;

/**
 * Shift - represents a work shift.
 * Frontend phase: mock data only.
 */
public class Shift {

    private String shiftId;
    private String shiftName;
    private String date;
    private String startTime;
    private String endTime;
    private String location;

    public Shift(String shiftId, String shiftName, String date,
                 String startTime, String endTime, String location) {
        this.shiftId = shiftId;
        this.shiftName = shiftName;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.location = location;
    }

    public String getShiftId() { return shiftId; }
    public String getShiftName() { return shiftName; }
    public String getDate() { return date; }
    public String getStartTime() { return startTime; }
    public String getEndTime() { return endTime; }
    public String getLocation() { return location; }

    public void setShiftId(String shiftId) { this.shiftId = shiftId; }
    public void setShiftName(String shiftName) { this.shiftName = shiftName; }
    public void setDate(String date) { this.date = date; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    public void setLocation(String location) { this.location = location; }
}
