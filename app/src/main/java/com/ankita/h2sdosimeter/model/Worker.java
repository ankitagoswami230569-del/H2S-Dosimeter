package com.ankita.h2sdosimeter.model;

/**
 * Worker - represents a worker/user in the system.
 * Frontend phase: all data is mock/demo only.
 */
public class Worker {

    public enum Role {
        WORKER, SAFETY_OFFICER, ADMIN
    }

    private String workerId;
    private String name;
    private String email;
    private String department;
    private String assignedSite;
    private String contactNumber;
    private Role role;

    public Worker(String workerId, String name, String email,
                  String department, String assignedSite,
                  String contactNumber, Role role) {
        this.workerId = workerId;
        this.name = name;
        this.email = email;
        this.department = department;
        this.assignedSite = assignedSite;
        this.contactNumber = contactNumber;
        this.role = role;
    }

    // Getters
    public String getWorkerId() { return workerId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getDepartment() { return department; }
    public String getAssignedSite() { return assignedSite; }
    public String getContactNumber() { return contactNumber; }
    public Role getRole() { return role; }

    // Setters
    public void setWorkerId(String workerId) { this.workerId = workerId; }
    public void setName(String name) { this.name = name; }
    public void setEmail(String email) { this.email = email; }
    public void setDepartment(String department) { this.department = department; }
    public void setAssignedSite(String assignedSite) { this.assignedSite = assignedSite; }
    public void setContactNumber(String contactNumber) { this.contactNumber = contactNumber; }
    public void setRole(Role role) { this.role = role; }

    public String getInitials() {
        if (name == null || name.isEmpty()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return String.valueOf(parts[0].charAt(0)).toUpperCase();
        return (String.valueOf(parts[0].charAt(0)) + String.valueOf(parts[parts.length - 1].charAt(0))).toUpperCase();
    }
}
