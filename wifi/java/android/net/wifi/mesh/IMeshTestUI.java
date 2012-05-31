/*
 * Copyright cozybit Inc.
 * All rights reserved.
 *
 */

package android.net.wifi.mesh;

import android.net.wifi.mesh.MeshManager.MeshNode;

public interface IMeshTestUI {

	public final int SHELL_ERROR_DIALOG = 0;
	public final int INTERFACE_ERROR_DIALOG = 1;
	public final int NETDEV_ERROR_DIALOG = 2;

	public void promptDialog(int dialogID);
	public void notifyMeshEnabled();
	public void notifyMeshDisabled();
	public void notifyStationDumpEnabled();
	public void notifyStationDumpDisabled();
	public void notifyPeerInfo(MeshNode node);
}
