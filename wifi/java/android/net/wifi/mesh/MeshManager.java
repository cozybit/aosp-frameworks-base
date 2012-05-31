/*
 * Copyright cozybit Inc.
 * All rights reserved.
 *
 */
package android.net.wifi.mesh;

import java.util.concurrent.atomic.AtomicBoolean;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Process;
import android.util.Log;

import android.net.wifi.mesh.Shell.ShellException;

public class MeshManager {

	//debugging variables
	private static final String TAG = "MeshManager";
	private boolean DEBUG = true;
	
    private AtomicBoolean mMeshIsActive = new AtomicBoolean(false);

	private IMeshTestUI mUI;

	private HandlerThread mHandlerThread;
	private Handler mHandler;

    private enum MeshActions {
    	ENABLE_MESH,
    	DISABLE_MESH,
    	STATION_DUMP_ON,
    	STATION_DUMP_OFF
    }

	public class MeshNode {

		public String mac;
		public String status;
		public String ip;
		public String nextHop;
		public int latency;

		public MeshNode() {}

		public MeshNode(String mac, String status,
				String ip, String nextHop, int latency) {
			this.mac = mac;
			this.status = status;
			this.ip = ip;
			this.nextHop = nextHop;
			this.latency = latency;
		}
	}

	public MeshManager(IMeshTestUI ui) {

		mUI = ui;

        mHandlerThread = new HandlerThread ("HandlerThread");
        mHandlerThread.start();

        mHandler = new Handler(mHandlerThread.getLooper()) {
        	@Override
        	public void handleMessage(Message msg){
        		performMeshAction(msg);
        	}
        };
	}

	public void enableMesh() {
		if(!isMeshActive())
			sendMsg(MeshActions.ENABLE_MESH);
	}
	
	public void disableMesh() {
		if(isMeshActive())
			sendMsg(MeshActions.DISABLE_MESH);
	}

	public void enableStationDump(boolean enabled) {
		if(enabled)
			sendMsg(MeshActions.STATION_DUMP_ON);
		else
			sendMsg(MeshActions.STATION_DUMP_OFF);
	}

	private void performMeshAction(Message msg) {

		MeshActions action = MeshActions.values()[msg.what];
    	LOGD("Received action: " + action);

    	switch (action) {

    	case ENABLE_MESH:

			try {
				//get the number of netdev
				CmdOutput output = Shell.exec("ls /sys/kernel/debug/ieee80211/");
				int netDevs = output.STDOUT.split(System.getProperty("line.separator")).length;
				if(netDevs == 1) {
					mUI.promptDialog(IMeshTestUI.NETDEV_ERROR_DIALOG);
					break;
				}

				output = Shell.exec("mesh mesh0 up");
	    		if( output.exitValue == 0) {
	    			meshActiveSet(true);
	    			mUI.notifyMeshEnabled();
	    		} else {
	    			mUI.promptDialog(IMeshTestUI.INTERFACE_ERROR_DIALOG);
	    			LOGD("ERROR: " + output.STDERR);
	    		}
			} catch (ShellException e) {
				mUI.promptDialog(IMeshTestUI.SHELL_ERROR_DIALOG);
				Log.e(TAG, e.getMessage());
				e.printStackTrace();
			}

			break;

    	case DISABLE_MESH:

    		mHandler.removeCallbacks(mStationDumpTask);
    		try {
    			CmdOutput output = Shell.exec("mesh mesh0 down");
    			if(output.exitValue == 0) {
    				meshActiveSet(false);
    				mUI.notifyMeshDisabled();
    			} else {
    				mUI.promptDialog(IMeshTestUI.INTERFACE_ERROR_DIALOG);
    				LOGD("ERROR: " + output.STDERR);
    			}
			} catch (ShellException e) {
				mUI.promptDialog(IMeshTestUI.SHELL_ERROR_DIALOG);
				Log.e(TAG, e.getMessage());
				e.printStackTrace();
			}

			break;

    	case STATION_DUMP_ON:
    		mHandler.post(mStationDumpTask);
    		mUI.notifyStationDumpEnabled();
			break;

    	case STATION_DUMP_OFF:
    		mHandler.removeCallbacks(mStationDumpTask);
    		mUI.notifyStationDumpDisabled();
			break;
		}
	}

    public void dumpStations() {

		try {
			CmdOutput output = Shell.exec("mesh mesh0 stations");

			if( output.STDOUT != null & !output.STDOUT.isEmpty() ) {

				String[] entries = output.STDOUT.split("\n");
				MeshNode[] nodes = new MeshNode[entries.length];

				for(int i = 0; i < entries.length; i++) {
					nodes[i] = new MeshNode();
					nodes[i].mac = entries[i].split(",")[0].trim();
					nodes[i].status = entries[i].split(",")[3].trim();
					nodes[i].ip = constructIp(nodes[i].mac);
					//get latency before nexthop, in order to generate mpaths
					nodes[i].latency = getLatency(nodes[i]);
					nodes[i].nextHop = getNextHop(nodes[i].mac);

					mUI.notifyPeerInfo(nodes[i]);
				}
			}
		} catch (ShellException e) {
			mUI.promptDialog(IMeshTestUI.SHELL_ERROR_DIALOG);
			Log.e(TAG, e.getMessage());
			e.printStackTrace();
		}
    }

    private String constructIp(String macAddress) {
    	if(macAddress == null )
    		return "0.0.0.0";
		String[] mac_parts = macAddress.split(":");
		StringBuffer ip = new StringBuffer("10.");
		ip.append(Integer.decode("#" + mac_parts[3]));
		ip.append(".");
		ip.append(Integer.decode("#" + mac_parts[4]));
		ip.append(".");
		ip.append(Integer.decode("#" + mac_parts[5]));
		return ip.toString();
    }

    private int getLatency(MeshNode node) throws ShellException {

    	if(node.ip == null)
    		return -1;

    	CmdOutput output = Shell.exec("ping -f -c 3 -W 2 " + node.ip);

    	if( output.exitValue == 0 ) {
	    	String[] lines = output.STDOUT.split("\n");
	    	//rtt min/avg/max/mdev = 79.969/87.795/95.622/7.832 m
	    	if( lines != null && lines[lines.length-1].contains("min/avg/max/mdev") ) {
		    	String latency = lines[lines.length-1].split("/")[4];
		    	return (int) Float.parseFloat(latency);
	    	}
    	}

    	return -1;
    }

    private String getNextHop(String macAddress) throws ShellException {

    	if(macAddress == null)
    		return "n/a";

    	CmdOutput output = Shell.exec("mesh mesh0 " + macAddress + " nexthop");
    	if ( output.exitValue == 0)
    		return output.STDOUT;
    	else
    		return "n/a";
    }

    private Runnable mStationDumpTask = new Runnable() {
 	   public void run() {
 		   //just send a message
 		   dumpStations();
 		   mHandler.postDelayed(this, 1000);
 	   }
    };

    //send messages to the state machine
    private void sendMsg(MeshActions action) {
    	Message msg = Message.obtain(mHandler, action.ordinal());
    	msg.sendToTarget();
    }

    private void LOGD(String logMsg) {
    	if(DEBUG) {
    		if(logMsg == null)
    			logMsg = "The logMsg was null!";
    		Log.d(TAG, logMsg);
    	}
    }
    
    /**
     * Check if default route is set
     */
    public boolean isMeshActive() {
        return mMeshIsActive.get();
    }
    
    public void meshActiveSet(boolean enabled) {
    	mMeshIsActive.set(enabled);
    }

}
