/*Copyright [2010] [David Van de Ven]

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
package org.wahtod.wififixer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.wahtod.wififixer.R;
import org.wahtod.wififixer.PrefConstants.Pref;
import org.wahtod.wififixer.ScreenStateHandler.OnScreenStateChangedListener;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Message;
import android.text.format.Formatter;

/*
 * Handles all interaction 
 * with WifiManager
 */
public class WFConnection extends Object implements
	OnScreenStateChangedListener {
    private static WifiManager wm;
    private static String cachedIP;
    private static String appname;
    private static PrefUtil prefs;
    private static Context ctxt;
    private WakeLock wakelock;
    static boolean screenstate;

    // flags
    private static boolean pendingwifitoggle = false;
    private static boolean shouldrepair = false;
    private static boolean pendingscan = false;
    private static boolean pendingreconnect = false;
    private static final boolean screenisoff = false;

    // IDs For notifications
    private static final int NOTIFID = 31337;
    private static final int NETNOTIFID = 8236;
    private static final int ERR_NOTIF = 7972;

    // Supplicant Constants
    private static final String DISCONNECTED = "DISCONNECTED";
    private static final String INACTIVE = "INACTIVE";
    private static final String COMPLETED = "COMPLETED";

    // Wifi Lock tag
    private static final String WFLOCK_TAG = "WFLock";

    // Empty string
    private final static String EMPTYSTRING = "";
    private static final String NEWLINE = "\n";

    // Target for header check
    private static final String H_TARGET = "http://www.google.com";
    private static URI headURI;

    // ms for IsReachable
    private final static int REACHABLE = 4000;
    private final static int HTTPREACH = 8000;
    // ms for main loop sleep
    private final static int LOOPWAIT = 10000;
    // ms for sleep loop check
    private final static long SLEEPWAIT = 60000;
    // ms for lock delays
    private final static int LOCKWAIT = 5000;
    // ms to wait after trying to connect
    private static final int CONNECTWAIT = 8000;
    private static final int SHORTWAIT = 1500;
    private static final int REALLYSHORTWAIT = 200;

    // for Dbm
    private static final int DBM_FLOOR = -90;

    // various
    private static final int NULLVAL = -1;
    private static int lastAP = NULLVAL;

    private static WifiInfo myWifi;
    private static WifiManager.WifiLock lock;
    private static DefaultHttpClient httpclient;
    private static HttpParams httpparams;
    private static HttpHead head;
    private static HttpResponse response;
    private static List<WFConfig> knownbysignal = new ArrayList<WFConfig>();

    // deprecated
    static boolean templock;
    static boolean logging;

    /*
     * Constants for wifirepair values
     */
    private static final int W_REASSOCIATE = 0;
    private static final int W_RECONNECT = 1;
    private static final int W_REPAIR = 2;

    private static int wifirepair = W_REASSOCIATE;

    // Runnable Constants for handler
    private static final int MAIN = 0;
    private static final int REPAIR = 1;
    private static final int RECONNECT = 2;
    private static final int WIFITASK = 3;
    private static final int TEMPLOCK_ON = 4;
    private static final int TEMPLOCK_OFF = 5;
    private static final int SLEEPCHECK = 8;
    private static final int SCAN = 9;
    private static final int N1CHECK = 10;
    private static final int SIGNALHOP = 12;

    private Handler hMain = new Handler() {
	@Override
	public void handleMessage(Message message) {
	    switch (message.what) {

	    case MAIN:
		hMain.post(rMain);
		break;

	    case REPAIR:
		hMain.post(rRepair);
		break;

	    case RECONNECT:
		hMain.post(rReconnect);
		break;

	    case WIFITASK:
		hMain.post(rWifiTask);
		break;

	    case TEMPLOCK_ON:
		templock = true;
		if (logging)
		    LogService.log(ctxt, appname, ctxt
			    .getString(R.string.setting_temp_lock));
		break;

	    case TEMPLOCK_OFF:
		templock = false;
		if (logging)
		    LogService.log(ctxt, appname, ctxt
			    .getString(R.string.removing_temp_lock));
		break;

	    case SLEEPCHECK:
		hMain.post(rSleepcheck);
		break;

	    case SCAN:
		hMain.post(rScan);
		break;

	    case N1CHECK:
		n1Fix();
		break;

	    case SIGNALHOP:
		hMain.post(rSignalhop);
		break;

	    }
	}
    };

    /*
     * Runs second time supplicant nonresponsive
     */
    private Runnable rRepair = new Runnable() {
	public void run() {
	    if (!wm.isWifiEnabled()) {
		handlerWrapper(TEMPLOCK_OFF);
		if (logging)
		    LogService.log(ctxt, appname, ctxt
			    .getString(R.string.wifi_off_aborting_repair));
		return;
	    }

	    if (getKnownAPsBySignal(ctxt) > 0 && connectToBest(ctxt) != NULLVAL
		    && (getNetworkID() != NULLVAL)) {
		pendingreconnect = false;
		if (logging)
		    LogService.log(ctxt, appname, ctxt
			    .getString(R.string.connected_to_network)
			    + getNetworkID());
	    } else {
		pendingreconnect = true;
		toggleWifi();
		if (logging)
		    LogService.log(ctxt, appname, ctxt
			    .getString(R.string.toggling_wifi));

	    }
	}

    };

    /*
     * Runs first time supplicant nonresponsive
     */
    private Runnable rReconnect = new Runnable() {
	public void run() {
	    if (!wm.isWifiEnabled()) {
		handlerWrapper(TEMPLOCK_OFF);
		if (logging)
		    LogService.log(ctxt, appname, ctxt
			    .getString(R.string.wifi_off_aborting_reconnect));
		return;
	    }
	    if (getKnownAPsBySignal(ctxt) > 0 && connectToBest(ctxt) != NULLVAL
		    && (getNetworkID() != NULLVAL)) {
		pendingreconnect = false;
		if (logging)
		    LogService.log(ctxt, appname, ctxt
			    .getString(R.string.connected_to_network)
			    + getNetworkID());
	    } else {
		wifirepair = W_REASSOCIATE;
		startScan(true);
		if (logging)
		    LogService
			    .log(
				    ctxt,
				    appname,
				    ctxt
					    .getString(R.string.exiting_supplicant_fix_thread_starting_scan));
	    }

	}

    };

    /*
     * Main tick
     */
    private Runnable rMain = new Runnable() {
	public void run() {

	    // Check Supplicant
	    if (wm.isWifiEnabled() && !wm.pingSupplicant()) {
		if (logging)
		    LogService
			    .log(
				    ctxt,
				    appname,
				    ctxt
					    .getString(R.string.supplicant_nonresponsive_toggling_wifi));
		toggleWifi();
	    } else if (!templock && !screenstate)
		checkWifi();

	    if (prefs.getFlag(Pref.DISABLE_KEY)) {
		if (logging) {
		    LogService.log(ctxt, appname, ctxt
			    .getString(R.string.shouldrun_false_dying));
		    // stopSelf();
		}
	    } else
		// Queue next run of main runnable
		handlerWrapper(MAIN, LOOPWAIT);

	}
    };

    /*
     * Handles non-supplicant wifi fixes.
     */
    private Runnable rWifiTask = new Runnable() {
	public void run() {

	    switch (wifirepair) {

	    case W_REASSOCIATE:
		// Let's try to reassociate first..
		wm.reassociate();
		if (logging)
		    LogService.log(ctxt, appname, ctxt
			    .getString(R.string.reassociating));
		tempLock(REACHABLE);
		wifirepair++;
		notifyWrap(ctxt, ctxt.getString(R.string.reassociating));
		break;

	    case W_RECONNECT:
		// Ok, now force reconnect..
		wm.reconnect();
		if (logging)
		    LogService.log(ctxt, appname, ctxt
			    .getString(R.string.reconnecting));
		tempLock(REACHABLE);
		wifirepair++;
		notifyWrap(ctxt, ctxt.getString(R.string.reconnecting));
		break;

	    case W_REPAIR:
		// Start Scan
		startScan(true);
		wifirepair = W_REASSOCIATE;
		if (logging)
		    LogService.log(ctxt, appname, ctxt
			    .getString(R.string.repairing));
		notifyWrap(ctxt, ctxt.getString(R.string.repairing));
		break;
	    }
	    /*
	     * Remove wake lock if there is one
	     */
	    wakelock.lock(false);

	    if (logging) {
		LogService.log(ctxt, appname, ctxt
			.getString(R.string.fix_algorithm)
			+ Integer.toString(wifirepair));
	    }
	}
    };

    /*
     * Sleep tick if wifi is enabled and screenpref
     */
    private Runnable rSleepcheck = new Runnable() {
	public void run() {
	    /*
	     * This is all we want to do.
	     */
	    wakelock.lock(true);
	    checkWifi();
	    /*
	     * Post next run
	     */
	    handlerWrapper(SLEEPCHECK, SLEEPWAIT);
	    wakelock.lock(false);
	}

    };

    /*
     * Scanner runnable
     */
    private Runnable rScan = new Runnable() {
	public void run() {
	    /*
	     * Start scan if nothing is holding a temp lock
	     */
	    if (!templock) {
		startScan(false);
	    } else
		handlerWrapper(SCAN, CONNECTWAIT);

	}

    };

    /*
     * SignalHop runnable
     */
    private Runnable rSignalhop = new Runnable() {
	public void run() {
	    /*
	     * Remove all posts first
	     */
	    wakelock.lock(true);
	    clearQueue();
	    hMain.removeMessages(TEMPLOCK_OFF);
	    /*
	     * Set Lock
	     */
	    handlerWrapper(TEMPLOCK_ON, SHORTWAIT);
	    /*
	     * run the signal hop check
	     */
	    signalHop();
	    /*
	     * Then restore main tick
	     */
	    hMain.sendEmptyMessageDelayed(TEMPLOCK_OFF, SHORTWAIT);
	    wakelock.lock(false);
	}

    };

    private BroadcastReceiver receiver = new BroadcastReceiver() {
	public void onReceive(final Context context, final Intent intent) {

	    /*
	     * Dispatches the broadcast intent to the appropriate handler method
	     */

	    String iAction = intent.getAction();

	    if (iAction.equals(WifiManager.WIFI_STATE_CHANGED_ACTION))
		handleWifiState(intent);
	    else if (iAction
		    .equals(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION))
		handleSupplicantIntent(intent);
	    else if (iAction.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
		handleScanResults();
	    else if (iAction
		    .equals(android.net.ConnectivityManager.CONNECTIVITY_ACTION))
		handleNetworkAction(context);

	}

    };

    public WFConnection(final Context context, PrefUtil p) {
	wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
	prefs = p;
	appname = context.getClass().getName();
	ScreenStateHandler.setOnScreenStateChangedListener(this);
	screenstate = ScreenStateHandler.getScreenState(context);
	logging = prefs.getFlag(Pref.LOG_KEY);
	/*
	 * Cache Context from consumer
	 */
	ctxt = context;

	/*
	 * Set current AP int
	 */
	lastAP = getNetworkID();

	/*
	 * Set up Intent filters
	 */
	IntentFilter myFilter = new IntentFilter(
		WifiManager.WIFI_STATE_CHANGED_ACTION);
	// Supplicant State filter
	myFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);

	// Network State filter
	myFilter.addAction(android.net.ConnectivityManager.CONNECTIVITY_ACTION);

	// wifi scan results available callback
	myFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);

	context.registerReceiver(receiver, myFilter);
	/*
	 * Acquire wifi lock WIFI_MODE_FULL should p. much always be used
	 * acquire lock if pref says we should
	 */
	lock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL, WFLOCK_TAG);
	if (prefs.getFlag(Pref.WIFILOCK_KEY)) {
	    lock.acquire();
	    if (logging)
		LogService.log(context, appname, context
			.getString(R.string.acquiring_wifi_lock));
	}

	// Initialize WakeLock
	wakelock = new WakeLock(context) {

	    @Override
	    public void onAcquire() {
		if (logging)
		    LogService.log(context, appname, context
			    .getString(R.string.acquiring_wake_lock));
		super.onAcquire();
	    }

	    @Override
	    public void onRelease() {
		if (logging)
		    LogService.log(context, appname, context
			    .getString(R.string.releasing_wake_lock));
		super.onRelease();
	    }

	};

	/*
	 * Start Main tick
	 */
	hMain.sendEmptyMessage(MAIN);
    }

    private static boolean checkNetwork(final Context context) {
	boolean isup = false;

	/*
	 * First check if wifi is current network
	 */

	if (!getIsOnWifi(context)) {
	    if (logging)
		LogService.log(context, appname, context
			.getString(R.string.wifi_not_current_network));
	    return false;
	}

	/*
	 * Failover switch
	 */
	isup = icmpHostup(context);
	if (!isup) {
	    isup = httpHostup(context);
	    if (isup)
		wifirepair = W_REASSOCIATE;
	} else
	    wifirepair = W_REASSOCIATE;

	/*
	 * Signal check
	 */

	checkSignal(context);

	return isup;
    }

    private static void checkSignal(final Context context) {
	int signal = wm.getConnectionInfo().getRssi();

	if (signal < DBM_FLOOR) {
	    notifyWrap(context, context.getString(R.string.signal_poor));
	    wm.startScan();
	}

	if (logging)
	    LogService.log(context, appname, context
		    .getString(R.string.current_dbm)
		    + signal);
    }

    private static boolean connectToAP(final Context context,
	    final WFConfig best) {
	/*
	 * Handles connection to network disableOthers should always be true
	 */

	if (logging)
	    LogService.log(context, appname, context
		    .getString(R.string.connecting_to_network)
		    + best.wificonfig.SSID);

	boolean state = wm.enableNetwork(best.wificonfig.networkId, true);

	if (logging) {
	    if (state)
		LogService.log(context, appname, context
			.getString(R.string.connect_succeeded));
	    else
		LogService.log(context, appname, context
			.getString(R.string.connect_failed));
	}

	return state;
    }

    private static int connectToBest(final Context context) {
	/*
	 * Make sure knownbysignal is populated first
	 */
	if (knownbysignal.size() == 0)
	    return NULLVAL;
	/*
	 * Get nth best network id from scanned by connecting and doing a
	 * network check
	 */
	int bestnid = NULLVAL;
	for (WFConfig best : knownbysignal) {
	    bestnid = best.wificonfig.networkId;
	    wm.updateNetwork(WFConfig.sparseConfig(best.wificonfig));
	    if (bestnid == lastAP) {
		if (checkNetwork(context))
		    return bestnid;
		else if (knownbysignal.indexOf(best) == knownbysignal.size() - 1)
		    return NULLVAL;
	    } else if (connectToAP(context, best))
		if (checkNetwork(context)) {
		    if (logging)
			LogService.log(context, appname, context
				.getString(R.string.best_signal_ssid)
				+ best.wificonfig.SSID
				+ context.getString(R.string.signal_level)
				+ best.level
				+ context.getString(R.string.nid)
				+ bestnid);
		    return bestnid;
		}
	}
	return bestnid;
    }

    private static boolean containsBSSID(final String bssid,
	    final List<WFConfig> results) {
	for (WFConfig sResult : results) {
	    if (sResult.wificonfig.BSSID.equals(bssid))
		return true;
	}
	return false;
    }

    private static boolean getHttpHeaders(final Context context)
	    throws IOException, URISyntaxException {

	/*
	 * Performs HTTP HEAD request and returns boolean success or failure
	 */

	boolean isup = false;
	int status = NULLVAL;

	/*
	 * Reusing our Httpclient, only initializing first time
	 */

	if (httpclient == null) {
	    httpclient = new DefaultHttpClient();
	    headURI = new URI(H_TARGET);
	    head = new HttpHead(headURI);
	    httpparams = new BasicHttpParams();
	    HttpConnectionParams.setConnectionTimeout(httpparams, HTTPREACH);
	    HttpConnectionParams.setSoTimeout(httpparams, HTTPREACH);
	    HttpConnectionParams.setLinger(httpparams, REPAIR);
	    HttpConnectionParams.setStaleCheckingEnabled(httpparams, true);
	    httpclient.setParams(httpparams);
	    if (logging)
		LogService.log(context, appname, context
			.getString(R.string.instantiating_httpclient));
	}
	/*
	 * The next two lines actually perform the connection since it's the
	 * same, can re-use.
	 */
	response = httpclient.execute(head);
	status = response.getStatusLine().getStatusCode();
	if (status != NULLVAL)
	    isup = true;
	if (logging) {
	    LogService.log(context, appname, context
		    .getString(R.string.http_status)
		    + status);
	}

	return isup;
    }

    private static boolean getIsOnWifi(final Context context) {
	boolean wifi = false;
	ConnectivityManager cm = (ConnectivityManager) context
		.getSystemService(Context.CONNECTIVITY_SERVICE);
	if (cm.getActiveNetworkInfo() != null
		&& cm.getActiveNetworkInfo().getType() == ConnectivityManager.TYPE_WIFI)
	    wifi = true;
	return wifi;
    }

    private static boolean getIsSupplicantConnected(final Context context) {
	SupplicantState sstate = getSupplicantState();
	if (sstate == null)
	    return false;
	else if (sstate == SupplicantState.ASSOCIATED
		|| sstate == SupplicantState.COMPLETED)
	    return true;
	else
	    return false;
    }

    private static boolean getisWifiEnabled(final Context context) {
	boolean enabled = false;

	if (wm.isWifiEnabled()) {
	    if (logging)
		LogService.log(context, appname, context
			.getString(R.string.wifi_is_enabled));
	    enabled = true;
	} else {
	    if (logging)
		LogService.log(context, appname, context
			.getString(R.string.wifi_is_disabled));
	}

	return enabled;
    }

    private static int getKnownAPsBySignal(final Context context) {
	List<ScanResult> scanResults = wm.getScanResults();
	/*
	 * Catch null if scan results fires after wifi disabled or while wifi is
	 * in intermediate state
	 */
	if (scanResults == null) {
	    if (logging)
		LogService.log(context, appname, context
			.getString(R.string.null_scan_results));
	    return NULLVAL;
	}

	knownbysignal.clear();

	class SortBySignal implements Comparator<WFConfig> {
	    @Override
	    public int compare(WFConfig o2, WFConfig o1) {
		/*
		 * Sort by signal
		 */
		return (o1.level < o2.level ? -1 : (o1.level == o2.level ? 0
			: 1));
	    }
	}
	/*
	 * Known networks from supplicant.
	 */
	final List<WifiConfiguration> wifiConfigs = wm.getConfiguredNetworks();

	/*
	 * Iterate the known networks over the scan results, adding found known
	 * networks.
	 */

	if (logging)
	    LogService.log(context, appname, context
		    .getString(R.string.parsing_scan_results));

	for (ScanResult sResult : scanResults) {
	    for (WifiConfiguration wfResult : wifiConfigs) {
		/*
		 * Using .contains to find sResult.SSID in doublequoted string
		 * 
		 * containsBSSID filters out duplicate MACs in broken scans
		 * (yes, that happens)
		 */
		try {
		    if (wfResult.SSID.contains(sResult.SSID)
			    && !containsBSSID(sResult.BSSID, knownbysignal)) {
			if (logging) {
			    LogService.log(context, appname, context
				    .getString(R.string.found_ssid)
				    + sResult.SSID);
			    LogService.log(context, appname, context
				    .getString(R.string.capabilities)
				    + sResult.capabilities);
			    LogService.log(context, appname, context
				    .getString(R.string.signal_level)
				    + sResult.level);
			}
			/*
			 * Add result to knownbysignal
			 */
			knownbysignal.add(new WFConfig(sResult, wfResult));

		    }
		} catch (NullPointerException e) {
		    if (logging) {
			if (wfResult.SSID == null)
			    LogService.log(context, appname, context
				    .getString(R.string.wfresult_null));
			else if (sResult.SSID == null)
			    LogService.log(context, appname, context
				    .getString(R.string.sresult_null));
		    }
		}
	    }
	}

	if (logging)
	    LogService.log(context, appname, context
		    .getString(R.string.number_of_known)
		    + knownbysignal.size());

	/*
	 * Sort by ScanResult.level which is signal
	 */
	Collections.sort(knownbysignal, new SortBySignal());

	return knownbysignal.size();
    }

    private static int getNetworkID() {
	myWifi = wm.getConnectionInfo();
	return myWifi.getNetworkId();
    }

    private static String getSSID() {
	return wm.getConnectionInfo().getSSID();
    }

    private static SupplicantState getSupplicantState() {
	myWifi = wm.getConnectionInfo();
	return myWifi.getSupplicantState();
    }

    private static void handleNetworkAction(final Context context) {
	/*
	 * This action means network connectivty has changed but, we only want
	 * to run this code for wifi
	 */
	if (!wm.isWifiEnabled() || !getIsOnWifi(context))
	    return;

	icmpCache(context);
    }

    private static boolean handlerCheck(final int hmain) {
	if (templock) {
	    /*
	     * Check if is appropriate post and if lock exists
	     */
	    if (hmain == RECONNECT || hmain == REPAIR || hmain == WIFITASK)
		return false;
	}
	return true;
    }

    private static boolean httpHostup(final Context context) {
	boolean isUp = false;
	/*
	 * getHttpHeaders() does all the heavy lifting
	 */
	if (logging)
	    LogService.log(context, appname, context
		    .getString(R.string.http_method));

	try {
	    isUp = getHttpHeaders(context);
	} catch (IOException e) {
	    try {
		/*
		 * Second try
		 */
		isUp = getHttpHeaders(context);
	    } catch (IOException e1) {
		if (logging)
		    LogService.log(context, appname, context
			    .getString(R.string.httpexception));
	    } catch (URISyntaxException e1) {
		if (logging)
		    LogService.log(context, appname, context
			    .getString(R.string.http_method));
	    }

	} catch (URISyntaxException e) {
	    if (logging)
		LogService.log(context, appname, context
			.getString(R.string.urlexception));
	}

	return isUp;
    }

    private static void icmpCache(final Context context) {
	/*
	 * Caches DHCP gateway IP for ICMP check
	 */
	DhcpInfo info = wm.getDhcpInfo();
	cachedIP = Formatter.formatIpAddress(info.gateway);
	if (prefs.getFlag(Pref.LOG_KEY))
	    LogService.log(context, appname, context
		    .getString(R.string.cached_ip)
		    + cachedIP);
    }

    private static boolean icmpHostup(final Context context) {
	boolean isUp = false;
	/*
	 * If IP hasn't been cached yet cache it
	 */
	if (cachedIP == null)
	    icmpCache(context);

	if (logging)
	    LogService.log(context, appname, context
		    .getString(R.string.icmp_method)
		    + cachedIP);

	try {
	    if (InetAddress.getByName(cachedIP).isReachable(REACHABLE)) {
		isUp = true;
		if (logging)
		    LogService.log(context, appname, context
			    .getString(R.string.icmp_success));
	    }
	} catch (UnknownHostException e) {
	    if (logging)
		LogService.log(context, appname, context
			.getString(R.string.unknownhostexception));
	} catch (IOException e) {
	    if (logging)
		LogService.log(context, appname, context
			.getString(R.string.ioexception));
	}
	return isUp;
    }

    private static void logSupplicant(final Context context, final String state) {

	LogService.log(context, appname, context
		.getString(R.string.supplicant_state)
		+ state);
	if (wm.pingSupplicant()) {
	    LogService.log(context, appname, context
		    .getString(R.string.supplicant_responded));
	} else {
	    LogService.log(context, appname, context
		    .getString(R.string.supplicant_nonresponsive));

	}

	LogService.log(context, appname, context.getString(R.string.ssid)
		+ getSSID());

    }

    private static void networkNotify(final Context context) {
	final int NUM_SSIDS = 3;
	final int SSID_LENGTH = 10;
	final List<ScanResult> wifiList = wm.getScanResults();
	String ssid = EMPTYSTRING;
	String signal = EMPTYSTRING;
	int n = 0;
	for (ScanResult sResult : wifiList) {
	    if (sResult.capabilities.length() == W_REASSOCIATE && n < NUM_SSIDS) {
		if (sResult.SSID.length() > SSID_LENGTH)
		    ssid = ssid + sResult.SSID.substring(0, SSID_LENGTH)
			    + NEWLINE;
		else
		    ssid = ssid + sResult.SSID + NEWLINE;

		signal = signal + sResult.level + NEWLINE;
		n++;
	    }
	}
	NotifUtil.addNetNotif(context, ssid, signal);
    }

    private static void notifyWrap(final Context context, final String message) {
	if (prefs.getFlag(Pref.NOTIF_KEY)) {
	    NotifUtil.show(context, context
		    .getString(R.string.wifi_connection_problem)
		    + message, message, ERR_NOTIF, PendingIntent.getActivity(
		    context, 0, new Intent(), 0));
	}

    }

    private void checkWifi() {
	if (getisWifiEnabled(ctxt)) {
	    if (getIsSupplicantConnected(ctxt)) {
		if (!checkNetwork(ctxt)) {
		    shouldrepair = true;
		    handlerWrapper(TEMPLOCK_OFF);
		    handlerWrapper(SCAN);
		}
	    } else {
		if (screenisoff)
		    startScan(true);
		else
		    pendingscan = true;
	    }

	}

    }

    private void cleanupPosts() {
	hMain.removeMessages(RECONNECT);
	hMain.removeMessages(REPAIR);
	hMain.removeMessages(WIFITASK);
	hMain.removeMessages(TEMPLOCK_ON);
    }

    private void clearQueue() {
	hMain.removeMessages(RECONNECT);
	hMain.removeMessages(REPAIR);
	hMain.removeMessages(WIFITASK);
	pendingscan = false;
	pendingreconnect = false;
	shouldrepair = false;
    }

    /*
     * Lets us control duplicate posts and odd handler behavior when screen is
     * off
     */
    private boolean handlerWrapper(final int hmain) {
	if (handlerCheck(hmain)) {
	    hMain.removeMessages(hmain);
	    if (!screenstate)
		return hMain.sendEmptyMessage(hmain);
	    else
		return hMain.sendEmptyMessageDelayed(hmain, REALLYSHORTWAIT);

	} else {
	    hMain.removeMessages(hmain);
	    return hMain.sendEmptyMessageDelayed(hmain, REACHABLE);
	}
    }

    private boolean handlerWrapper(final int hmain, final long delay) {
	if (handlerCheck(hmain)) {
	    hMain.removeMessages(hmain);
	    return hMain.sendEmptyMessageDelayed(hmain, delay);
	} else {
	    hMain.removeMessages(hmain);
	    return hMain.sendEmptyMessageDelayed(hmain, delay + REACHABLE);
	}
    }

    private void handleScanResults() {
	if (!wm.isWifiEnabled())
	    return;

	if (!pendingscan) {
	    if (getIsOnWifi(ctxt)) {
		/*
		 * We're on wifi, so we want to check for better signal
		 */
		handlerWrapper(SIGNALHOP);
		return;
	    } else {
		/*
		 * Network notification check
		 */
		if (prefs.getFlag(Pref.NETNOT_KEY)) {
		    if (logging)
			LogService.log(ctxt, appname, ctxt
				.getString(R.string.network_notification_scan));
		    networkNotify(ctxt);
		}
	    }
	}

	if (!pendingreconnect) {
	    /*
	     * Service called the scan: dispatch appropriate runnable
	     */
	    pendingscan = false;
	    handlerWrapper(TEMPLOCK_OFF);
	    handlerWrapper(REPAIR);
	    if (logging)
		LogService.log(ctxt, appname, ctxt
			.getString(R.string.repairhandler));
	} else {
	    pendingscan = false;
	    handlerWrapper(RECONNECT);
	    if (logging)
		LogService.log(ctxt, appname, ctxt
			.getString(R.string.reconnecthandler));
	}

    }

    private void handleSupplicantIntent(final Intent intent) {

	/*
	 * Get Supplicant New State
	 */
	String sState = intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE)
		.toString();

	/*
	 * Flush queue if connected
	 * 
	 * Also clear any error notifications
	 */
	if (sState == COMPLETED) {
	    clearQueue();
	    NotifUtil.cancel(ERR_NOTIF, ctxt);
	    NotifUtil.cancel(NETNOTIFID, ctxt);
	    pendingscan = false;
	    pendingreconnect = false;
	    lastAP = getNetworkID();
	    return;
	}

	/*
	 * New setting disabling supplicant fixes
	 */
	if (prefs.getFlag(Pref.SUPFIX_KEY))
	    return;

	/*
	 * The actual meat of the supplicant fixes
	 */
	handleSupplicantState(sState);

    }

    private void handleSupplicantState(final String sState) {

	/*
	 * Dispatches appropriate supplicant fix
	 */

	if (!wm.isWifiEnabled()) {
	    return;
	} else if (screenstate && !prefs.getFlag(Pref.SCREEN_KEY))
	    return;
	else if (sState == DISCONNECTED) {
	    startScan(true);
	    notifyWrap(ctxt, sState);
	} else if (sState == INACTIVE) {
	    supplicantFix(true);
	    notifyWrap(ctxt, sState);
	}

	if (logging && !screenisoff)
	    logSupplicant(ctxt, sState);
    }

    private void handleWifiState(final Intent intent) {
	// What kind of state change is it?
	int state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
		WifiManager.WIFI_STATE_UNKNOWN);
	switch (state) {
	case WifiManager.WIFI_STATE_ENABLED:
	    if (prefs.getFlag(Pref.LOG_KEY))
		LogService.log(ctxt, appname, ctxt
			.getString(R.string.wifi_state_enabled));
	    onWifiEnabled();
	    break;
	case WifiManager.WIFI_STATE_ENABLING:
	    if (prefs.getFlag(Pref.LOG_KEY))
		LogService.log(ctxt, appname, ctxt
			.getString(R.string.wifi_state_enabling));
	    break;
	case WifiManager.WIFI_STATE_DISABLED:
	    if (prefs.getFlag(Pref.LOG_KEY))
		LogService.log(ctxt, appname, ctxt
			.getString(R.string.wifi_state_disabled));
	    onWifiDisabled();
	    break;
	case WifiManager.WIFI_STATE_DISABLING:
	    if (prefs.getFlag(Pref.LOG_KEY))
		LogService.log(ctxt, appname, ctxt
			.getString(R.string.wifi_state_disabling));
	    break;
	case WifiManager.WIFI_STATE_UNKNOWN:
	    if (prefs.getFlag(Pref.LOG_KEY))
		LogService.log(ctxt, appname, ctxt
			.getString(R.string.wifi_state_unknown));
	    break;
	}
    }

    private void n1Fix() {
	/*
	 * Nexus One Sleep Fix duplicating widget function
	 */
	if (wm.isWifiEnabled() && !screenstate) {
	    toggleWifi();
	}
    }

    private void onScreenOff() {
	/*
	 * Disable Sleep check
	 */
	if (prefs.getFlag(Pref.SCREEN_KEY))
	    sleepCheck(true);
	/*
	 * Schedule N1 fix
	 */
	if (prefs.getFlag(Pref.N1FIX2_KEY))
	    handlerWrapper(N1CHECK, REACHABLE);

	if (logging) {
	    LogService.log(ctxt, appname, ctxt
		    .getString(R.string.screen_off_handler));
	}
    }

    private void onScreenOn() {

	sleepCheck(false);
	if (logging) {
	    LogService.log(ctxt, appname, ctxt
		    .getString(R.string.screen_on_handler));
	}
    }

    @Override
    public void onScreenStateChanged(boolean state) {
	screenstate = state;

	if (state)
	    onScreenOn();
	else
	    onScreenOff();
    }

    private void onWifiDisabled() {
	// TODO Auto-generated method stub

    }

    private void onWifiEnabled() {
	// TODO Auto-generated method stub

    }

    private void signalHop() {
	/*
	 * Walks the list of known APs in the scan results by signal connects,
	 * network check pass: stay connected network check fail, try next until
	 * list exhausted
	 * 
	 * If there are not alternate APs just does a wifi repair.
	 */

	if (getisWifiEnabled(ctxt))
	    if (getIsSupplicantConnected(ctxt))
		if (checkNetwork(ctxt)) {
		    /*
		     * Network is fine
		     */
		    return;
		}

	int bestap = NULLVAL;
	int numKnownAPs = getKnownAPsBySignal(ctxt);
	if (numKnownAPs > 1) {
	    bestap = connectToBest(ctxt);

	    if (bestap == NULLVAL) {
		if (logging)
		    LogService.log(ctxt, appname, ctxt
			    .getString(R.string.signalhop_no_result));
		handlerWrapper(TEMPLOCK_OFF);
		wifiRepair();
		return;
	    } else {
		if (logging) {
		    LogService.log(ctxt, appname, ctxt
			    .getString(R.string.hopping)
			    + bestap);
		    LogService.log(ctxt, appname, ctxt.getString(R.string.nid)
			    + lastAP);
		}
	    }
	    return;
	}

	if (logging)
	    LogService.log(ctxt, appname, ctxt
		    .getString(R.string.signalhop_nonetworks));
	handlerWrapper(TEMPLOCK_OFF);
	shouldrepair = true;
	wifiRepair();

    }

    private void sleepCheck(final boolean state) {
	if (state && wm.isWifiEnabled()) {
	    /*
	     * Start sleep check
	     */
	    handlerWrapper(SLEEPCHECK, SLEEPWAIT);

	} else {
	    /*
	     * Screen is on, remove any posts
	     */
	    hMain.removeMessages(SLEEPCHECK);
	    /*
	     * Check state
	     */
	    handlerWrapper(MAIN, SHORTWAIT);
	}

    }

    private void startScan(final boolean pending) {
	// We want a lock after a scan
	pendingscan = pending;
	wm.startScan();
	if (logging)
	    LogService.log(ctxt, appname, ctxt
		    .getString(R.string.initiating_scan));
	tempLock(LOCKWAIT);
    }

    private void supplicantFix(final boolean wftoggle) {
	// Toggling wifi fixes the supplicant
	if (wftoggle)
	    toggleWifi();
	startScan(true);
	if (logging)
	    LogService.log(ctxt, appname, ctxt
		    .getString(R.string.running_supplicant_fix));
    }

    private void tempLock(final int time) {

	handlerWrapper(TEMPLOCK_ON);
	// Queue for later
	handlerWrapper(TEMPLOCK_OFF, time);
    }

    private void toggleWifi() {
	if (pendingwifitoggle)
	    return;

	pendingwifitoggle = true;
	cleanupPosts();
	tempLock(CONNECTWAIT);
	/*
	 * Send Toggle request to broadcastreceiver
	 */

	ctxt.sendBroadcast(new Intent(IntentConstants.ACTION_WIFI_TOGGLE));
    }

    private void wifiRepair() {
	if (!shouldrepair)
	    return;

	if (!screenstate) {
	    /*
	     * Start Wifi Task
	     */
	    handlerWrapper(WIFITASK);
	    if (logging)
		LogService.log(ctxt, appname, ctxt
			.getString(R.string.running_wifi_repair));
	} else {
	    /*
	     * if screen off, try wake lock then resubmit to handler
	     */
	    wakelock.lock(true);
	    handlerWrapper(WIFITASK);
	    if (logging)
		LogService.log(ctxt, appname, ctxt
			.getString(R.string.wifi_repair_post_failed));
	}

	shouldrepair = false;

    }

    public void wifiLock(final Boolean state) {
	if (state)
	    lock.acquire();
	else
	    lock.release();
    }

    public boolean wifiLockHeld() {
	return lock.isHeld();
    }

}
