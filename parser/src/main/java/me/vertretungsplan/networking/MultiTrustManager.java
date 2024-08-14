/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package me.vertretungsplan.networking;

import javax.net.ssl.X509TrustManager;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class MultiTrustManager implements X509TrustManager
{
    private final Set<X509TrustManager> trustManagers = new HashSet<>();

    public void addTrustManager(final X509TrustManager trustManager)
    {
        trustManagers.add(trustManager);
    }

    @Override
    public void checkClientTrusted(final X509Certificate[] chain, final String authType) throws CertificateException
    {
        if (trustManagers.isEmpty()) {
            throw new CertificateException("No trust managers installed!");
        }

        CertificateException ce = null;
        for (X509TrustManager trustManager : trustManagers) {
            try {
                trustManager.checkClientTrusted(chain, authType);
                return;
            }
            catch (CertificateException trustCe) {
                ce = trustCe;
            }
        }

        throw ce;
    }

    @Override
    public void checkServerTrusted(final X509Certificate[] chain, final String authType) throws CertificateException
    {
        if (trustManagers.isEmpty()) {
            throw new CertificateException("No trust managers installed!");
        }

        CertificateException ce = null;
        for (X509TrustManager trustManager : trustManagers) {
            try {
                trustManager.checkServerTrusted(chain, authType);
                return;
            }
            catch (CertificateException trustCe) {
                ce = trustCe;
            }
        }

        throw ce;
    }

    @Override
    public X509Certificate[] getAcceptedIssuers()
    {
        final Set<X509Certificate> certificates = new HashSet<>();
        for (X509TrustManager trustManager : trustManagers) {
            certificates.addAll(Arrays.asList(trustManager.getAcceptedIssuers()));
        }
        return certificates.toArray(new X509Certificate[0]);
    }
}
