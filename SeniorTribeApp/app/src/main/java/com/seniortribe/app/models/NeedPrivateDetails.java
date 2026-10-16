package com.seniortribe.app.models;

import java.io.Serializable;

public class NeedPrivateDetails implements Serializable {
    private String needId;
    private String elderlyName;
    private String exactAddress;
    private String phoneContact;
    private String healthNotes;
    private String consentEvidenceUrl;

    public NeedPrivateDetails() {}

    public String getNeedId() { return needId; }
    public void setNeedId(String needId) { this.needId = needId; }

    public String getElderlyName() { return elderlyName; }
    public void setElderlyName(String elderlyName) { this.elderlyName = elderlyName; }

    public String getExactAddress() { return exactAddress; }
    public void setExactAddress(String exactAddress) { this.exactAddress = exactAddress; }

    public String getPhoneContact() { return phoneContact; }
    public void setPhoneContact(String phoneContact) { this.phoneContact = phoneContact; }

    public String getHealthNotes() { return healthNotes; }
    public void setHealthNotes(String healthNotes) { this.healthNotes = healthNotes; }

    public String getConsentEvidenceUrl() { return consentEvidenceUrl; }
    public void setConsentEvidenceUrl(String consentEvidenceUrl) { this.consentEvidenceUrl = consentEvidenceUrl; }
}
