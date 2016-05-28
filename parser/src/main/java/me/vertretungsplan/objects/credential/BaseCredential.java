package me.vertretungsplan.objects.credential;

import org.apache.commons.codec.binary.Base64;
import org.joda.time.DateTime;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public abstract class BaseCredential implements Credential {
    private String id;
    private String schoolId;
    private String scheduleId;
    private boolean valid = true;
    private DateTime lastCheck;

    @Override
    public String getSchoolId() {
        return schoolId;
    }

    public void setSchoolId(String schoolId) {
        this.schoolId = schoolId;
    }

    @SuppressWarnings({"EmptyMethod", "UnusedParameters"})
    public void setHash(String hash) {
        // TODO: This needs to be there so that Jackson can deserialize it. Is there a better way?
    }

    @Override
    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    protected String hash(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data.getBytes());
            return Base64.encodeBase64URLSafeString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public DateTime getLastCheck() {
        return lastCheck;
    }

    public void setLastCheck(DateTime lastCheck) {
        this.lastCheck = lastCheck;
    }

    public String getScheduleId() {
        return scheduleId;
    }

    public void setScheduleId(String scheduleId) {
        this.scheduleId = scheduleId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
