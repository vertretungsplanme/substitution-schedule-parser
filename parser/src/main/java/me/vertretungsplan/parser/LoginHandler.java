/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.parser;

import me.vertretungsplan.exception.CredentialInvalidException;
import me.vertretungsplan.objects.SubstitutionScheduleData;
import me.vertretungsplan.objects.credential.Credential;
import me.vertretungsplan.objects.credential.PasswordCredential;
import me.vertretungsplan.objects.credential.UserPasswordCredential;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.jetbrains.annotations.Nullable;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Utility class used by most parsers to access schedules protected by a login page. This can be used with both
 * {@link me.vertretungsplan.objects.authentication.UserPasswordAuthenticationData} and
 * {@link me.vertretungsplan.objects.authentication.PasswordAuthenticationData}.
 * <p>
 * <code>LoginHandler</code> supports authentication using HTTP POST (using
 * <code>application/x-www-form-urlencoded</code>), HTTP Basic Auth, NTLM or a fixed (username and) password.
 *
 * <h4>Configuration parameters</h4>
 * These parameters can be supplied in a JSON Object under <code>login</code> in
 * {@link SubstitutionScheduleData#setData(JSONObject)} to configure the login:
 *
 * <dl>
 * <dt><code>type</code> (String, required)</dt>
 * <dd>The type of login. Can be one of <code>"post", "basic, "ntlm" or "fixed"</code></dd>
 * </dl>
 *
 * <h5>Parameters for HTTP POST (<code>"post"</code>)</h5>
 * <dl>
 * <dt><code>url</code> (String, required)</dt>
 * <dd>The URL that the HTTP POST data is sent to.</dd>
 *
 * <dt><code>data</code> (JSON Object, required)</dt>
 * <dd>The actual form data (in a <code>"key": "value"</code> fashion) sent in the HTTP POST. The value can be set to
 * <code>"_login"</code> (only for
 * {@link me.vertretungsplan.objects.authentication.UserPasswordAuthenticationData}) or <code>"_password"</code> to
 * insert the username and password, respectively. If you specify <code>preUrl</code> and add <code>"_hiddeninputs":
 * ""</code> here, the contents of all <code>&lt;input type="hidden"&gt;</code> fields on the page at
 * <code>preUrl</code> are inserted into the HTTP POST data.</dd>
 *
 * <dt><code>preUrl</code> (String, optional)</dt>
 * <dd>A URL that is opened before the login (such as the login form itself). This is only required if this sets a
 * Cookie that you need or if you need to send contents of hidden <code>input</code> fields located on this page.</dd>
 *
 * <dt><code>checkUrl</code> (String, optional)</dt>
 * <dd>A URL that is opened after the login to check if it was successful.</dd>
 *
 * <dt><code>checkText</code> (String, optional)</dt>
 * <dd>If this String is included in the HTML under <code>checkUrl</code>, the credential is considered invalid. If
 * checkUrl is not specified, the response from the login request is used (only for POST).
 * </dd>
 *
 * <dt><code>form-data</code> (Boolean, optional, Default: <code>false</code>)</dt>
 * <dd>Whether to use <code>multipart/form-data</code> instead of <code>application/x-www-form-urlencoded</code>.</dd>
 * </dl>
 *
 * <h5>Parameters for HTTP Basic Auth (<code>"basic"</code>) and NTLM (<code>"ntlm"</code>)</h5>
 * <dl>
 * <dt><code>url</code> (String, optional)</dt>
 * <dd>A URL that is opened after the login to check if it was successful. If the server responds with a status
 * code that is not <code>200</code>, the credential is considered invalid.</dd>
 * </dl>
 *
 * <h5>Parameters for fixed login (<code>"fixed"</code>)</h5>
 * <dl>
 * <dt><code>login</code> (String, required, only for
 * {@link me.vertretungsplan.objects.authentication.UserPasswordAuthenticationData})</dt>
 * <dd>The fixed username checked against the supplied one.</dd>
 *
 * <dl>
 * <dt><code>password</code> (String, required)</dt>
 * <dd>The fixed password checked against the supplied one.</dd>
 * </dl>
 */
public class LoginHandler {
    private static final String LOGIN_CONFIG = "login";
    private static final String PARAM_TYPE = "type";
    private static final String PARAM_PRE_URL = "preUrl";
    private static final String PARAM_URL = "url";
    private static final String PARAM_DATA = "data";
    private static final String PARAM_LOGIN = "login";
    private static final String PARAM_PASSWORD = "password";
    private static final String PARAM_CHECK_URL = "checkUrl";
    private static final String PARAM_CHECK_TEXT = "checkText";
    private SubstitutionScheduleData scheduleData;
    private Credential auth;
    private CookieProvider cookieProvider;

    LoginHandler(SubstitutionScheduleData scheduleData, Credential auth,
                 @Nullable CookieProvider cookieProvider) {
        this.scheduleData = scheduleData;
        this.auth = auth;
        this.cookieProvider = cookieProvider;
    }

    void handleLogin(Executor executor, CookieStore cookieStore)
            throws JSONException, IOException, CredentialInvalidException {
        handleLogin(executor, cookieStore, false);
    }

    String handleLoginWithResponse(Executor executor, CookieStore cookieStore)
            throws JSONException, IOException, CredentialInvalidException {
        return handleLogin(executor, cookieStore, true);
    }

    private String handleLogin(Executor executor, CookieStore cookieStore, boolean needsResponse) throws JSONException,
            IOException, CredentialInvalidException {
        if (auth == null) return null;
        if (!(auth instanceof UserPasswordCredential || auth instanceof PasswordCredential)) {
            throw new IllegalArgumentException("Wrong authentication type");
        }

        String login;
        String password;
        if (auth instanceof UserPasswordCredential) {
            login = ((UserPasswordCredential) auth).getUsername();
            password = ((UserPasswordCredential) auth).getPassword();
        } else {
            login = null;
            password = ((PasswordCredential) auth).getPassword();
        }

        JSONObject data = scheduleData.getData();
        JSONObject loginConfig = data.getJSONObject(LOGIN_CONFIG);
        String type = loginConfig.optString(PARAM_TYPE, "post");
        switch (type) {
            case "post":
                List<Cookie> cookieList = cookieProvider != null ? cookieProvider.getCookies(auth) : null;
                if (cookieList != null && !needsResponse) {
                    for (Cookie cookie : cookieList) cookieStore.addCookie(cookie);

                    String checkUrl = loginConfig.optString(PARAM_CHECK_URL, null);
                    String checkText = loginConfig.optString(PARAM_CHECK_TEXT, null);
                    if (checkUrl != null && checkText != null) {
                        String response = executor.execute(Request.Get(checkUrl)).returnContent().asString();
                        if (!response.contains(checkText)) {
                            return null;
                        }
                    } else {
                        return null;
                    }
                }
                executor.clearCookies();
                Document preDoc = null;
                if (loginConfig.has(PARAM_PRE_URL)) {
                    String preUrl = loginConfig.getString(PARAM_PRE_URL);
                    String preHtml = executor.execute(Request.Get(preUrl)).returnContent().asString();
                    preDoc = Jsoup.parse(preHtml);
                }

                String postUrl = loginConfig.getString(PARAM_URL);
                JSONObject loginData = loginConfig.getJSONObject(PARAM_DATA);
                List<NameValuePair> nvps = new ArrayList<>();

                String typo3Challenge = null;
                BigInteger typo3RsaN = null;
                BigInteger typo3RsaE = null;

                if (loginData.has("_hiddeninputs") && preDoc != null) {
                    for (Element hidden : preDoc.select(loginData.getString("_hiddeninputs") +
                            " input[type=hidden]")) {
                        nvps.add(new BasicNameValuePair(hidden.attr("name"), hidden.attr("value")));
                        if (hidden.attr("name").equals("challenge")) {
                            typo3Challenge = hidden.attr("value");
                        } else if (hidden.attr("name").equals("n") && hidden.attr("id").equals("rsa_n")) {
                            typo3RsaN = new BigInteger(hidden.attr("value"), 16);
                        } else if (hidden.attr("name").equals("e") && hidden.attr("id").equals("rsa_e")) {
                            typo3RsaE = new BigInteger(hidden.attr("value"), 16);
                        }
                    }
                }

                for (String name : JSONObject.getNames(loginData)) {
                    String value = loginData.getString(name);

                    if (name.equals("_hiddeninputs")) continue;

                    switch (value) {
                        case "_login":
                            value = login;
                            break;
                        case "_password":
                            value = password;
                            break;
                        case "_password_md5":
                            value = DigestUtils.md5Hex(password);
                            break;
                        case "_password_md5_typo3":
                            value = DigestUtils
                                    .md5Hex(login + ":" + DigestUtils.md5Hex(password) + ":" + typo3Challenge);
                            break;
                        case "_password_rsa_typo3":
                            try {
                                final Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                                cipher.init(Cipher.ENCRYPT_MODE, KeyFactory.getInstance("RSA").generatePublic
                                        (new RSAPublicKeySpec(typo3RsaN, typo3RsaE)));
                                byte[] result = cipher.doFinal(password.getBytes());
                                value = "rsa:" + new Base64().encodeAsString(result);
                            } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                                    BadPaddingException | IllegalBlockSizeException | InvalidKeySpecException e) {
                                e.printStackTrace();
                            }

                            break;
                    }

                    nvps.add(new BasicNameValuePair(name, value));
                }
                Request request = Request.Post(postUrl);
                if (loginConfig.optBoolean("form-data", false)) {
                    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
                    for (NameValuePair nvp : nvps) {
                        builder.addTextBody(nvp.getName(), nvp.getValue());
                    }
                    request.body(builder.build());
                } else {
                    request.bodyForm(nvps, Charset.forName("UTF-8"));
                }
                String html = executor.execute(request).returnContent().asString();
                if (cookieProvider != null) cookieProvider.saveCookies(auth, cookieStore.getCookies());

                String checkUrl = loginConfig.optString(PARAM_CHECK_URL, null);
                String checkText = loginConfig.optString(PARAM_CHECK_TEXT, null);
                if (checkUrl != null && checkText != null) {
                    String response = executor.execute(Request.Get(checkUrl)).returnContent().asString();
                    if (response.contains(checkText)) throw new CredentialInvalidException();
                } else if (checkText != null) {
                    if (html.contains(checkText)) throw new CredentialInvalidException();
                }
                return html;
            case "basic":
                if (login == null) throw new IOException("wrong auth type");
                executor.auth(login, password);
                if (loginConfig.has(PARAM_URL)) {
                    String url = loginConfig.getString(PARAM_URL);
                    if (executor.execute(Request.Get(url)).returnResponse().getStatusLine().getStatusCode() != 200) {
                        throw new CredentialInvalidException();
                    }
                }
                break;
            case "ntlm":
                if (login == null) throw new IOException("wrong auth type");
                executor.auth(login, password, null, null);
                if (loginConfig.has(PARAM_URL)) {
                    String url = loginConfig.getString(PARAM_URL);
                    if (executor.execute(Request.Get(url)).returnResponse().getStatusLine().getStatusCode() != 200) {
                        throw new CredentialInvalidException();
                    }
                }
                break;
            case "fixed":
                String loginFixed = loginConfig.optString(PARAM_LOGIN, null);
                String passwordFixed = loginConfig.getString(PARAM_PASSWORD);
                if (!Objects.equals(loginFixed, login) || !Objects.equals(passwordFixed, password)) {
                    throw new CredentialInvalidException();
                }
                break;
        }
        return null;
    }
}
