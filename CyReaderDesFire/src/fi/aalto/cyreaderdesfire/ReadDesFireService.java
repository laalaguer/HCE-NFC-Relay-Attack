package fi.aalto.cyreaderdesfire;
/**
 * This is a class working for these purposes:
 * 1. Manage connections to a Desfire card.
 * 2. Transfer and receive APDUs to/from card.
 * 3. Send some dummy bytes to card and read as demo purpose.
 * 
 * @author Isaac
 *
 */

import java.io.IOException;

import android.nfc.TagLostException;
import android.nfc.tech.IsoDep;
import android.util.Log;

public class ReadDesFireService {
	// For debug
	private final String DEBUG_NAME = "ReadDesFireService";
	private final boolean D = true;
	
	// Member Fields
	IsoDep tagtech;
	private int mStatus;
	private DummyReplyThreads mThread;
	
	// Status of connection
	public static final int STATUS_NOTING = 0;
	public static final int STATUS_CONNECTED = 1;
	public static final int STATUS_CLOSED = 2;
	public static final int STATUS_ERROR = 3;
	public static final int STATUS_LOST = 4;
	
	public ReadDesFireService(IsoDep tagtech){
		this.tagtech = tagtech;
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
		}catch (IOException e) {
			if (D) Log.e(DEBUG_NAME, "openTagConnection", e);
			mStatus = STATUS_ERROR;
		}

	}
	
	// Call close to close the Desfire card connection
	public synchronized void closeTagConnection(){
		if (D) Log.d(DEBUG_NAME, "closeTagConnection");
		try {
			tagtech.close();
			mStatus = STATUS_CLOSED;
			if (D) Log.d(DEBUG_NAME, "Closed.");
		} catch (IOException e) {
			if (D) Log.e(DEBUG_NAME, "closeTagConnection", e);
			mStatus = STATUS_ERROR;
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
	}
	
	
	public void startService(){
		// connect tag, change status
		// start a new thread, try to communicate until tag disconnect, by the time of disconnect, change status.
		// new thread end.
		openTagConnection();
		mThread = new DummyReplyThreads(tagtech);
		mThread.start();
	}
	private class DummyReplyThreads extends Thread{
		
		IsoDep mtagtech;
		
		public DummyReplyThreads(IsoDep tagtech){
			mtagtech = tagtech;
		}
		
		public void run(){
			// Transfer some dummy bytes[]
			byte[] cmd = null;
			byte[] dummyquestion = new byte[] { (byte) 0x90, 0x5A, 0x00, 0x00, 0x03, 0x00, 0x00, 0x00, 0x00};
			byte[] dummyquestion2 = new byte[] {0x5a, 0x03, 0x00, 0x00};
			long startTime = 0; 
			long endTime = 0;
			
	    	while (mtagtech.isConnected()) synchronized (this){
	    		// if (IS_DEBUG) Log.d(DEBUG_NAME, "Start to transceive");
	    		try {
	    			startTime = System.currentTimeMillis();
					cmd = mtagtech.transceive(dummyquestion);
					endTime = System.currentTimeMillis();
				} catch (TagLostException e) {
					if (D) Log.e(DEBUG_NAME, "DummyReplyThreads, TagLostException", e);
					break;
				} catch (IOException e) {
					if (D) Log.e(DEBUG_NAME, "DummyReplyThreads, IO exception", e);
					break;
				} 
	    		if (D) Log.d(DEBUG_NAME, "Answer for "+ NFCByteUtil.toHex(dummyquestion) +" : " + NFCByteUtil.toHex(cmd)+ ", RTT: "+(endTime - startTime)+"ms");
	    	}
	    	
	    	// otherwise, mtagtech is not connected.(lost)
	    	mtagtech = null;
	    	ReadDesFireService.this.setStatus(ReadDesFireService.STATUS_LOST);
	    	// Close Connection
	    	ReadDesFireService.this.closeTagConnection();
		}
	} 
	
	
	

}
