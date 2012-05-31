/*
 * Copyright (C) 2010 The Android Open Source Project
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

package android.net.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.LinkAddress;
import android.net.LinkCapabilities;
import android.net.LinkProperties;
import android.net.NetworkInfo;
import android.net.NetworkStateTracker;
import android.os.Handler;
import android.os.Message;
import android.util.Slog;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Track the state of wifi for connectivity service.
 *
 * @hide
 */
public class WifiStateTracker implements NetworkStateTracker {

    private static final String NETWORKTYPE = "WIFI";
    private static final String TAG = "MeshedWifiStateTracker";

    private static final boolean LOGV = true;

    private AtomicBoolean mTeardownRequested = new AtomicBoolean(false);
    private AtomicBoolean mPrivateDnsRouteSet = new AtomicBoolean(false);
    private AtomicBoolean mDefaultRouteSet = new AtomicBoolean(false);

    private LinkProperties mLinkProperties;
    private LinkCapabilities mLinkCapabilities;
    private NetworkInfo mNetworkInfo;
    private NetworkInfo.State mLastState = NetworkInfo.State.UNKNOWN;

    /* For sending events to connectivity service handler */
    private Handler mCsHandler;
    private Context mContext;
    private BroadcastReceiver mWifiStateReceiver;
    private WifiManager mWifiManager;

    public WifiStateTracker(int netType, String networkName) {
        mNetworkInfo = new NetworkInfo(netType, 0, networkName, "");
        mLinkProperties = new LinkProperties();
        mLinkCapabilities = new LinkCapabilities();

        mNetworkInfo.setIsAvailable(false);
        setTeardownRequested(false);
    }


    public void setTeardownRequested(boolean isRequested) {
        mTeardownRequested.set(isRequested);
    }

    public boolean isTeardownRequested() {
        return mTeardownRequested.get();
    }

    /**
     * Begin monitoring wifi connectivity
     */
    public void startMonitoring(Context context, Handler target) {
        mCsHandler = target;
        mContext = context;

        mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);

        mWifiStateReceiver = new WifiStateReceiver();
        mContext.registerReceiver(mWifiStateReceiver, filter);
    }

    /**
     * Disable connectivity to a network
     * TODO: do away with return value after making MobileDataStateTracker async
     */
    public boolean teardown() {
        mTeardownRequested.set(true);
        mWifiManager.stopWifi();
        return true;
    }

    /**
     * Re-enable connectivity to a network after a {@link #teardown()}.
     */
    public boolean reconnect() {
        mTeardownRequested.set(false);
        mWifiManager.startWifi();
        return true;
    }

    /**
     * Turn the wireless radio off for a network.
     * @param turnOn {@code true} to turn the radio on, {@code false}
     */
    public boolean setRadio(boolean turnOn) {
        mWifiManager.setWifiEnabled(turnOn);
        return true;
    }

    /**
     * Wi-Fi is considered available as long as we have a connection to the
     * supplicant daemon and there is at least one enabled network. If a teardown
     * was explicitly requested, then Wi-Fi can be restarted with a reconnect
     * request, so it is considered available. If the driver has been stopped
     * for any reason other than a teardown request, Wi-Fi is considered
     * unavailable.
     * @return {@code true} if Wi-Fi connections are possible
     */
    public boolean isAvailable() {
        return mNetworkInfo.isAvailable();
    }

    @Override
    public void setUserDataEnable(boolean enabled) {
        Slog.w(TAG, "ignoring setUserDataEnable(" + enabled + ")");
    }

    @Override
    public void setPolicyDataEnable(boolean enabled) {
        Slog.w(TAG, "ignoring setPolicyDataEnable(" + enabled + ")");
    }

    /**
     * Check if private DNS route is set for the network
     */
    public boolean isPrivateDnsRouteSet() {
        return mPrivateDnsRouteSet.get();
    }

    /**
     * Set a flag indicating private DNS route is set
     */
    public void privateDnsRouteSet(boolean enabled) {
        mPrivateDnsRouteSet.set(enabled);
    }

    /**
     * Fetch NetworkInfo for the network
     */
    public NetworkInfo getNetworkInfo() {
        return new NetworkInfo(mNetworkInfo);
    }

    /**
     * Fetch LinkProperties for the network
     */
    public LinkProperties getLinkProperties() {
        return new LinkProperties(mLinkProperties);
    }

    /**
     * A capability is an Integer/String pair, the capabilities
     * are defined in the class LinkSocket#Key.
     *
     * @return a copy of this connections capabilities, may be empty but never null.
     */
    public LinkCapabilities getLinkCapabilities() {
        return new LinkCapabilities(mLinkCapabilities);
    }

    /**
     * Check if default route is set
     */
    public boolean isDefaultRouteSet() {
        return mDefaultRouteSet.get();
    }

    /**
     * Set a flag indicating default route is set for the network
     */
    public void defaultRouteSet(boolean enabled) {
        mDefaultRouteSet.set(enabled);
    }

    /**
     * Return the system properties name associated with the tcp buffer sizes
     * for this network.
     */
    public String getTcpBufferSizesPropName() {
        return "net.tcp.buffersize.wifi";
    }

	private InetAddress getMeshInetAddress() {
    	try {
    		NetworkInterface ni = NetworkInterface.getByName("mesh0");
    		if (ni.isUp()) {
    			List<InetAddress> ipList = Collections.list((Enumeration<InetAddress>)ni.getInetAddresses());
    			 
    		    for ( InetAddress ip : ipList){
    		    	// Only IPV4 address ??
    		    	if (ip.getAddress().length == 4) {
    		    		return ip;
    		    	}
    		    }
    		}
		} catch (SocketException e) {
			e.printStackTrace();
		}
    	return null;
	}
    
    private class WifiStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {

             if (intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
            	 
                mNetworkInfo = (NetworkInfo) intent.getParcelableExtra(
                        WifiManager.EXTRA_NETWORK_INFO);
                                
                mLinkProperties = intent.getParcelableExtra(
                        WifiManager.EXTRA_LINK_PROPERTIES);
                if (mLinkProperties == null) {
                    mLinkProperties = new LinkProperties();
                }
                
                mLinkCapabilities = intent.getParcelableExtra(
                        WifiManager.EXTRA_LINK_CAPABILITIES);
                if (mLinkCapabilities == null) {
                    mLinkCapabilities = new LinkCapabilities();
                }
                // don't want to send redundant state messages
                // TODO can this be fixed in WifiStateMachine?
                NetworkInfo.State state = mNetworkInfo.getState();
                if (mLastState == state) {
                    return;
                } else {
                    mLastState = state;
                    // This is a workaround to add the correct IP 
                    // in the LinkProperties when we are CONNECTED
                	if (mNetworkInfo.isConnected() &&  mLinkProperties.getAddresses().size() == 0 ) {
                		LinkAddress address = new LinkAddress(getMeshInetAddress(), 8);
                		mLinkProperties.addLinkAddress(address);
                	} else if (!mNetworkInfo.isConnected()) {
                		mLinkProperties.clear();
                	}
                }
                Message msg = mCsHandler.obtainMessage(EVENT_STATE_CHANGED,
                        new NetworkInfo(mNetworkInfo));
                msg.sendToTarget();
            } 
        }
    }

    public void setDependencyMet(boolean met) {
        // not supported on this network
    }
}
