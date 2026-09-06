package com.ankita.h2sdosimeter.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Request body for adding or updating a calibration point. */
public class CalibrationPointRequest {

    @NotNull(message = "colourDifference is required")
    @DecimalMin(value = "0.0", message = "colourDifference must be >= 0")
    @DecimalMax(value = "441.0", message = "colourDifference must be <= 441")
    private Double colourDifference;

    @NotNull(message = "knownPpmHr is required")
    @PositiveOrZero(message = "knownPpmHr must be >= 0")
    private Double knownPpmHr;

    @Size(max = 300)
    private String label;

    @Size(max = 50)
    private String calibrationVersion; // defaults to "default" if omitted

    @Size(max = 100)
    private String addedBy;

    public Double getColourDifference()        { return colourDifference; }
    public void setColourDifference(Double v)  { this.colourDifference = v; }
    public Double getKnownPpmHr()              { return knownPpmHr; }
    public void setKnownPpmHr(Double v)        { this.knownPpmHr = v; }
    public String getLabel()                   { return label; }
    public void setLabel(String v)             { this.label = v; }
    public String getCalibrationVersion()      { return calibrationVersion; }
    public void setCalibrationVersion(String v){ this.calibrationVersion = v; }
    public String getAddedBy()                 { return addedBy; }
    public void setAddedBy(String v)           { this.addedBy = v; }
}
