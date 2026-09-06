package com.ankita.h2sdosimeter.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Worker entity — a person who wears an H2S dosimeter badge.
 *
 * workerId is a human-readable identifier (e.g. WRK-001) assigned at
 * registration. It is separate from the auto-generated database id so
 * the Android app can use it as a stable external key.
 */
@Entity
@Table(name = "workers",
       indexes = {
           @Index(name = "idx_worker_worker_id",  columnList = "worker_id",  unique = true),
           @Index(name = "idx_worker_badge_id",   columnList = "badge_id"),
           @Index(name = "idx_worker_email",      columnList = "email", unique = true)
       })
public class Worker {

    public enum Role { WORKER, SAFETY_OFFICER, ADMIN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "worker_id", nullable = false, unique = true, length = 50)
    @NotBlank
    @Size(max = 50)
    private String workerId;

    @Column(nullable = false, length = 120)
    @NotBlank
    @Size(max = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 150)
    @NotBlank
    @Email
    @Size(max = 150)
    private String email;

    @Column(length = 100)
    private String department;

    @Column(name = "assigned_site", length = 100)
    private String assignedSite;

    @Column(name = "contact_number", length = 30)
    private String contactNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.WORKER;

    /** The badge currently assigned to this worker (nullable — badge may not be assigned). */
    @Column(name = "badge_id", length = 50)
    private String badgeId;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Bidirectional: one worker has many scan records
    @OneToMany(mappedBy = "worker", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<ScanRecord> scanRecords;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // -------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------

    public Worker() {}

    public Worker(String workerId, String name, String email,
                  String department, String assignedSite,
                  String contactNumber, Role role, String badgeId) {
        this.workerId      = workerId;
        this.name          = name;
        this.email         = email;
        this.department    = department;
        this.assignedSite  = assignedSite;
        this.contactNumber = contactNumber;
        this.role          = role;
        this.badgeId       = badgeId;
    }

    // -------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------

    public Long getId()                     { return id; }
    public String getWorkerId()             { return workerId; }
    public void setWorkerId(String v)       { this.workerId = v; }
    public String getName()                 { return name; }
    public void setName(String v)           { this.name = v; }
    public String getEmail()                { return email; }
    public void setEmail(String v)          { this.email = v; }
    public String getDepartment()           { return department; }
    public void setDepartment(String v)     { this.department = v; }
    public String getAssignedSite()         { return assignedSite; }
    public void setAssignedSite(String v)   { this.assignedSite = v; }
    public String getContactNumber()        { return contactNumber; }
    public void setContactNumber(String v)  { this.contactNumber = v; }
    public Role getRole()                   { return role; }
    public void setRole(Role v)             { this.role = v; }
    public String getBadgeId()              { return badgeId; }
    public void setBadgeId(String v)        { this.badgeId = v; }
    public boolean isActive()               { return active; }
    public void setActive(boolean v)        { this.active = v; }
    public LocalDateTime getCreatedAt()     { return createdAt; }
    public LocalDateTime getUpdatedAt()     { return updatedAt; }
    public List<ScanRecord> getScanRecords(){ return scanRecords; }
}
