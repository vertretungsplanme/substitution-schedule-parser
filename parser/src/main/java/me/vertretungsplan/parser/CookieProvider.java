package me.vertretungsplan.parser;

import me.vertretungsplan.objects.credential.Credential;
import org.apache.http.cookie.Cookie;

import java.util.List;

public interface CookieProvider {
    List<Cookie> getCookies(Credential credential);

    void saveCookies(Credential credential, List<Cookie> cookies);
}
