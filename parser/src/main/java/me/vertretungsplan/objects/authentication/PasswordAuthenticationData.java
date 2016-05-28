package me.vertretungsplan.objects.authentication;

import me.vertretungsplan.objects.credential.Credential;
import me.vertretungsplan.objects.credential.PasswordCredential;
import org.json.JSONException;
import org.json.JSONObject;

public class PasswordAuthenticationData implements AuthenticationData {
    @Override
    public Class<? extends Credential> getCredentialType() {
        return PasswordCredential.class;
    }

    @Override
    public JSONObject getData() throws JSONException {
        return null;
    }
}
