/**
 * Copyright (C) 2012 Ness Computing, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
    private final Set<X509TrustManager> trustManagers = new HashSet<X509TrustManager>();

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
        final Set<X509Certificate> certificates = new HashSet<X509Certificate>();
        for (X509TrustManager trustManager : trustManagers) {
            certificates.addAll(Arrays.asList(trustManager.getAcceptedIssuers()));
        }
        return certificates.toArray(new X509Certificate[certificates.size()]);
    }
}
