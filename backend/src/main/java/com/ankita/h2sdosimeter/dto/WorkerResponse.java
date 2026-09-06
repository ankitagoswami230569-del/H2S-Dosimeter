package com.ankita.h2sdosimeter.dto;

import com.ankita.h2sdosimeter.entity.Worker;
import java.time.LocalDateTime;

/** Response DTO for Worker — never exposes the internal numeric id. */
public class WorkerResponse {

    private String       workerId;
    private String       name;
    private String       email;
    private String       department;
    private String       assignedSite;
    private String       contactNumber;
    private String       role;
    private String       badgeId;
    private boolean      active;
    private LocalDateTime createdAt;

    public static WorkerResponse from(Worker w) {
        WorkerResponse r = new WorkerResponse();
        r.workerId      = w.getWorkerId();
        r.name          = w.getName();
        r.email         = w.getEmail();
        r.department    = w.getDepartment();
        r.assignedSite  = w.getAssignedSite();
        r.contactNumber = w.getContactNumber();
        r.role          = w.getRole() != null ? w.getRole().name() : null;
        r.badgeId       = w.getBadgeId();
        r.active        = w.isActive();
        r.createdAt     = w.getCreatedAt();
        return r;
    }

    public String getWorkerId()            { return workerId; }
    public String getName()                { return name; }
    public String getEmail()               { return email; }
    public String getDepartment()          { return department; }
    public String getAssignedSite()        { return assignedSite; }
    public String getContactNumber()       { return contactNumber; }
    public String getRole()                { return role; }
    public String getBadgeId()             { return badgeId; }
    public boolean isActive()              { return active; }
    public LocalDateTime getCreatedAt()    { return createdAt; }
}
