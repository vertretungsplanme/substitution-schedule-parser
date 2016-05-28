package me.vertretungsplan.objects.authentication;

import me.vertretungsplan.objects.credential.Credential;
import org.json.JSONException;
import org.json.JSONObject;

public interface AuthenticationData {
    Class<? extends Credential> getCredentialType();
    JSONObject getData() throws JSONException;
}
