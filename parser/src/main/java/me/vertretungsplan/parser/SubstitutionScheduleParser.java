package me.vertretungsplan.parser;

import me.vertretungsplan.exception.CredentialInvalidException;
import me.vertretungsplan.objects.SubstitutionSchedule;
import me.vertretungsplan.objects.credential.Credential;
import org.json.JSONException;

import java.io.IOException;

public interface SubstitutionScheduleParser {
    SubstitutionSchedule getSubstitutionSchedule() throws IOException, JSONException, CredentialInvalidException;
    void setCredential(Credential credential);
    Credential getCredential();
}
