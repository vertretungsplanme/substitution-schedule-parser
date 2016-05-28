package me.vertretungsplan.objects.authentication;

import me.vertretungsplan.objects.credential.Credential;
import org.json.JSONObject;

public class NoAuthenticationData implements AuthenticationData {
    @Override
    public Class<? extends Credential> getCredentialType() {
        return null;
    }

    @Override
    public JSONObject getData() {
        return null;
    }
}
