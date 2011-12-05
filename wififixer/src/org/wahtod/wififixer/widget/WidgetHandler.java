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

package org.wahtod.wififixer.widget;

import org.wahtod.wififixer.R;
import org.wahtod.wififixer.ToggleService;
import org.wahtod.wififixer.WFConnection;
import org.wahtod.wififixer.prefs.PrefUtil;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.widget.Toast;

public class WidgetHandler {

    private static Context ctxt;

    /*
     * Intent Constants
     */
    public static final String WIFI_ON = "org.wahtod.wififixer.WidgetReceiver.WIFI_ON";
    public static final String WIFI_OFF = "org.wahtod.wififixer.WidgetReceiver.WIFI_OFF";
    public static final String TOGGLE_WIFI = "org.wahtod.wififixer.WidgetReceiver.WIFI_TOGGLE";
    public static final String REASSOCIATE = "org.wahtod.wififixer.WidgetReceiver.WIFI_REASSOCIATE";

    private static WifiManager wm;

    private Handler handler = new Handler() {
	@Override
	public void handleMessage(Message message) {
	    String action = message.getData().getString(PrefUtil.INTENT_ACTION);
	    /*
	     * Turn on WIFI
	     */
	    if (action.equals(WIFI_ON))
		setWifiState(ctxt, true);
	    else
	    /*
	     * If Wifi is disabled, notify
	     */
	    if (!getWifiManager(ctxt).isWifiEnabled()) {
		Toast.makeText(ctxt, ctxt.getString(R.string.wifi_is_disabled),
			Toast.LENGTH_LONG).show();
		return;
	    }
	    /*
	     * Turn off Wifi
	     */
	    else if (action.equals(WIFI_OFF))
		setWifiState(ctxt, false);
	    /*
	     * Toggle Wifi
	     */
	    else if (action.equals(TOGGLE_WIFI)) {
		ctxt.startService(new Intent(ctxt, ToggleService.class));
	    }
	    /*
	     * Reassociate
	     */
	    else if (action.equals(REASSOCIATE)) {
		Toast.makeText(ctxt, ctxt.getString(R.string.reassociating),
			Toast.LENGTH_LONG).show();
		ctxt.sendBroadcast(new Intent(WFConnection.USEREVENT));
		getWifiManager(ctxt).reassociate();
	    }

	}
    };

    public static WifiManager getWifiManager(final Context context) {
	if (wm == null)
	    wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
	return wm;
    }

    public static void setWifiState(final Context context, final boolean state) {
	getWifiManager(context).setWifiEnabled(state);
    }

    public void handleIntent(final Context context, final Intent intent) {
	/*
	 * Dispatch intent commands to handler
	 */
	Message message = handler.obtainMessage();
	Bundle data = new Bundle();
	data.putString(PrefUtil.INTENT_ACTION, intent.getAction());
	message.setData(data);
	handler.sendMessage(message);
    }

    public WidgetHandler(final Context context) {
	ctxt = context;
    }

}