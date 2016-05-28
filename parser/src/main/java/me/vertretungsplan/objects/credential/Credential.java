package me.vertretungsplan.objects.credential;

import org.joda.time.DateTime;

public interface Credential {
    /**
     * @return Hash value for the credential data.
     * For two Credential objects a and b, the following relation should apply:
     * a.getHash().equals(b.getHash()) if and only if a.getLoginData().equals(b.getLoginData())
     */
    String getHash();

    String getSchoolId();

    String getScheduleId();

    boolean isValid();

    void setValid(boolean valid);

    DateTime getLastCheck();

    void setLastCheck(DateTime lastCheck);

    String getId();
}
