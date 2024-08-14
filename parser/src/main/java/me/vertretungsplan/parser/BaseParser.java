/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.parser;

import com.github.sardine.Sardine;
import com.github.sardine.impl.SardineImpl;
import com.mifmif.common.regex.Generex;
import me.vertretungsplan.exception.CredentialInvalidException;
import me.vertretungsplan.networking.MultiTrustManager;
import me.vertretungsplan.objects.SubstitutionSchedule;
import me.vertretungsplan.objects.SubstitutionScheduleData;
import me.vertretungsplan.objects.credential.Credential;
import me.vertretungsplan.objects.credential.UserPasswordCredential;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joda.time.LocalDateTime;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.universalchardet.UniversalDetector;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base class for {@link SubstitutionScheduleParser} implementations.
 */
public abstract class BaseParser implements SubstitutionScheduleParser {
    public static final String PARAM_CLASS_REGEX = "classRegex";
    private static final String PARAM_SSL_HOSTNAME = "sslHostname";
    private static final String PARAM_SSL_VERIFY_HOSTNAME = "sslVerifyHostname";
    static final String PARAM_CLASS_RANGES = "classRanges";
    static final String PARAM_HEADERS = "headers";
    static final String CLASS_RANGES_CLASS_REGEX = "classRegex";
    static final String CLASS_RANGES_GRADE_REGEX = "gradeRegex";
    static final String CLASS_RANGES_RANGE_FORMAT = "rangeFormat";
    static final String CLASS_RANGES_SINGLE_FORMAT = "singleFormat";

    protected final SubstitutionScheduleData scheduleData;
    protected Executor executor;
    protected Credential credential;
    protected final CookieStore cookieStore;
    protected final ColorProvider colorProvider;
    protected final CookieProvider cookieProvider;
    protected final UniversalDetector encodingDetector;
    protected DebuggingDataHandler debuggingDataHandler;
    protected Sardine sardine;
    private Path localSource;

    BaseParser(SubstitutionScheduleData scheduleData, CookieProvider cookieProvider) {
        this.scheduleData = scheduleData;
        this.cookieProvider = cookieProvider;
        this.cookieStore = new BasicCookieStore();
        this.colorProvider = new ColorProvider(scheduleData);
        this.encodingDetector = new UniversalDetector(null);
        this.debuggingDataHandler = new NoOpDebuggingDataHandler();
        this.sardine = null;

        try {
            SSLConnectionSocketFactory sslsf = getSslConnectionSocketFactory(scheduleData);

            CloseableHttpClient httpclient = HttpClients.custom()
                    .setSSLSocketFactory(sslsf)
                    .setRedirectStrategy(new LaxRedirectStrategy())
                    .setDefaultRequestConfig(RequestConfig.custom()
                            .setCookieSpec(CookieSpecs.STANDARD).build())
                    .setUserAgent(
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.169 Safari/537.36")
                    .build();
            this.executor = Executor.newInstance(httpclient).use(cookieStore);
        } catch (GeneralSecurityException | JSONException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    @NotNull private SSLConnectionSocketFactory getSslConnectionSocketFactory(SubstitutionScheduleData scheduleData)
            throws IOException, GeneralSecurityException, JSONException {
        KeyStore ks = loadKeyStore();
        MultiTrustManager multiTrustManager = new MultiTrustManager();
        multiTrustManager.addTrustManager(getDefaultTrustManager());
        multiTrustManager.addTrustManager(trustManagerFromKeystore(ks));

        TrustManager[] trustManagers = new TrustManager[]{multiTrustManager};
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagers, null);
        final HostnameVerifier hostnameVerifier;

        if (scheduleData.getData() != null && scheduleData.getData().has(PARAM_SSL_HOSTNAME)) {
            hostnameVerifier = new CustomHostnameVerifier(scheduleData.getData().getString(PARAM_SSL_HOSTNAME));
        } else if (scheduleData.getData() != null &&
                !scheduleData.getData().optBoolean(PARAM_SSL_VERIFY_HOSTNAME, true)) {
            hostnameVerifier = new NoopHostnameVerifier();
        } else {
            hostnameVerifier = new DefaultHostnameVerifier();
        }
        return new SSLConnectionSocketFactory(
                sslContext,
                new String[]{"TLSv1", "TLSv1.1", "TLSv1.2"},
                null,
                hostnameVerifier);
    }

    /**
     * Create an appropriate parser for a given school. Automatically uses the appropriate subclass depending on
     * {@link SubstitutionScheduleData#getApi()}.
     *
     * @param data a {@link SubstitutionScheduleData} object containing information about the substitution schedule
     * @return a {@link BaseParser} subclass able to parse the given schedule.
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
                parser = new NotCompatibleParser(data, cookieProvider);
                break;
            case "dsblight":
                parser = new NotCompatibleParser(data, cookieProvider);
                break;
            case "not-compatible":
                parser = new NotCompatibleParser(data, cookieProvider);
                break;
            case "not-desired":
                parser = new NotCompatibleParser(data, cookieProvider);
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
            case "iphis":
                parser = new IphisParser(data, cookieProvider);
                break;
            case "indiware":
                parser = new IndiwareParser(data, cookieProvider);
                break;
            case "stundenplan24":
                parser = new IndiwareStundenplan24Parser(data, cookieProvider);
                break;
            case "indiware-mobile":
                parser = new IndiwareMobileParser(data, cookieProvider);
                break;
            case "schooljoomla":
                parser = new SchoolJoomlaParser(data, cookieProvider);
                break;
            case "webuntis":
                parser = new NotCompatibleParser(data, cookieProvider);
                break;
            case "vpo":
                parser = new VPOParser(data, cookieProvider);
                break;
        }
        return parser;
    }

    protected Sardine getWebdavClient(UserPasswordCredential credential)
            throws JSONException, GeneralSecurityException, IOException {
        if (sardine == null) {
            final SSLConnectionSocketFactory sslsf = getSslConnectionSocketFactory(scheduleData);
            sardine = new SardineImpl() {
                @Override protected ConnectionSocketFactory createDefaultSecureSocketFactory() {
                    return sslsf;
                }
            };
            if (credential != null) {
                sardine.setCredentials(credential.getUsername(), credential.getPassword());
            }
        }
        return sardine;
    }

    @Override public LocalDateTime getLastChange() throws IOException, CredentialInvalidException {
        // default implementation returns null
        return null;
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
                return (X509TrustManager) tm;
            }
        }
        throw new IllegalStateException("Could not locate X509TrustManager!");
    }

    protected static String recognizeType(String text) {
        if (text.toLowerCase().contains("f.a.") || text.toLowerCase().contains("fällt aus")
                || text.toLowerCase().contains("faellt aus") || text.toLowerCase().contains("entfällt") || text
                .toLowerCase().contains("entfall")) {
            return "Entfall";
        } else if (equalsOneOf(text, "Raumänderung", "Klasse frei", "Unterrichtstausch", "Freistunde", "Raumverlegung",
                "Selbstlernen", "Zusammenlegung", "HA", "Raum beachten", "Stundentausch", "Klausur", "Raum-Vertr.",
                "Betreuung", "Frei/Veranstaltung", "Raumwechsel", "selbstständiges Arbeiten")) {
            return text;
        } else if (text.startsWith("Ausfallstunde:")) {
            return "Ausfallstunde";
        } else if (text.startsWith("Raumwechsel/ Stillarbeit:")) {
            return "Raumwechsel/ Stillarbeit";
        } else if (text.startsWith("Stillarbeit:")) {
            return "Stillarbeit";
        } else if (text.contains("verschoben")) {
            return "Verlegung";
        } else if (text.contains("geänderter Raum")) {
            return "Raumänderung";
        } else if (text.contains("frei")) {
            return "Entfall";
        } else if (text.contains("Aufgaben")) {
            return "Aufgaben";
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

    public abstract SubstitutionSchedule getSubstitutionSchedule()
            throws IOException, JSONException, CredentialInvalidException;

    /**
     * Get a list of all available classes.
     *
     * @return a list of all available classes (also those not currently affected by the substitution schedule)
     * @throws IOException Connection or parsing error
     * @throws JSONException Error with the JSON configuration
     */
    public abstract List<String> getAllClasses() throws IOException, JSONException, CredentialInvalidException;

    /**
     * Get a list of all available teachers. Can also be <code>null</code>.
     *
     * @return a list of all available teachers (also those not currently affected by the substitution schedule)
     * @throws IOException   Connection or parsing error
     * @throws JSONException Error with the JSON configuration
     */
    @SuppressWarnings("SameReturnValue")
    public abstract List<String> getAllTeachers() throws IOException, JSONException, CredentialInvalidException;

    public Credential getCredential() {
        return credential;
    }

    public void setCredential(Credential credential) {
        if (!scheduleData.getAuthenticationData().getCredentialType().equals(credential.getClass())) {
            throw new IllegalArgumentException("Wrong credential type");
        }
        this.credential = credential;
    }

    public void setDebuggingDataHandler(DebuggingDataHandler handler) {
        this.debuggingDataHandler = handler;
    }

    protected String httpGet(String url) throws IOException, CredentialInvalidException {
        return httpGet(url, null, null);
    }

    protected String httpGet(String url, String encoding) throws IOException, CredentialInvalidException {
        return httpGet(url, encoding, null);
    }

    protected String httpGet(String url, String encoding,
                             Map<String, String> headers) throws IOException, CredentialInvalidException {
        if (url.startsWith("local://")) {
            Path file = localSource.resolve(url.substring("local://".length()));
            byte[] bytes = Files.readAllBytes(file);
            encoding = getEncoding(encoding, bytes);
            return new String(bytes, encoding);
        } else {
            Request request = Request.Get(url).connectTimeout(getTimeout())
                    .socketTimeout(getTimeout());
            if (headers != null) {
                for (Entry<String, String> entry : headers.entrySet()) {
                    request.addHeader(entry.getKey(), entry.getValue());
                }
            }
            JSONObject jsonHeaders = scheduleData.getData().optJSONObject(PARAM_HEADERS);
            if (jsonHeaders != null) {
                for (String key : JSONObject.getNames(jsonHeaders)) {
                    request.addHeader(key, jsonHeaders.optString(key));
                }
            }
            return executeRequest(encoding, request);
        }
    }

    @Nullable private String executeRequest(String encoding, Request request)
            throws IOException, CredentialInvalidException {
        try {
            byte[] bytes = executor.execute(request).returnContent().asBytes();
            encoding = getEncoding(encoding, bytes);
            return new String(bytes, encoding);
        } catch (HttpResponseException e) {
            handleHttpResponseException(e);
            return null;
        } finally {
            encodingDetector.reset();
        }
    }

    @NotNull private String getEncoding(String defaultEncoding, byte[] bytes) {
        encodingDetector.handleData(bytes, 0, bytes.length);
        encodingDetector.dataEnd();
        String encoding = encodingDetector.getDetectedCharset();
        if (encoding == null || encoding.equals("GB18030")) encoding = defaultEncoding;
        if (encoding == null) encoding = "UTF-8";
        encodingDetector.reset();
        return encoding;
    }

    @SuppressWarnings("SameParameterValue")
    protected String httpPost(String url, String encoding,
                              List<NameValuePair> formParams) throws IOException, CredentialInvalidException {
        return httpPost(url, encoding, formParams, null);
    }

    protected String httpPost(String url, String encoding,
                              List<NameValuePair> formParams, Map<String, String> headers)
            throws IOException, CredentialInvalidException {
        Request request = Request.Post(url).bodyForm(formParams)
                .connectTimeout(getTimeout()).socketTimeout(getTimeout());
        if (headers != null) {
            for (Entry<String, String> entry : headers.entrySet()) {
                request.addHeader(entry.getKey(), entry.getValue());
            }
        }
        return executeRequest(encoding, request);
    }

    private int getTimeout() {
        return 30000;
    }

    @SuppressWarnings("SameParameterValue")
    protected String httpPost(String url, String encoding, String body, ContentType contentType)
            throws IOException, CredentialInvalidException {
        return httpPost(url, encoding, body, contentType, null);
    }

    @SuppressWarnings("SameParameterValue")
    protected String httpPost(String url, String encoding, String body, ContentType contentType,
                              Map<String, String> headers) throws IOException, CredentialInvalidException {
        Request request = Request.Post(url).bodyString(body, contentType)
                .connectTimeout(getTimeout()).socketTimeout(getTimeout());
        if (headers != null) {
            for (Entry<String, String> entry : headers.entrySet()) {
                request.addHeader(entry.getKey(), entry.getValue());
            }
        }
        return executeRequest(encoding, request);
    }

    private void handleHttpResponseException(HttpResponseException e)
            throws CredentialInvalidException, HttpResponseException {
        if (e.getStatusCode() == 401 || e.getStatusCode() == 403) {
            throw new CredentialInvalidException();
        } else {
            throw e;
        }
    }

    private KeyStore loadKeyStore() throws KeyStoreException,
            NoSuchAlgorithmException, CertificateException, IOException {
        Security.addProvider(new BouncyCastleProvider());
        InputStream is = null;
        try {
            KeyStore ks = KeyStore.getInstance("BKS");
            is = getClass().getClassLoader().getResourceAsStream(
                    "trustStore.bks");
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

    static String getClassName(String text, JSONObject data) throws JSONException {
        text = text.replace("(", "").replace(")", "");
        if (data.has(PARAM_CLASS_REGEX)) {
            Pattern pattern = Pattern.compile(data.getString(PARAM_CLASS_REGEX));
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

    protected static boolean contains(JSONArray array, String string) throws JSONException {
        for (int i = 0; i < array.length(); i++) {
            if (array.getString(i).equals(string)) {
                return true;
            }
        }
        return false;
    }


    @Nullable
    protected List<String> getClassesFromJson() throws JSONException {
        final JSONObject data = scheduleData.getData();
        return ParserUtils.getClassesFromJson(data);
    }

    static Set<String> handleClassRanges(String klasse, JSONObject data) throws JSONException {
        HashSet<String> classes = new HashSet<>();
        classes.add(klasse);
        return handleClassRanges(classes, data);
    }

    static Set<String> handleClassRanges(Set<String> classes, JSONObject data) throws JSONException {
        if (data == null || !data.has(PARAM_CLASS_RANGES)) return classes;
        JSONObject options = data.getJSONObject(PARAM_CLASS_RANGES);
        String rangeFormat = options.getString(CLASS_RANGES_RANGE_FORMAT);
        String singleFormat = options.getString(CLASS_RANGES_SINGLE_FORMAT);
        String classRegex = options.getString(CLASS_RANGES_CLASS_REGEX);
        String gradeRegex = options.getString(CLASS_RANGES_GRADE_REGEX);

        int gradePos = -1;
        int minClassPos = -1;
        int maxClassPos = -1;

        StringBuilder regex = new StringBuilder();
        int i = 0;
        for (char c: rangeFormat.toCharArray()) {
            switch (c) {
                case 'g':
                    if (gradePos == -1) {
                        regex.append("(").append(gradeRegex).append(")");
                        i++;
                        gradePos = i;
                    } else {
                        regex.append("\\").append(gradePos);
                    }
                    break;
                case 'c':
                    regex.append("(").append(classRegex).append(")");
                    i++;
                    if (minClassPos == -1) {
                        minClassPos = i;
                    } else if (maxClassPos == -1) {
                        maxClassPos = i;
                    } else {
                        throw new IllegalArgumentException("more than two classes in classRanges.rangeFormat");
                    }
                    break;
                default:
                    regex.append(c);
                    break;
            }
        }
        Pattern pattern = Pattern.compile(regex.toString());

        Set<String> processedClasses = new HashSet<>();
        for (String klasse:classes) {
            Matcher matcher = pattern.matcher(klasse);
            if (matcher.matches()) {
                String grade = matcher.group(gradePos);
                String minClass = matcher.group(minClassPos);
                String maxClass = matcher.group(maxClassPos);

                StringBuilder rangeRegex = new StringBuilder();
                for (char c: singleFormat.toCharArray()) {
                    switch (c) {
                        case 'g':
                            rangeRegex.append(grade);
                            break;
                        case 'c':
                            rangeRegex.append("[").append(minClass).append("-").append(maxClass).append("]");
                            break;
                        default:
                            rangeRegex.append(c);
                            break;
                    }
                }
                processedClasses.addAll(new Generex(rangeRegex.toString()).getAllMatchedStrings());
            } else {
                processedClasses.add(klasse);
            }
        }

        return processedClasses;
    }

    public void setLocalSource(Path localSource) {
        this.localSource = localSource;
    }

    private static class CustomHostnameVerifier implements HostnameVerifier {
        private final String host;
        private final DefaultHostnameVerifier defaultHostnameVerifier;

        public CustomHostnameVerifier(String host) {
            this.host = host;
            this.defaultHostnameVerifier = new DefaultHostnameVerifier();
        }

        @Override public boolean verify(String s, SSLSession sslSession) {
            return defaultHostnameVerifier.verify(host, sslSession) |
                    defaultHostnameVerifier.verify(this.host, sslSession);
        }
    }

    public boolean isPersonal() {
        return false;
    }

    private static class NoOpDebuggingDataHandler implements DebuggingDataHandler {
        @Override public void columnTitles(List<String> columnTitles) {

        }
    }
}
