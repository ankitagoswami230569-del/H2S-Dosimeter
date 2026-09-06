package com.ankita.h2sdosimeter.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating or updating a Worker.
 */
public class WorkerRequest {

    @NotBlank(message = "Worker ID is required")
    @Size(max = 50)
    private String workerId;

    @NotBlank(message = "Name is required")
    @Size(max = 120)
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 150)
    private String email;

    @Size(max = 100)
    private String department;

    @Size(max = 100)
    private String assignedSite;

    @Size(max = 30)
    private String contactNumber;

    private String role; // WORKER, SAFETY_OFFICER, ADMIN

    @Size(max = 50)
    private String badgeId;

    public String getWorkerId()            { return workerId; }
    public void setWorkerId(String v)      { this.workerId = v; }
    public String getName()                { return name; }
    public void setName(String v)          { this.name = v; }
    public String getEmail()               { return email; }
    public void setEmail(String v)         { this.email = v; }
    public String getDepartment()          { return department; }
    public void setDepartment(String v)    { this.department = v; }
    public String getAssignedSite()        { return assignedSite; }
    public void setAssignedSite(String v)  { this.assignedSite = v; }
    public String getContactNumber()       { return contactNumber; }
    public void setContactNumber(String v) { this.contactNumber = v; }
    public String getRole()                { return role; }
    public void setRole(String v)          { this.role = v; }
    public String getBadgeId()             { return badgeId; }
    public void setBadgeId(String v)       { this.badgeId = v; }
}
