package me.vertretungsplan.parser;

import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import me.vertretungsplan.exception.CredentialInvalidException;
import me.vertretungsplan.objects.SubstitutionScheduleData;
import me.vertretungsplan.objects.credential.UserPasswordCredential;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.assertEquals;

public class LoginHandlerTest {
    @Rule
    public final WireMockRule wireMockRule = new WireMockRule(Options.DYNAMIC_PORT);

    private SubstitutionScheduleData dataBasic;
    private SubstitutionScheduleData dataBasicUrl;
    private SubstitutionScheduleData dataFixed;
    private SubstitutionScheduleData dataPost;
    private SubstitutionScheduleData dataPostCheckText;
    private SubstitutionScheduleData dataPostCheckUrl;
    private SubstitutionScheduleData dataPostFormData;
    
    private UserPasswordCredential wrong;
    private UserPasswordCredential correct;

    private String baseurl;

    @Before
    public void setUp() throws JSONException {
        baseurl = "http://localhost:" + wireMockRule.port();

        correct = new UserPasswordCredential("user", "password");
        wrong = new UserPasswordCredential("user", "wrong");

        JSONObject loginBasic = new JSONObject();
        loginBasic.put("type", "basic");
        dataBasic = getSubstitutionScheduleData(loginBasic);

        JSONObject loginBasicUrl = new JSONObject();
        loginBasicUrl.put("type", "basic");
        loginBasicUrl.put("url", baseurl + "/index.html");
        dataBasicUrl = getSubstitutionScheduleData(loginBasicUrl);

        JSONObject loginFixed = new JSONObject();
        loginFixed.put("type", "fixed");
        loginFixed.put("login", correct.getUsername());
        loginFixed.put("password", correct.getPassword());
        dataFixed = getSubstitutionScheduleData(loginFixed);

        JSONObject loginPost = new JSONObject();
        loginPost.put("type", "post");
        loginPost.put("url", baseurl + "/index.html");
        JSONObject postData = new JSONObject();
        postData.put("user", "_login");
        postData.put("password", "_password");
        loginPost.put("data", postData);
        dataPost = getSubstitutionScheduleData(loginPost);

        JSONObject loginPostCheckUrl = new JSONObject();
        loginPostCheckUrl.put("type", "post");
        loginPostCheckUrl.put("url", baseurl + "/index.html");
        loginPostCheckUrl.put("checkUrl", baseurl + "/index.html");
        loginPostCheckUrl.put("checkText", "please login");
        loginPostCheckUrl.put("data", postData);
        dataPostCheckUrl = getSubstitutionScheduleData(loginPostCheckUrl);

        JSONObject loginPostCheckText = new JSONObject();
        loginPostCheckText.put("type", "post");
        loginPostCheckText.put("url", baseurl + "/index.html");
        loginPostCheckText.put("checkText", "wrong");
        loginPostCheckText.put("data", postData);
        dataPostCheckText = getSubstitutionScheduleData(loginPostCheckText);

        JSONObject loginPostFormData = new JSONObject();
        loginPostFormData.put("type", "post");
        loginPostFormData.put("url", baseurl + "/index.html");
        loginPostFormData.put("form-data", true);
        loginPostFormData.put("data", postData);
        dataPostFormData = getSubstitutionScheduleData(loginPostFormData);
    }

    @Test
    public void testBasicAuth() throws JSONException, IOException, CredentialInvalidException {
        stubBasicAuth();
        Executor exec = newExecutor();

        LoginHandler handler = new LoginHandler(dataBasic, correct, null);
        handler.handleLogin(exec, null);

        // loading page should succeed
        int status = exec.execute(Request.Get(baseurl + "/index.html")).returnResponse().getStatusLine()
                .getStatusCode();
        assertEquals(200, status);
    }

    @Test
    public void testBasicAuthFail() throws JSONException, IOException, CredentialInvalidException {
        stubBasicAuth();
        Executor exec = newExecutor();

        LoginHandler handler = new LoginHandler(dataBasic, wrong, null);
        handler.handleLogin(exec, null);

        // loading page should succeed
        int status = exec.execute(Request.Get(baseurl + "/index.html")).returnResponse().getStatusLine()
                .getStatusCode();
        assertEquals(401, status);
    }

    @Test(expected = CredentialInvalidException.class)
    public void testBasicAuthFailUrl() throws JSONException, IOException, CredentialInvalidException {
        stubBasicAuth();
        Executor exec = newExecutor();

        LoginHandler handler = new LoginHandler(dataBasicUrl, wrong, null);
        handler.handleLogin(exec, null);
    }

    @Test
    public void testFixedAuth() throws JSONException, IOException, CredentialInvalidException {
        LoginHandler handler = new LoginHandler(dataFixed, correct, null);
        handler.handleLogin(newExecutor(), null);
    }

    @Test(expected = CredentialInvalidException.class)
    public void testFixedAuthFail() throws JSONException, IOException, CredentialInvalidException {
        LoginHandler handler = new LoginHandler(dataFixed, wrong, null);
        handler.handleLogin(newExecutor(), null);
    }

    @Test
    public void testPostAuth() throws JSONException, IOException, CredentialInvalidException {
        stubPostAuth();
        Executor exec = newExecutor();

        LoginHandler handler = new LoginHandler(dataPost, correct, null);
        handler.handleLogin(exec, null);

        // loading page should succeed
        String content = exec.execute(Request.Get(baseurl + "/index.html")).returnContent().asString();
        assertEquals("content", content);
    }

    @Test
    public void testPostAuthFail() throws JSONException, IOException, CredentialInvalidException {
        stubPostAuth();
        Executor exec = newExecutor();

        LoginHandler handler = new LoginHandler(dataPost, wrong, null);
        handler.handleLogin(exec, null);

        // loading page should fail
        String content = exec.execute(Request.Get(baseurl + "/index.html")).returnContent().asString();
        assertEquals("please login", content);
    }

    @Test(expected = CredentialInvalidException.class)
    public void testPostAuthFailCheckText() throws JSONException, IOException, CredentialInvalidException {
        stubPostAuth();
        Executor exec = newExecutor();

        LoginHandler handler = new LoginHandler(dataPostCheckText, wrong, null);
        handler.handleLogin(exec, null); // should throw CredentialInvalidException
    }

    @Test(expected = CredentialInvalidException.class)
    public void testPostAuthFailCheckUrl() throws JSONException, IOException, CredentialInvalidException {
        stubPostAuth();
        Executor exec = newExecutor();

        LoginHandler handler = new LoginHandler(dataPostCheckUrl, wrong, null);
        handler.handleLogin(exec, null); // should throw CredentialInvalidException
    }

    @Test
    public void testPostAuthFormData() throws JSONException, IOException, CredentialInvalidException {
        stubPostAuthFormData();
        Executor exec = newExecutor();

        LoginHandler handler = new LoginHandler(dataPostFormData, correct, null);
        handler.handleLogin(exec, null);

        // loading page should succeed
        String content = exec.execute(Request.Get(baseurl + "/index.html")).returnContent().asString();
        assertEquals("content", content);
    }

    @NotNull private Executor newExecutor() {
        return Executor.newInstance(HttpClientBuilder.create().build());
    }

    private void stubBasicAuth() {
        wireMockRule.stubFor(get(urlEqualTo("/index.html")).atPriority(5)
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("WWW-Authenticate", "Basic realm=\"Vertretungsplan\"")));

        wireMockRule.stubFor(get(urlEqualTo("/index.html")).atPriority(1)
                .withBasicAuth(correct.getUsername(), correct.getPassword())
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("Some content")));
    }

    private void stubPostAuth() {
        wireMockRule.stubFor(get(urlEqualTo("/index.html")).atPriority(5)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("please login")));

        wireMockRule.stubFor(post(urlEqualTo("/index.html")).atPriority(5)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("wrong")));

        stubFor(post(urlEqualTo("/index.html")).atPriority(1)
                .withRequestBody(equalTo("password=" + correct.getPassword() + "&user=" + correct.getUsername()))
                .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded; charset=UTF-8"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withHeader("Set-Cookie", "authenticated=true")
                        .withBody("content")));

        stubFor(get(urlEqualTo("/index.html")).atPriority(1)
                .withCookie("authenticated", equalTo("true"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("content")));
    }

    private void stubPostAuthFormData() {
        wireMockRule.stubFor(get(urlEqualTo("/index.html")).atPriority(5)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("please login")));

        wireMockRule.stubFor(post(urlEqualTo("/index.html")).atPriority(5)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("wrong")));

        stubFor(post(urlEqualTo("/index.html")).atPriority(1)
                .withRequestBody(matching("(.|\\n)*name=\"password\"(?:(?!--)(.|\\n))*" + correct.getPassword()
                        + "(.|\\n)*name=\"user\"(?:(?!--)(.|\\n))*" + correct.getUsername() + "(.|\\n)*"))
                .withHeader("Content-Type", matching("multipart/form-data; boundary=.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withHeader("Set-Cookie", "authenticated=true")
                        .withBody("content")));

        stubFor(get(urlEqualTo("/index.html")).atPriority(1)
                .withCookie("authenticated", equalTo("true"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("content")));
    }

    @NotNull private static SubstitutionScheduleData getSubstitutionScheduleData(JSONObject login) throws
            JSONException {
        SubstitutionScheduleData data = new SubstitutionScheduleData();
        JSONObject data1 = new JSONObject();
        data1.put("login", login);
        data.setData(data1);
        return data;
    }
}
