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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.widget.RemoteViews;

public class NotifUtil {
    private static final int NETNOTIFID = 8236;
    private static final int STATNOTIFID = 2392;

    public static void addNetNotif(final Context context, final String ssid,
	    final String signal) {
	NotificationManager nm = (NotificationManager) context
		.getSystemService(Context.NOTIFICATION_SERVICE);

	Intent intent = new Intent(WifiManager.ACTION_PICK_WIFI_NETWORK);
	PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
		intent, 0);

	Notification notif = new Notification(R.drawable.wifi_ap, context
		.getString(R.string.open_network_found), System
		.currentTimeMillis());
	if (ssid.length() > 0) {
	    RemoteViews contentView = new RemoteViews(context.getPackageName(),
		    R.layout.netnotif_layout);
	    contentView.setTextViewText(R.id.ssid, ssid);
	    contentView.setTextViewText(R.id.signal, signal);
	    notif.contentView = contentView;
	    notif.contentIntent = contentIntent;
	    notif.flags = Notification.FLAG_ONGOING_EVENT;
	    notif.tickerText = context.getText(R.string.open_network_found);
	    /*
	     * Fire notification, cancel if message empty: means no open APs
	     */
	    nm.notify(NETNOTIFID, notif);
	} else
	    nm.cancel(NETNOTIFID);

    }
    
    public static void addStatNotif(final Context context, final String ssid,
	    final String status) {
	NotificationManager nm = (NotificationManager) context
		.getSystemService(Context.NOTIFICATION_SERVICE);

	Intent intent = new Intent(WifiManager.ACTION_PICK_WIFI_NETWORK);
	PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
		intent, 0);

	Notification notif = new Notification(R.drawable.icon, context.getString(R.string.network_status), System
		.currentTimeMillis());
	if (ssid.length() > 0) {
	    RemoteViews contentView = new RemoteViews(context.getPackageName(),
		    R.layout.connection_notif_layout);
	    contentView.setTextViewText(R.id.ssid, ssid);
	    contentView.setTextViewText(R.id.status, status);
	    notif.contentView = contentView;
	    notif.contentIntent = contentIntent;
	    notif.flags = Notification.FLAG_ONGOING_EVENT;
	    notif.tickerText = context.getText(R.string.network_status);
	    /*
	     * Fire notification, cancel if message empty: means no status info
	     */
	    nm.notify(STATNOTIFID, notif);
	} else
	    nm.cancel(STATNOTIFID);

    }

    public static void show(final Context context, final String message,
	    final String tickerText, final int id,
	    final PendingIntent contentIntent) {

	NotificationManager nm = (NotificationManager) context
		.getSystemService(Context.NOTIFICATION_SERVICE);

	CharSequence from = context.getText(R.string.app_name);

	Notification notif = new Notification(R.drawable.icon, tickerText,
		System.currentTimeMillis());

	notif.setLatestEventInfo(context, from, message, contentIntent);
	notif.flags = Notification.FLAG_AUTO_CANCEL;
	// unique ID
	nm.notify(id, notif);

    }

    public static void cancel(final int notif, final Context context) {
	NotificationManager nm = (NotificationManager) context
		.getSystemService(Context.NOTIFICATION_SERVICE);
	nm.cancel(notif);
    }
}
