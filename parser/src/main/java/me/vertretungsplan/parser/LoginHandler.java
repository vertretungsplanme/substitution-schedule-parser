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
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.cookie.Cookie;
import org.apache.http.message.BasicNameValuePair;
import org.jetbrains.annotations.Nullable;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class LoginHandler {
	private SubstitutionScheduleData scheduleData;
	private Credential auth;
	private CookieProvider cookieProvider;

	public LoginHandler(SubstitutionScheduleData scheduleData, Credential auth,
						@Nullable CookieProvider cookieProvider) {
		this.scheduleData = scheduleData;
		this.auth = auth;
		this.cookieProvider = cookieProvider;
	}

	public void handleLogin(Executor executor, CookieStore cookieStore) throws JSONException, IOException, CredentialInvalidException {
		handleLogin(executor, cookieStore, false);
	}

	public String handleLoginWithResponse(Executor executor, CookieStore cookieStore) throws JSONException, IOException, CredentialInvalidException {
		return handleLogin(executor, cookieStore, true);
	}
	
	public String handleLogin(Executor executor, CookieStore cookieStore, boolean needsResponse) throws JSONException,
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
		String type = data.getJSONObject("login").optString("type", "post");
		switch (type) {
			case "post":
				List<Cookie> cookieList = cookieProvider != null ? cookieProvider.getCookies(auth) : null;
				if (cookieList != null && !needsResponse) {
					for (Cookie cookie : cookieList) cookieStore.addCookie(cookie);
				} else {
					executor.clearCookies();
					Document preDoc = null;
					if (data.getJSONObject("login").has("preUrl")) {
						String preUrl = data.getJSONObject("login").getString("preUrl");
						String preHtml = executor.execute(Request.Get(preUrl)).returnContent().asString();
						preDoc = Jsoup.parse(preHtml);
					}

					String url = data.getJSONObject("login").getString("url");
					JSONObject loginData = data.getJSONObject("login").getJSONObject("data");
					List<NameValuePair> nvps = new ArrayList<>();
					for (String name : JSONObject.getNames(loginData)) {
						String value = loginData.getString(name);

						if (name.equals("_hiddeninputs")) {
							for (Element hidden:preDoc.select(value + " input[type=hidden]")) {
								nvps.add(new BasicNameValuePair(hidden.attr("name"), hidden.attr("value")));
							}
							continue;
						}

						if (value.equals("_login"))
							value = login;
						else if (value.equals("_password"))
							value = password;
						nvps.add(new BasicNameValuePair(name, value));
					}
					String html = executor.execute(Request.Post(url).bodyForm(nvps, Charset.forName("UTF-8"))).returnContent().asString();
					if (cookieProvider != null) cookieProvider.saveCookies(auth, cookieStore.getCookies());

					String checkUrl = data.getJSONObject("login").optString("checkUrl", null);
					String checkText = data.getJSONObject("login").optString("checkText", null);
					if (checkUrl != null && checkText != null) {
						String response = executor.execute(Request.Get(checkUrl)).returnContent()
								.asString();
						if (response.contains(checkText)) throw new CredentialInvalidException();
					}
					return html;
				}
				break;
			case "basic":
				if (login == null) throw new IOException("wrong auth type");
				executor.auth(login, password);
				if (data.getJSONObject("login").has("url")) {
					String url = data.getJSONObject("login").getString("url");
					if (executor.execute(Request.Get(url)).returnResponse().getStatusLine().getStatusCode() != 200)
						throw new IOException("wrong login/password");
				}
				break;
			case "fixed":
				String loginFixed = data.getJSONObject("login").optString("login", null);
				String passwordFixed = data.getJSONObject("login").getString("password");
				if (!Objects.equals(loginFixed, login) || !Objects.equals(passwordFixed, password)) {
					throw new IOException("wrong login/password");
				}
				break;
		}
		return null;
	}
}
