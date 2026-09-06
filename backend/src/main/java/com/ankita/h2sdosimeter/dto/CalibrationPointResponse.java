package com.ankita.h2sdosimeter.dto;

import com.ankita.h2sdosimeter.entity.CalibrationPoint;
import java.time.LocalDateTime;

/** Response DTO for a CalibrationPoint. */
public class CalibrationPointResponse {

    private Long          id;
    private Double        colourDifference;
    private Double        knownPpmHr;
    private String        label;
    private String        calibrationVersion;
    private boolean       active;
    private String        addedBy;
    private LocalDateTime createdAt;

    public static CalibrationPointResponse from(CalibrationPoint p) {
        CalibrationPointResponse r = new CalibrationPointResponse();
        r.id                 = p.getId();
        r.colourDifference   = p.getColourDifference();
        r.knownPpmHr         = p.getKnownPpmHr();
        r.label              = p.getLabel();
        r.calibrationVersion = p.getCalibrationVersion();
        r.active             = p.isActive();
        r.addedBy            = p.getAddedBy();
        r.createdAt          = p.getCreatedAt();
        return r;
    }

    public Long          getId()                 { return id; }
    public Double        getColourDifference()   { return colourDifference; }
    public Double        getKnownPpmHr()         { return knownPpmHr; }
    public String        getLabel()              { return label; }
    public String        getCalibrationVersion() { return calibrationVersion; }
    public boolean       isActive()              { return active; }
    public String        getAddedBy()            { return addedBy; }
    public LocalDateTime getCreatedAt()          { return createdAt; }
}
