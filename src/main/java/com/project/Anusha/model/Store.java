package com.project.Anusha.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "stores")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Store {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String label;

    @Column(name = "display_order")
    private Integer displayOrder = 0;   

    private Boolean active = true; 

    @Column(name = "image_url")
    private String imageUrl;   

    private String address;
    private String city;
    private String pincode;

    @Column(unique = true)
    private String phoneNumber;

    private String priceRange;
    private String timings;

    @Column(length = 500)
    private String announcement;

    private String delivery;
    private String packageCost;
    private Double rating;
    private Integer preferredOrder = 0;

    /** GPS coordinates stored in MySQL for proximity queries */
    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
}