/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.dataflow.rest.util;

import java.net.URI;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.ssl.TrustStrategy;

import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * Provides utilities for the Apache {@link HttpClient}, used to make REST calls
 *
 * @author Gunnar Hillert
 */
public class HttpUtils {

	/**
	 * Ensures that the passed-in {@link RestTemplate} is using the Apache HTTP Client. If
	 * the optional {@code
	 * username} AND {@code password} are not empty, then a
	 * {@link BasicCredentialsProvider} will be added to the {@link CloseableHttpClient}.
	 * <p>
	 * Furthermore, you can set the underlying {@link SSLContext} of the
	 * {@link HttpClient} allowing you to accept self-signed certificates.
	 *
	 * @param restTemplate the rest template, must not be null
	 * @param host the target host URI
	 * @param username the username for authentication, can be null
	 * @param password the password for authentication, can be null
	 * @param skipSslValidation whether to skip ssl validation. Use with caution! If true
	 * certificate warnings will be ignored.
	 */
	public static void prepareRestTemplate(RestTemplate restTemplate, URI host, String username, String password,
			boolean skipSslValidation) {

		Assert.notNull(restTemplate, "The provided RestTemplate must not be null.");

		final HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();

		if (StringUtils.hasText(username) && StringUtils.hasText(password)) {
			final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
			credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
			httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
		}

		if (skipSslValidation) {
			httpClientBuilder.setSSLContext(HttpUtils.buildCertificateIgnoringSslContext());
			httpClientBuilder.setSSLHostnameVerifier(new NoopHostnameVerifier());
		}

		final CloseableHttpClient httpClient = httpClientBuilder.build();
		final HttpHost targetHost = new HttpHost(host.getHost(), host.getPort(), host.getScheme());

		final HttpComponentsClientHttpRequestFactory requestFactory = new PreemptiveBasicAuthHttpComponentsClientHttpRequestFactory(
				httpClient, targetHost);
		restTemplate.setRequestFactory(requestFactory);
	}

	/**
	 * Will create a certificate-ignoring {@link SSLContext}. Please use with utmost
	 * caution as it undermines security, but may be useful in certain testing or
	 * development scenarios.
	 *
	 * @return The SSLContext
	 */
	public static SSLContext buildCertificateIgnoringSslContext() {
		try {
			return SSLContexts.custom().loadTrustMaterial(new TrustStrategy() {
				@Override
				public boolean isTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
					return true;
				}
			}).build();
		}
		catch (KeyManagementException | NoSuchAlgorithmException | KeyStoreException e) {
			throw new IllegalStateException(
					"Unexpected exception while building the certificate-ignoring SSLContext" + ".", e);
		}
	}
}
