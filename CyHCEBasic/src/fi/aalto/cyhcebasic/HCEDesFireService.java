package fi.aalto.cyhcebasic;

import java.io.IOException;

import android.nfc.TagLostException;
import android.os.Handler;
import android.util.Log;

public class HCEDesFireService {
	// For debug
	private final String DEBUG_NAME = "HCEDesFireService";
	private final boolean D = true;
	private final boolean S = true;
	
	TagWrapper tagtech ;
	private int mStatus;
	private DummyReplyThreads mThread;
	private final Handler handler;
	
	// Status of connection
	public static final int STATUS_NOTING = 0;
	public static final int STATUS_CONNECTED = 1;
	public static final int STATUS_CLOSED = 2;
	public static final int STATUS_ERROR = 3;
	public static final int STATUS_LOST = 4;
	
	public HCEDesFireService(TagWrapper tw, Handler handler){
		this.tagtech = tw;
		this.handler = handler;
		this.mStatus = STATUS_NOTING;
	}
	
	// Call connect to connect to the Desfire card
	public synchronized void openTagConnection(){
		if (D) Log.d(DEBUG_NAME, "openTagConnection");
		try{
			if (!tagtech.isConnected()){
				tagtech.connect();
			} 
			mStatus = STATUS_CONNECTED;
			// Send to UI thread
			if (S) handler.obtainMessage(GlobalContext.HCE_STATUS_CHANGE, STATUS_CONNECTED, -1).sendToTarget();
		}catch (IOException e) {
			if (D) Log.e(DEBUG_NAME, "openTagConnection", e);
			mStatus = STATUS_ERROR;
			// Send to UI thread
			if (S) handler.obtainMessage(GlobalContext.HCE_STATUS_CHANGE, STATUS_ERROR, -1).sendToTarget();
		}

	}
	
	// Call close to close the Desfire card connection
	public synchronized void closeTagConnection(){
		if (D) Log.d(DEBUG_NAME, "closeTagConnection");
		try {
			tagtech.close();
			mStatus = STATUS_CLOSED;
			if (D) Log.d(DEBUG_NAME, "Closed.");
			// Send to UI thread
			if (S) handler.obtainMessage(GlobalContext.HCE_STATUS_CHANGE, STATUS_CLOSED, -1).sendToTarget();
		} catch (IOException e) {
			if (D) Log.e(DEBUG_NAME, "closeTagConnection", e);
			mStatus = STATUS_ERROR;
			// Send to UI thread
			if (S) handler.obtainMessage(GlobalContext.HCE_STATUS_CHANGE, STATUS_ERROR, -1).sendToTarget();
		}
	}
	
	// Get the max transfer length of bytes[] we can read/write card
	public synchronized int  getMaxTransceiveLength(){
		if (mStatus == STATUS_CONNECTED)
			return tagtech.getMaxTransceiveLength();
		else
			return 0;
	}
	
	public synchronized void setStatus(int value){
		this.mStatus = value;
		if (D) Log.d(DEBUG_NAME, "setStatus(): " + value);
		// Send to UI thread
		if (S) handler.obtainMessage(GlobalContext.HCE_STATUS_CHANGE, this.mStatus, -1).sendToTarget();
	}
	
	public void startService(){
		// connect tag, change status
		// start a new thread, try to communicate until tag disconnect, by the time of disconnect, change status.
		// new thread end.
		openTagConnection();
		mThread = new DummyReplyThreads(tagtech, handler);
		mThread.start();
	}
	private class DummyReplyThreads extends Thread{
		
		TagWrapper mtagtech;
		Handler mHandler;
		
		public DummyReplyThreads(TagWrapper tagtech, Handler handler){
			mtagtech = tagtech;
			mHandler = handler;
		}
		
		public void run(){
			// Transfer some dummy bytes[]
			byte[] cmd = null;
			byte[] dummyAnswer = new byte[] { (byte) 0x91, 0x00 };
			long startTime = 0; 
			long endTime = 0;
			
	    	while (mtagtech.isConnected()) synchronized (this){
	    		// if (IS_DEBUG) Log.d(DEBUG_NAME, "Start to transceive");
	    		try {
	    			startTime = System.currentTimeMillis();
					cmd = mtagtech.transceive(dummyAnswer); // Transceive to/from reader.
					endTime = System.currentTimeMillis();
				} catch (TagLostException e) {
					if (D) Log.e(DEBUG_NAME, "DummyReplyThreads, TagLostException", e);
					break;
				} catch (IOException e) {
					if (D) Log.e(DEBUG_NAME, "DummyReplyThreads, IO exception", e);
					break;
				} 
	    		String debugMessage = "Bus: "+ NFCByteUtil.toHex(cmd)+ " We : 91 00"+", RTT: "+(endTime - startTime)+ "ms"; 
	    		if (D) Log.d(DEBUG_NAME, debugMessage);
	    		// Send to UI Thread
	    		if (S) mHandler.obtainMessage(GlobalContext.HCE_MESSAGE_WRITE_TO_SCREEN, -1, -1, debugMessage).sendToTarget();
				
	    	}
	    	
	    	// otherwise, mtagtech is not connected.(lost)
	    	mtagtech = null;
	    	HCEDesFireService.this.setStatus(HCEDesFireService.STATUS_LOST);
	    	// Close Connection
	    	HCEDesFireService.this.closeTagConnection();
		}
	} 
}
