package com.seniortribe.app.models;

import java.io.Serializable;

public class Organization implements Serializable {
    private String orgId;
    private String name;
    private String address;
    private String phone;
    private boolean verified;

    public Organization() {}

    public Organization(String orgId, String name, String address, String phone, boolean verified) {
        this.orgId = orgId;
        this.name = name;
        this.address = address;
        this.phone = phone;
        this.verified = verified;
    }

    public String getOrgId() { return orgId; }
    public void setOrgId(String orgId) { this.orgId = orgId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }
}
