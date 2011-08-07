/*Copyright [2010-2011] [David Van de Ven]

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
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import android.content.Context;
import android.util.Log;

public class HttpHostup {

    /*
     * The reason for this odd class is that under some circumstances,
     * HttpClient ignores its timeouts and must be nulled
     */

    private static volatile DefaultHttpClient httpclient;
    private static volatile HttpParams httpparams;
    private static volatile HttpHead head;
    private static volatile HttpResponse response;
    // Target for header check
    private static final String H_TARGET = "http://www.google.com";
    private static String target = H_TARGET;
    private static final int TIMEOUT_EXTRA = 2000;
    private static URI headURI;
    private static int reachable;
    private static Context context;
    private volatile static boolean state;
    private Thread self;

    /*
     * Thread for header check
     */
    private class GetHeaders implements Runnable {
	public void run() {

	    try {
		state = getHttpHeaders(context);

	    } catch (IOException e) {
		state = false;
	    } catch (URISyntaxException e) {
		state = false;
	    }
	    /*
	     * Interrupt waiting thread since we have a result
	     */
	    self.interrupt();

	}
    };

    public synchronized String getHostup(final int timeout, final Context ctxt, final String router) {
	
	
	if (router == null)
	    target = H_TARGET;
	else {
	    target = router;
	    Log.i("WifiFixerService",router);
	}
	   
	
	/*
	 * get URI
	 */
	try {
	    headURI = new URI(target);
	} catch (URISyntaxException e1) {
	   return null;
	}
	
	reachable = timeout + TIMEOUT_EXTRA;
	/*
	 * Header Check Thread
	 */
	self = Thread.currentThread();
	Thread tgetHeaders = new Thread(new GetHeaders());
	tgetHeaders.start();

	try {
	    Thread.sleep(reachable);
	    /*
	     * Oh no, looks like rHttpHead has timed out longer than it should
	     * have, reset it
	     */
	    httpclient = null;
	    
	    if(state)
		return target;
	    else 
		return null;
	} catch (InterruptedException e) {
	    /*
	     * rHttpHead interrupted this is desired behavior
	     */
	    return target;
	}

    }

    /*
     * Performs HTTP HEAD request and returns boolean success or failure
     */
    private static boolean getHttpHeaders(final Context context)
	    throws IOException, URISyntaxException {

	/*
	 * Reusing Httpclient, only initializing first time
	 */
	if (httpclient == null) {
	    httpclient = new DefaultHttpClient();
	    head = new HttpHead(headURI);
	    httpparams = new BasicHttpParams();
	    HttpConnectionParams.setConnectionTimeout(httpparams, Integer
		    .valueOf(reachable));
	    HttpConnectionParams.setSoTimeout(httpparams, reachable);
	    HttpConnectionParams.setLinger(httpparams, 1);
	    HttpConnectionParams.setStaleCheckingEnabled(httpparams, true);
	    httpclient.setParams(httpparams);
	}
	int status;
	try {
	    /*
	     * The next two lines actually perform the connection since it's the
	     * same, can re-use.
	     */
	    response = httpclient.execute(head);
	    status = response.getStatusLine().getStatusCode();
	} catch (IllegalStateException e) {
	    // httpclient in bad state, reset
	    status = -1;
	    httpclient = null;
	    return false;
	}

	if (status == HttpURLConnection.HTTP_OK)
	    return true;
	else
	    return false;
    }

}
