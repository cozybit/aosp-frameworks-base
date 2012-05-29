package com.android.server;

import static android.net.wifi.WifiManager.WIFI_STATE_DISABLED;
import static android.net.wifi.WifiManager.WIFI_STATE_ENABLED;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.LinkProperties;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.NetworkUtils;
import android.net.wifi.IWifiManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.mesh.IMeshTestUI;
import android.net.wifi.mesh.MeshManager;
import android.net.wifi.mesh.MeshManager.MeshNode;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.WorkSource;
import android.provider.Settings;
import android.util.Slog;

import com.android.internal.app.IBatteryStats;
import com.android.internal.util.AsyncChannel;

public class MeshedWifiService extends IWifiManager.Stub {

    private static final String TAG = "WifiService";
    
    private List<AsyncChannel> mClients = new ArrayList<AsyncChannel>();
        
    private static final int WIFI_DISABLED = 0;
    private static final int WIFI_ENABLED = 1;
    
    private static final int MAX_RSSI = 256;
    
    private final IBatteryStats mBatteryStats;
    
    private final AtomicInteger mWifiState = new AtomicInteger(WIFI_STATE_DISABLED);
    private AtomicInteger mPersistWifiState = new AtomicInteger(WIFI_DISABLED);
    
    private NetworkInfo mNetworkInfo;
    
    private class AsyncServiceHandler extends Handler {

        AsyncServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case AsyncChannel.CMD_CHANNEL_HALF_CONNECTED: {
                    if (msg.arg1 == AsyncChannel.STATUS_SUCCESSFUL) {
                        Slog.d(TAG, "New client listening to asynchronous messages");
                        mClients.add((AsyncChannel) msg.obj);
                    } else {
                        Slog.e(TAG, "Client connection failure, error=" + msg.arg1);
                    }
                    break;
                }
                case AsyncChannel.CMD_CHANNEL_DISCONNECTED: {
                    if (msg.arg1 == AsyncChannel.STATUS_SEND_UNSUCCESSFUL) {
                        Slog.d(TAG, "Send failed, client connection lost");
                    } else {
                        Slog.d(TAG, "Client connection lost with reason: " + msg.arg1);
                    }
                    mClients.remove((AsyncChannel) msg.obj);
                    break;
                }
                case AsyncChannel.CMD_CHANNEL_FULL_CONNECTION: {
                    AsyncChannel ac = new AsyncChannel();
                    ac.connect(mContext, this, msg.replyTo);
                    break;
                }
                default: {
                    Slog.d(TAG, "WifiServicehandler.handleMessage ignoring msg=" + msg);
                    break;
                }
            }
        }
    }
    private AsyncServiceHandler mAsyncServiceHandler;
    
    private Context mContext;

	private MeshManager mMeshManager;

    MeshedWifiService(Context context) {
        mContext = context;
        HandlerThread wifiThread = new HandlerThread("MeshedWifiService");
        wifiThread.start();
        mAsyncServiceHandler = new AsyncServiceHandler(wifiThread.getLooper());
        
        mBatteryStats = IBatteryStats.Stub.asInterface(ServiceManager.getService("batteryinfo"));
        mNetworkInfo = new NetworkInfo(ConnectivityManager.TYPE_WIFI, 0, "WIFI", "");
        
        mMeshManager = new MeshManager(new IMeshTestUI() {
			
			@Override
			public void promptDialog(int dialogID) {
				// Nothing to do here
			}
			
			@Override
			public void notifyStationDumpEnabled() {
				// Nothing to do here
			}
			
			@Override
			public void notifyStationDumpDisabled() {
				// Nothing to do here
			}
			
			@Override
			public void notifyPeerInfo(MeshNode node) {
				// Nothing to do here
			}
			
			@Override
			public void notifyMeshEnabled() {
				// We set & notify that wifi is enabled
				setWifiState(WIFI_STATE_ENABLED);
				// We set mNetworkInfo to DatailedState.CONNECTED
				setNetworkDetailedState(DetailedState.CONNECTED);
				// We notify the NetworkState change
				sendNetworkStateChangeBroadcast(null);
				// We send a sticky MAX_RSSI value
				sendRssiChangeBroadcast(MAX_RSSI);
			}
			
			@Override
			public void notifyMeshDisabled() {
				// We set mNetworkInfo to DatailedState.DISCONNECTED
				setNetworkDetailedState(DetailedState.DISCONNECTED);
				// We notify the NetworkState change
				sendNetworkStateChangeBroadcast(null);
				// We set & notify that wifi is disabled
				setWifiState(WIFI_STATE_DISABLED);

			}
		});
        
    }
    
	@Override
	public List<WifiConfiguration> getConfiguredNetworks()
			throws RemoteException {
		List<WifiConfiguration> networks = new ArrayList<WifiConfiguration>();
		return networks;
	}

	@Override
	public int addOrUpdateNetwork(WifiConfiguration config)
			throws RemoteException {
		// Ignoring this
		return 0;
	}

	@Override
	public boolean removeNetwork(int netId) throws RemoteException {
		// Ignoring this
		return false;
	}

	@Override
	public boolean enableNetwork(int netId, boolean disableOthers)
			throws RemoteException {
		// Ignoring this
		return false;
	}

	@Override
	public boolean disableNetwork(int netId) throws RemoteException {
		// Ignoring this
		return false;
	}

	@Override
	public boolean pingSupplicant() throws RemoteException {
		// Ignoring this
		return true;
	}

	@Override
	public void startScan(boolean forceActive) throws RemoteException {
		// Ignoring this
	}

	@Override
	public List<ScanResult> getScanResults() throws RemoteException {
		List<ScanResult> scanList = new ArrayList<ScanResult>();
		return scanList;
	}

	@Override
	public void disconnect() throws RemoteException {
		// Ignoring this
	}

	@Override
	public void reconnect() throws RemoteException {
		// Ignoring this
	}

	@Override
	public void reassociate() throws RemoteException {
		// Ignoring this
	}

	@Override
	public WifiInfo getConnectionInfo() throws RemoteException {
		WifiInfo[] wifiInfo = WifiInfo.CREATOR.newArray(1);
		return wifiInfo[0];
	}

	@Override
	public int getWifiEnabledState() throws RemoteException {
		if (mMeshManager.isMeshActive())
			return WifiManager.WIFI_STATE_ENABLED;
		return WifiManager.WIFI_STATE_DISABLED;
	}

	@Override
	public void setCountryCode(String country, boolean persist)
			throws RemoteException {		
	}

	@Override
	public void setFrequencyBand(int band, boolean persist)
			throws RemoteException {		
	}

	@Override
	public int getFrequencyBand() throws RemoteException {
		return WifiManager.WIFI_FREQUENCY_BAND_2GHZ;
	}

	@Override
	public boolean isDualBandSupported() throws RemoteException {
		return false;
	}

	@Override
	public boolean saveConfiguration() throws RemoteException {
		return true;
	}

    private int convertToInt(String addr) {
        if (addr != null) {
            try {
                InetAddress inetAddress = NetworkUtils.numericToInetAddress(addr);
                if (inetAddress instanceof Inet4Address) {
                    return NetworkUtils.inetAddressToInt(inetAddress);
                }
            } catch (IllegalArgumentException e) {}
        }
        return 0;
    }

	@Override
	public DhcpInfo getDhcpInfo() throws RemoteException {
		
		String ipAddress = "192.168.1.5";
	    String dns1 = "192.168.1.1";
	    String dns2 = "192.168.1.1";
	    String gateway = "192.168.1.1";
	    String serverAddress = "192.168.1.1";
	    int prefixLength = 4;
	    int leaseDuration = 3600;
		
		DhcpInfo info = new DhcpInfo();
        info.ipAddress = convertToInt(ipAddress);
        info.gateway = convertToInt(gateway);
        try {
            InetAddress inetAddress = NetworkUtils.numericToInetAddress(ipAddress);
            info.netmask = NetworkUtils.prefixLengthToNetmaskInt(prefixLength);
        } catch (IllegalArgumentException e) {}
        info.dns1 = convertToInt(dns1);
        info.dns2 = convertToInt(dns2);
        info.serverAddress = convertToInt(serverAddress);
        info.leaseDuration = leaseDuration;
		
		return info;
	}

	@Override
    public boolean acquireWifiLock(IBinder binder, int lockMode, String tag, WorkSource ws) {
		return true;
    }

	@Override
	public void updateWifiLockWorkSource(IBinder lock, WorkSource ws)
			throws RemoteException {	
	}

	@Override
	public boolean releaseWifiLock(IBinder lock) throws RemoteException {
		return true;
	}

	@Override
	public void initializeMulticastFiltering() throws RemoteException {	
	}

	@Override
	public boolean isMulticastEnabled() throws RemoteException {
		return true;
	}

	@Override
	public void acquireMulticastLock(IBinder binder, String tag)
			throws RemoteException {	
	}

	@Override
	public void releaseMulticastLock() throws RemoteException {		
	}

	@Override
	public void setWifiApEnabled(WifiConfiguration wifiConfig, boolean enable)
			throws RemoteException {		
	}

	@Override
	public int getWifiApEnabledState() throws RemoteException {
		return WifiManager.WIFI_AP_STATE_DISABLED;
	}

	@Override
	public WifiConfiguration getWifiApConfiguration() throws RemoteException {		
		return new WifiConfiguration();
	}

	@Override
	public void setWifiApConfiguration(WifiConfiguration wifiConfig)
			throws RemoteException {
	}

	@Override
	public void startWifi() throws RemoteException {
		// Nothing to do here as we start WiFi when we mMeshManager.enableMesh();
	}

	@Override
	public void stopWifi() throws RemoteException {
		// Nothing to do here as we stop WiFi when we mMeshManager.disableMesh();
	}

	@Override
	public void addToBlacklist(String bssid) throws RemoteException {
	}

	@Override
	public void clearBlacklist() throws RemoteException {
	}

	@Override
	public Messenger getMessenger() throws RemoteException {
		return new Messenger(mAsyncServiceHandler);
	}

	@Override
	public String getConfigFile() throws RemoteException {
		return Environment.getDataDirectory() +
				"/misc/wifi/ipconfig.txt";
	}
	
    /**
     * see {@link android.net.wifi.WifiManager#setWifiEnabled(boolean)}
     * @param enable {@code true} to enable, {@code false} to disable.
     * @return {@code true} if the enable/disable operation was
     *         started or is already in the queue.
     */
    public synchronized boolean setWifiEnabled(boolean enable) {
        
        Slog.e(TAG, "Invoking setWifiEnabled\n");
        
        if (enable)
        	mMeshManager.enableMesh();
        else
        	mMeshManager.disableMesh();

        long ident = Binder.clearCallingIdentity();
        persistWifiState(enable);
        Binder.restoreCallingIdentity(ident);

        return true;
    }

    private void persistWifiState(boolean enabled) {
        final ContentResolver cr = mContext.getContentResolver();
        if (enabled) {
        	mPersistWifiState.set(WIFI_ENABLED);
        } else {  	
            mPersistWifiState.set(WIFI_DISABLED);
        }

        Settings.Secure.putInt(cr, Settings.Secure.WIFI_ON, mPersistWifiState.get());
    }

	/**
     * Check if Wi-Fi needs to be enabled and start
     * if needed
     *
     * This function is used only at boot time
     */
    public void checkAndStartWifi() {
    	
    	if (getPersistedWifiState() == WIFI_ENABLED)
    		setWifiEnabled(true);
    	else
    		setWifiEnabled(false);
    }
    
	
    private int getPersistedWifiState() {
        final ContentResolver cr = mContext.getContentResolver();
        try {
            return Settings.Secure.getInt(cr, Settings.Secure.WIFI_ON);
        } catch (Settings.SettingNotFoundException e) {
            Settings.Secure.putInt(cr, Settings.Secure.WIFI_ON, WIFI_DISABLED);
            return WIFI_DISABLED;
        }
    }
    
    private void sendRssiChangeBroadcast(final int newRssi) {
        Intent intent = new Intent(WifiManager.RSSI_CHANGED_ACTION);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        intent.putExtra(WifiManager.EXTRA_NEW_RSSI, newRssi);
        mContext.sendBroadcast(intent);
    }

    private void sendNetworkStateChangeBroadcast(String bssid) {
        Intent intent = new Intent(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT
                | Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.putExtra(WifiManager.EXTRA_NETWORK_INFO, mNetworkInfo);
        intent.putExtra(WifiManager.EXTRA_LINK_PROPERTIES, new LinkProperties ());
        if (bssid != null)
            intent.putExtra(WifiManager.EXTRA_BSSID, bssid);
        if (mNetworkInfo.getState() == NetworkInfo.State.CONNECTED)
            intent.putExtra(WifiManager.EXTRA_WIFI_INFO, WifiInfo.CREATOR.newArray(1)[0]);
        mContext.sendStickyBroadcast(intent);
    }   
    
    /**
     * Record the detailed state of a network.
     * @param state the new @{code DetailedState}
     */
    private void setNetworkDetailedState(NetworkInfo.DetailedState state) {
            Slog.d(TAG, "setDetailed state, old ="
                    + mNetworkInfo.getDetailedState() + " and new state=" + state);

        if (state != mNetworkInfo.getDetailedState()) {
            mNetworkInfo.setDetailedState(state, null, null);
        }
    }
    
    private void setWifiState(int wifiState) {
        final int previousWifiState = mWifiState.get();

        try {
            if (wifiState == WIFI_STATE_ENABLED) {
                mBatteryStats.noteWifiOn();
            } else if (wifiState == WIFI_STATE_DISABLED) {
                mBatteryStats.noteWifiOff();
            }
        } catch (RemoteException e) {
            Slog.d(TAG, "Failed to note battery stats in wifi");
        }

        mWifiState.set(wifiState);

        Slog.d(TAG, "setWifiState: " + syncGetWifiStateByName());

        final Intent intent = new Intent(WifiManager.WIFI_STATE_CHANGED_ACTION);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        intent.putExtra(WifiManager.EXTRA_WIFI_STATE, wifiState);
        intent.putExtra(WifiManager.EXTRA_PREVIOUS_WIFI_STATE, previousWifiState);
        mContext.sendStickyBroadcast(intent);
    }
    
    public String syncGetWifiStateByName() {
        switch (mWifiState.get()) {
            case WIFI_STATE_DISABLED:
                return "disabled";
            case WIFI_STATE_ENABLED:
                return "enabled";
            default:
                return "[invalid state]";
        }
    }

}
