package com.project.Anusha.dto;

public class AdminAccessTimeRequest {
    private Long days;
    private Long hours;
    private Long minutes;
    private Long seconds;
 
    public Long getDays() { return days; }
    public void setDays(Long days) { this.days = days; }

    public Long getHours() { return hours; }
    public void setHours(Long hours) { this.hours = hours; }

    public Long getMinutes() { return minutes; }
    public void setMinutes(Long minutes) { this.minutes = minutes; }

    public Long getSeconds() { return seconds; }
    public void setSeconds(Long seconds) { this.seconds = seconds; }
}
