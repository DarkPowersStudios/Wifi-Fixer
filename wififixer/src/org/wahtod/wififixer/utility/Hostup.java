/*Copyright [2010-2012] [David Van de Ven]

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */
package org.wahtod.wififixer.utility;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.wahtod.wififixer.R;

import android.content.Context;

public class Hostup {

	private static final String HTTPSCHEME = "http";
	/*
	 * getHostUp method: Executes 2 threads, icmp check and http check first
	 * thread to return state "wins"
	 */

	// Target for header check
	private static final String H_TARGET = "http://www.google.com";
	private static final String INET_LOOPBACK = "127.0.0.1";
	private static final String INET_INVALID = "0.0.0.0";
	private static String target;
	private static String response;
	private static final int TIMEOUT_EXTRA = 2000;
	private static URI headURI;
	private static int reachable;
	private Context context;
	protected volatile static boolean state;
	protected volatile static boolean finished;
	private Thread self;
	private static String icmpIP;
	private static long timer;
	private static DefaultHttpClient httpclient;

	/*
	 * for http header check thread
	 */
	private class GetHeaders implements Runnable {
		public void run() {
			boolean up = false;
			try {
				up = getHttpHeaders(context);

			} catch (IOException e) {

			} catch (URISyntaxException e) {

			}
			/*
			 * Interrupt waiting thread since we have a result
			 */
			finish(up);
		}
	};

	/*
	 * for icmp check thread
	 */
	private class GetICMP implements Runnable {
		public void run() {
			boolean up = icmpHostup(context);
			/*
			 * Interrupt waiting thread since we have a result
			 */
			finish(up);
		}
	};

	protected synchronized void finish(final boolean up) {
		if (!finished) {
			state = up;
			finished = true;
			self.interrupt();
		}
	}

	public String getHostup(final int timeout, Context ctxt, final String router) {
		finished = false;
		context = ctxt;
		/*
		 * If null, use H_TARGET else construct URL from router string
		 */
		if (router == null)
			target = H_TARGET;
		else
			target = router;

		icmpIP = target.substring(7, target.length());

		reachable = timeout + TIMEOUT_EXTRA;
		/*
		 * Start Check Threads
		 */
		self = Thread.currentThread();
		Thread tgetHeaders = new Thread(new GetHeaders());
		if (!icmpIP.equals(INET_LOOPBACK) && !icmpIP.equals(INET_INVALID)) {
			Thread tgetICMP = new Thread(new GetICMP());
			tgetICMP.start();
		}
		tgetHeaders.start();
		timer = System.currentTimeMillis();

		try {
			Thread.sleep(reachable);
			/*
			 * Oh no, looks like rHttpHead has timed out longer than it should
			 * have
			 */
			return ctxt.getString(R.string.critical_timeout);
		} catch (InterruptedException e) {
			finished = true;
			/*
			 * interrupted by a result: this is desired behavior
			 */
			return response + "\n"
					+ String.valueOf(System.currentTimeMillis() - timer)
					+ ctxt.getString(R.string.ms);
		}
	}

	/*
	 * Performs ICMP ping/echo and returns boolean success or failure
	 */
	private static boolean icmpHostup(final Context context) {
		boolean isUp = false;

		try {
			if (InetAddress.getByName(icmpIP).isReachable(reachable))
				isUp = true;

		} catch (UnknownHostException e) {

		} catch (IOException e) {

		}

		if (isUp && !finished)
			response = icmpIP + context.getString(R.string.icmp_ok);
		else
			response = icmpIP + context.getString(R.string.icmp_fail);

		return isUp;
	}

	/*
	 * Performs HTTP HEAD request and returns boolean success or failure
	 */
	private static boolean getHttpHeaders(final Context context)
			throws IOException, URISyntaxException {

		/*
		 * Reusing Httpclient, since it's expensive
		 */
		if (httpclient == null) {
			SchemeRegistry scheme = new SchemeRegistry();
			scheme.register(new Scheme(HTTPSCHEME, PlainSocketFactory
					.getSocketFactory(), 80));
			BasicHttpParams httpparams = new BasicHttpParams();
			HttpConnectionParams.setConnectionTimeout(httpparams,
					Integer.valueOf(reachable));
			HttpConnectionParams.setSoTimeout(httpparams, reachable);
			HttpConnectionParams.setLinger(httpparams, 1);
			HttpConnectionParams.setStaleCheckingEnabled(httpparams, true);
			ClientConnectionManager cm = new ThreadSafeClientConnManager(
					httpparams, scheme);
			httpclient = new DefaultHttpClient(cm, httpparams);
		}
		/*
		 * get URI
		 */
		try {
			headURI = new URI(target);
		} catch (URISyntaxException e1) {
			try {
				headURI = new URI(H_TARGET);
			} catch (URISyntaxException e) {
				// Should not ever happen since H_TARGET is a valid URL
				e.printStackTrace();
			}
		}

		int status;
		try {
			/*
			 * Get response
			 */
			HttpResponse hr = httpclient.execute(new HttpHead(headURI));
			status = hr.getStatusLine().getStatusCode();
		} catch (IllegalStateException e) {
			// httpclient in bad state, reset
			httpclient = null;
			status = -1;
		} catch (NullPointerException e) {
			/*
			 * httpConnection null
			 */
			status = -1;
		}

		if (status == HttpURLConnection.HTTP_OK) {
			if (!finished)
				response = target + context.getString(R.string.http_ok);
			return true;
		} else {
			if (!finished)
				response = target + context.getString(R.string.http_fail);
			return false;
		}
	}
}
