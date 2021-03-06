/*	    Wifi Fixer for Android
    Copyright (C) 2010-2013  David Van de Ven

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see http://www.gnu.org/licenses
 */

package org.wahtod.wififixer.ui;

import java.lang.ref.WeakReference;
import org.wahtod.wififixer.R;
import org.wahtod.wififixer.prefs.PrefConstants.Pref;
import org.wahtod.wififixer.prefs.PrefUtil;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ToggleButton;

public class ServiceFragment extends Fragment implements
		OnCheckedChangeListener {
	private static final int _CHECK_CHANGED_POST_DELAY = 3000;
	public static final String REFRESH_ACTION = "org.wahtod.wififixer.ui.ServiceFragment.REFRESH";
	private ToggleButton servicebutton;
	private ToggleButton wifibutton;
	private static WeakReference<ServiceFragment> self;
	private static Handler handler = new Handler() {
		@Override
		public void handleMessage(Message message) {
			if (self.get().getActivity() == null)
				return;
			if (message.getData().isEmpty())
				self.get().setIcons();
		}
	};
	private BroadcastReceiver wifireceiver = new BroadcastReceiver() {
		public void onReceive(final Context context, final Intent intent) {

			/*
			 * Dispatch intent commands to handler
			 */
			Message message = handler.obtainMessage();
			Bundle data = new Bundle();
			if (intent.getExtras() != null) {
				data.putString(PrefUtil.INTENT_ACTION, intent.getAction());
				data.putAll(intent.getExtras());
			}
			message.setData(data);
			handler.sendMessage(message);
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		self = new WeakReference<ServiceFragment>(this);
		super.onCreate(savedInstanceState);
	}

	@Override
	public void onPause() {
		unregisterReceiver();
		super.onPause();
	}

	private void unregisterReceiver() {
		getContext().unregisterReceiver(wifireceiver);
	}

	private void registerReceiver() {
		IntentFilter filter = new IntentFilter(REFRESH_ACTION);
		getContext().registerReceiver(wifireceiver, filter);
	}

	@Override
	public void onResume() {
		super.onResume();
		registerReceiver();
		setIcons();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.service, null);
		servicebutton = (ToggleButton) v.findViewById(R.id.ToggleButton1);
		servicebutton.setOnCheckedChangeListener(this);
		wifibutton = (ToggleButton) v.findViewById(R.id.ToggleButton2);
		wifibutton.setOnCheckedChangeListener(this);
		return v;
	}

	private Context getContext() {
		return getActivity();
	}

	private void setIcons() {
		/*
		 * Draw icons
		 */
		handler.post(DrawIcons);
	}

	private Runnable DrawIcons = new Runnable() {

		@Override
		public void run() {
			wifibutton.setChecked(PrefUtil.getWifiManager(getContext())
					.isWifiEnabled());
			servicebutton.setChecked(!PrefUtil.readBoolean(getContext(),
					Pref.DISABLE_KEY.key()));
		}
	};

	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		handler.postDelayed(DrawIcons, _CHECK_CHANGED_POST_DELAY);
	}

}
