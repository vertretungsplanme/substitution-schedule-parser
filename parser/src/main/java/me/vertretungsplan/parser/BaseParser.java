/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 */

package me.vertretungsplan.parser;

import me.vertretungsplan.exception.CredentialInvalidException;
import me.vertretungsplan.networking.MultiTrustManager;
import me.vertretungsplan.objects.SubstitutionSchedule;
import me.vertretungsplan.objects.SubstitutionScheduleData;
import me.vertretungsplan.objects.credential.Credential;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ein Parser für einen Vertretungsplan. Er erhält Informationen aus der
 * JSON-Datei für eine Schule und liefert den abgerufenen und geparsten
 * Vertretungsplan zurück.
 */
public abstract class BaseParser implements SubstitutionScheduleParser {
    protected SubstitutionScheduleData scheduleData;
    protected Executor executor;
    protected Credential credential;
    protected CookieStore cookieStore;
    protected ColorProvider colorProvider;
    protected CookieProvider cookieProvider;

    public BaseParser(SubstitutionScheduleData scheduleData, CookieProvider cookieProvider) {
        this.scheduleData = scheduleData;
        this.cookieProvider = cookieProvider;
        this.cookieStore = new BasicCookieStore();
        this.colorProvider = new ColorProvider(scheduleData);

        try {
            KeyStore ks = loadKeyStore();
            MultiTrustManager multiTrustManager = new MultiTrustManager();
            multiTrustManager.addTrustManager(getDefaultTrustManager());
            multiTrustManager.addTrustManager(trustManagerFromKeystore(ks));

            TrustManager[] trustManagers = new TrustManager[]{multiTrustManager};
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagers, null);
            SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(
                    sslContext,
                    new String[]{"TLSv1"},
                    null,
                    SSLConnectionSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER);

            CloseableHttpClient httpclient = HttpClients.custom()
                    .setSSLSocketFactory(sslsf).setRedirectStrategy(new LaxRedirectStrategy()).build();
            this.executor = Executor.newInstance(httpclient).cookieStore(
                    cookieStore);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Erstelle einen neuen Parser für eine Schule. Liefert automatisch eine
     * passende Unterklasse.
     *
     * @param data die Schule, für die ein Parser erstellt werden soll
     * @return Eine Unterklasse von {@link BaseParser}, die zur übergebenen
     * Schule passt
     */
    public static BaseParser getInstance(SubstitutionScheduleData data, @Nullable CookieProvider cookieProvider) {
        BaseParser parser = null;
        switch (data.getApi()) {
            case "untis-monitor":
                parser = new UntisMonitorParser(data, cookieProvider);
                break;
            case "untis-info":
                parser = new UntisInfoParser(data, cookieProvider);
                break;
            case "untis-info-headless":
                parser = new UntisInfoHeadlessParser(data, cookieProvider);
                break;
            case "untis-subst":
                parser = new UntisSubstitutionParser(data, cookieProvider);
                break;
            case "dsbmobile":
                parser = new DSBMobileParser(data, cookieProvider);
                break;
            case "dsblight":
                parser = new DSBLightParser(data, cookieProvider);
                break;
            case "svplan":
                parser = new SVPlanParser(data, cookieProvider);
                break;
            case "davinci":
                parser = new DaVinciParser(data, cookieProvider);
                break;
            case "eschool":
                parser = new ESchoolParser(data, cookieProvider);
                break;
            case "turbovertretung":
                parser = new TurboVertretungParser(data, cookieProvider);
                break;
            case "csv":
                parser = new CSVParser(data, cookieProvider);
                break;
        }
        return parser;
    }

    private static X509TrustManager getDefaultTrustManager()
            throws GeneralSecurityException {
        return trustManagerFromKeystore(null);
    }

    private static X509TrustManager trustManagerFromKeystore(
            final KeyStore keystore) throws GeneralSecurityException {
        final TrustManagerFactory trustManagerFactory = TrustManagerFactory
                .getInstance("PKIX", "SunJSSE");
        trustManagerFactory.init(keystore);

        final TrustManager[] tms = trustManagerFactory.getTrustManagers();

        for (final TrustManager tm : tms) {
            if (tm instanceof X509TrustManager) {
                return X509TrustManager.class.cast(tm);
            }
        }
        throw new IllegalStateException("Could not locate X509TrustManager!");
    }

    protected static String recognizeType(String text) {
        if (text.contains("f.a.") || text.contains("fällt aus") || text.contains("faellt aus") ||
                text.contains("entfällt")) {
            return "Entfall";
        } else if (equalsOneOf(text, "Raumänderung", "Klasse frei", "Unterrichtstausch", "Freistunde", "Raumverlegung",
                "Selbstlernen", "Zusammenlegung", "HA")) {
            return text;
        } else if (text.contains("verschoben")) {
            return "Verlegung";
        } else if (text.contains("geänderter Raum")) {
            return "Raumänderung";
        } else {
            return null;
        }
    }

    private static boolean equalsOneOf(String container, String... strings) {
        for (String string : strings) {
            if (container.equals(string)) return true;
        }
        return false;
    }

    /**
     * Ruft den Vertretungsplan ab und parst ihn. Wird immer asynchron
     * ausgeführt.
     *
     * @return Der geparste {@link SubstitutionSchedule}
     * @throws IOException
     * @throws JSONException
     */
    public abstract SubstitutionSchedule getSubstitutionSchedule()
            throws IOException, JSONException, CredentialInvalidException;

    /**
     * Gibt eine Liste aller verfügbaren Klassen zurück. Wird immer asynchron
     * ausgeführt.
     *
     * @return Eine Liste aller verfügbaren Klassen für diese Schule (auch die,
     * die nicht aktuell vom Vertretungsplan betroffen sind)
     * @throws IOException
     * @throws JSONException
     */
    public abstract List<String> getAllClasses() throws IOException, JSONException;

    @SuppressWarnings("SameReturnValue")
    public abstract List<String> getAllTeachers() throws IOException, JSONException;

    public Credential getCredential() {
        return credential;
    }

    public void setCredential(Credential credential) {
        if (!scheduleData.getAuthenticationData().getCredentialType().equals(credential.getClass())) {
            throw new IllegalArgumentException("Wrong credential type");
        }
        this.credential = credential;
    }

    protected String httpGet(String url, String encoding) throws IOException {
        return httpGet(url, encoding, null);
    }

    protected String httpGet(String url, String encoding,
                             Map<String, String> headers) throws IOException {
        Request request = Request.Get(url).connectTimeout(15000)
                .socketTimeout(15000);
        if (headers != null) {
            for (Entry<String, String> entry : headers.entrySet()) {
                request.addHeader(entry.getKey(), entry.getValue());
            }
        }
        return new String(executor.execute(request).returnContent().asBytes(),
                encoding);
    }

    @SuppressWarnings("SameParameterValue")
    protected String httpPost(String url, String encoding,
                              List<NameValuePair> formParams) throws IOException {
        return httpPost(url, encoding, formParams, null);
    }

    protected String httpPost(String url, String encoding,
                              List<NameValuePair> formParams, Map<String, String> headers) throws IOException {
        Request request = Request.Post(url).bodyForm(formParams)
                .connectTimeout(15000).socketTimeout(15000);
        if (headers != null) {
            for (Entry<String, String> entry : headers.entrySet()) {
                request.addHeader(entry.getKey(), entry.getValue());
            }
        }
        return new String(executor.execute(request)
                .returnContent().asBytes(), encoding);
    }

    @SuppressWarnings("SameParameterValue")
    protected String httpPost(String url, String encoding, String body, ContentType contentType) throws IOException {
        return httpPost(url, encoding, body, contentType, null);
    }

    @SuppressWarnings("SameParameterValue")
    protected String httpPost(String url, String encoding, String body, ContentType contentType,
                              Map<String, String> headers) throws IOException {
        Request request = Request.Post(url).bodyString(body, contentType)
                .connectTimeout(15000).socketTimeout(15000);
        if (headers != null) {
            for (Entry<String, String> entry : headers.entrySet()) {
                request.addHeader(entry.getKey(), entry.getValue());
            }
        }
        return new String(executor.execute(request)
                .returnContent().asBytes(), encoding);
    }

    private KeyStore loadKeyStore() throws KeyStoreException,
            NoSuchAlgorithmException, CertificateException, IOException {
        InputStream is = null;
        try {
            KeyStore ks = KeyStore.getInstance("JKS");
            is = getClass().getClassLoader().getResourceAsStream(
                    "trustStore.jks");
            if (is == null) {
                throw new RuntimeException();
            }
            ks.load(is, "Vertretungsplan".toCharArray());
            return ks;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    protected String getClassName(String text, JSONObject data) throws JSONException {
        text = text.replace("(", "").replace(")", "");
        if (data.has("classRegex")) {
            Pattern pattern = Pattern.compile(data.getString("classRegex"));
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                if (matcher.groupCount() > 0) {
                    return matcher.group(1);
                } else {
                    return matcher.group();
                }
            } else {
                return "";
            }
        } else {
            return text;
        }
    }

    protected boolean contains(JSONArray array, String string) throws JSONException {
        for (int i = 0; i < array.length(); i++) {
            if (array.getString(i).equals(string)) {
                return true;
            }
        }
        return false;
    }


    @Nullable
    protected List<String> getClassesFromJson() throws JSONException {
        if (scheduleData.getData().has("classes")) {
            JSONArray classesJson = scheduleData.getData().getJSONArray("classes");
            List<String> classes = new ArrayList<String>();
            for (int i = 0; i < classesJson.length(); i++) {
                classes.add(classesJson.getString(i));
            }
            return classes;
        } else {
            return null;
        }
    }
}
