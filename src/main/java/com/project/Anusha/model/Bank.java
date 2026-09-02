package com.project.Anusha.model;

import jakarta.persistence.*;

@Entity
@Table(name = "banks")
public class Bank {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "short_code")
    private String shortCode;

    @Column(name = "ifsc_prefix", length = 4)
    private String ifscPrefix;

    public Bank() {}

    public Bank(String name, String shortCode, String ifscPrefix) {
        this.name = name;
        this.shortCode = shortCode;
        this.ifscPrefix = ifscPrefix;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getShortCode() { return shortCode; }
    public String getIfscPrefix() { return ifscPrefix; }

    public void setId(Long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setShortCode(String shortCode) { this.shortCode = shortCode; }
    public void setIfscPrefix(String ifscPrefix) { this.ifscPrefix = ifscPrefix; }
}
