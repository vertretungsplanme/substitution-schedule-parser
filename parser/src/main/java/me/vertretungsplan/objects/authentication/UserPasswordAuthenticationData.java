package me.vertretungsplan.objects.authentication;

import me.vertretungsplan.objects.credential.Credential;
import me.vertretungsplan.objects.credential.UserPasswordCredential;
import org.json.JSONObject;

public class UserPasswordAuthenticationData implements AuthenticationData {
    @Override
    public Class<? extends Credential> getCredentialType() {
        return UserPasswordCredential.class;
    }

    @Override
    public JSONObject getData() {
        return null;
    }
}
