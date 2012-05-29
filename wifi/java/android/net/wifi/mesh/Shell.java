package android.net.wifi.mesh;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.util.Log;

public class Shell {

	//debugging variables
	private static final String TAG = "Shell";
	private static boolean DEBUG = true;

	// Define output streams.
	public static enum OUTPUT {
		NONE, STDOUT, STDERR, STDBOTH
	};

	private static OUTPUT sOStream = OUTPUT.STDBOTH;

	// Define su commands.
	private static enum SU_COMMAND {
		SU("su"),
		BIN("/system/bin/su"),
		XBIN("/system/xbin/su");

		private String mCmd;

		SU_COMMAND(String cmd) {
			mCmd = cmd;
		}

		/**
		 * @return Set su command.
		 */
		public String getCommand() {
			return mCmd;
		}
	}

	// Define uid commands.
	private static enum UID_COMMAND {
		ID("id"),
		BIN("/system/bin/id"),
		XBIN("/system/xbin/id");

		private String mCmd;

		UID_COMMAND(String cmd) {
			mCmd = cmd;
		}

		public String getCommand() {
			return mCmd;
		}
	}

	private static String sShell;
	private static final String EOL = System.getProperty("line.separator");
	private static final String EXIT = "exit" + Shell.EOL;

	/**
	 * Shell Interface Utility Exception is used to compress IOExcptions and InterruptedExceptions.
	 *
	 * @author JJ Ford
	 *
	 */
	public static class ShellException extends Exception {
		private static final long serialVersionUID = 4820332926695755116L;

		public ShellException() {
			super();
		}

		public ShellException(String msg) {
			super(msg);
		}
	}

	/**
	 * Used to buffer shell output off of the main thread.
	 *
	 * @author JJ Ford
	 *
	 */

	//TODO: This buffer approach has two issues:
	//   - Sometimes the output of the command is just empty.
    //     Probably because getOutput has been called too fast
	//   - If the command blocks and does not generate output,
	//     then it will probably miss future output
	private static class Buffer extends Thread {
		private InputStream mInputStream;
		private StringBuffer mBuffer;

		/**
		 * @param inputStream Data stream to get shell output from.
		 */
		public Buffer(InputStream inputStream) {
			mInputStream = inputStream;
			mBuffer = new StringBuffer();
			this.start();
		}

		public String getOutput() {
			return mBuffer.toString();
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Thread#run()
		 */
		@Override
		public void run() {
			try {
				String line;
				BufferedReader reader = new BufferedReader(new InputStreamReader(mInputStream));
				if((line = reader.readLine()) != null) {
					mBuffer.append(line);
					while((line = reader.readLine()) != null) {
						mBuffer.append(Shell.EOL).append(line);
					}
				}
			} catch(IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Block instantiation of this object.
	 */
	private Shell() {}

	/**
	 * Executes a command in the devices native shell.
	 *
	 * @param cmd The command to execute.
	 * @return Output of the command, null if there is no output.
	 * @throws ShellException
	 */
	private static CmdOutput nativeExec(String cmd) throws ShellException {
		Process proc = null;
		//Buffer[] buffer = null;
		CmdOutput output = new CmdOutput();
		try {
			proc = Runtime.getRuntime().exec(cmd);
			//stop using the buffer approach
			//buffer = getBuffer(proc);
			output.exitValue = proc.waitFor();
			output.STDOUT = getOutput( proc.getInputStream() );
			output.STDERR = getOutput( proc.getErrorStream() );
			//output.STDOUT = (buffer[0] !=null) ? buffer[0].getOutput() : null;
			//output.STDERR = (buffer[1] !=null) ? buffer[1].getOutput() : null;
			return output;
		} catch (Exception e) {
			throw new ShellException();
		}
	}

	/**
	 * Executes a command in the su shell.
	 *
	 * @param cmd The command to execute.
	 * @return Output of the command, null if there is no output.
	 * @throws ShellException
	 */
	private static CmdOutput suExec(String cmd) throws ShellException {
		try {
			Process proc = Runtime.getRuntime().exec(sShell);
			Buffer[] buffer = getBuffer(proc);
			CmdOutput output = new CmdOutput();
			DataOutputStream shell = new DataOutputStream(proc.getOutputStream());

			// Write su command to su shell.
			shell.writeBytes(cmd + Shell.EOL);
			shell.flush();
			shell.writeBytes(Shell.EXIT);
			shell.flush();

			output.exitValue = proc.waitFor();
			output.STDOUT = (buffer[0] !=null) ? buffer[0].getOutput() : null;
			output.STDERR = (buffer[1] !=null) ? buffer[1].getOutput() : null;

			return output;

		} catch (Exception e) {
			throw new ShellException();
		}
	}

	/**
	 * Finds and sets the su shell that has root privileges.
	 * @throws ShellException
	 */
	private static void setSuShell() throws ShellException {
		for(SU_COMMAND cmd : SU_COMMAND.values()) {
			sShell = cmd.getCommand();
			if(Shell.isRootUid()) {
				return;
			}
		}
		sShell = null;
	}

	/**
	 * Determines if the su shell has root privileges.
	 *
	 * @return True if the su shell has root privileges, false if not.
	 * @throws ShellException
	 */
	private static boolean isRootUid() throws ShellException {
		for(UID_COMMAND uid : UID_COMMAND.values()) {
			CmdOutput output = Shell.sudo(uid.getCommand());
			if(output.STDOUT != null && output.STDOUT.length() > 0) {
				Matcher regex = Pattern.compile("^uid=(\\d+).*?").matcher(output.STDOUT);
				if(regex.matches()) {
					if("0".equals(regex.group(1))) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * Gets the buffer for the shell output stream that is currently set.
	 *
	 * @param proc Process running the shell command.
	 * @return The buffer containing the shell output stream, NULL is none.
	 */
	private static Buffer[] getBuffer(Process proc) {
		Buffer buffer[] = new Buffer[2];
		switch(sOStream) {
			case NONE:
				new Buffer(proc.getInputStream());
				new Buffer(proc.getErrorStream());
				break;
			case STDOUT:
				buffer[0] = new Buffer(proc.getInputStream());
				new Buffer(proc.getErrorStream());
				break;
			case STDERR:
				buffer[1] = new Buffer(proc.getErrorStream());
				new Buffer(proc.getInputStream());
				break;
			case STDBOTH:
				buffer[0] = new Buffer(proc.getInputStream());
				buffer[1] = new Buffer(proc.getErrorStream());
			default:
				return buffer;
		}
		return buffer;
	}

	public static String getOutput( InputStream inputStream ) {

		StringBuffer strBuf = new StringBuffer();

		try {
			String line;
			InputStreamReader isr = new InputStreamReader(inputStream);
			BufferedReader reader = new BufferedReader(isr);
			if((line = reader.readLine()) != null) {
				strBuf.append(line);
				while((line = reader.readLine()) != null) {
					strBuf.append( Shell.EOL ).append(line);
				}
			}
			reader.close();
			isr.close();
		} catch(IOException e) {
			e.printStackTrace();
		}

		return strBuf.toString();
	}

	/*
	 * API
	 *
	 *
	 *
	 */

	/**
	 * Sets the shell's {@link Shell.OUTPUT output stream}.  Default value is STDBOTH.
	 *
	 * @param ostream The output Stream to read shell from.
	 */
	synchronized public static void setOutputStream(Shell.OUTPUT ostream) {
		sOStream = ostream;
	}

	/**
	 * Sets the su shell to be used.
	 *
	 * @param shell The shell to be used for sudo.
	 */
	synchronized public static void setShell(String shell) {
		sShell = shell;
	}

	/**
	 * Gains privileges to root shell.  Device must be rooted to use.
	 *
	 * @return True if root shell is obtained, false if not.
	 */
	synchronized public static boolean su() {
		if(sShell == null) {
			try {
				Shell.setSuShell();
			} catch (ShellException e) {
				sShell = null;
			}
		}
		return sShell != null;
	}

	/**
	 * Executes a command in the root shell.  Devices must be rooted to use.
	 *
	 * @param cmd The command to execute in root shell.
	 * @return Output of the command, null if there is no output.
	 * @throws ShellException
	 * @throws InterruptedException
	 * @throws IOException
	 */
	synchronized public static CmdOutput sudo(String cmd) throws ShellException {
		if(Shell.su()) {
			return Shell.suExec(cmd);
		} else {
			return null;
		}

	}

	/**
	 * Executes a native shell command.
	 *
	 * @param cmd The command to execute in the native shell.
	 * @return Output of the command, null if there is no output.
	 * @throws ShellException
	 */
	synchronized public static CmdOutput exec(String cmd) throws ShellException {
		CmdOutput output = Shell.nativeExec(cmd);
		LOGD("CMD (" + output.exitValue +"): " + cmd);
		LOGD("STDOUT: " + output.STDOUT);
		LOGD("STDERR: " + output.STDERR);
		return output;
	}

	static private void LOGD(String logMsg) {
    	if(DEBUG) {
    		if(logMsg == null)
    			logMsg = "The logMsg was null!";
    		Log.d(TAG, logMsg);
    	}
    }
}