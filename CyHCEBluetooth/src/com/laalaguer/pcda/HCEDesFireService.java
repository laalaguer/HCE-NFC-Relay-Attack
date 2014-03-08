package com.laalaguer.pcda;

import java.io.IOException;
import android.nfc.TagLostException;
import android.os.Handler;
import android.util.Log;
import com.laalaguer.bluetooth.*;
import com.laalaguer.util.*;

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
			if (S) handler.obtainMessage(EmulatorContext.HCE_STATUS_CHANGE, STATUS_CONNECTED, -1).sendToTarget();
		}catch (IOException e) {
			if (D) Log.e(DEBUG_NAME, "openTagConnection", e);
			mStatus = STATUS_ERROR;
			// Send to UI thread
			if (S) handler.obtainMessage(EmulatorContext.HCE_STATUS_CHANGE, STATUS_ERROR, -1).sendToTarget();
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
			if (S) handler.obtainMessage(EmulatorContext.HCE_STATUS_CHANGE, STATUS_CLOSED, -1).sendToTarget();
		} catch (IOException e) {
			if (D) Log.e(DEBUG_NAME, "closeTagConnection", e);
			mStatus = STATUS_ERROR;
			// Send to UI thread
			if (S) handler.obtainMessage(EmulatorContext.HCE_STATUS_CHANGE, STATUS_ERROR, -1).sendToTarget();
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
		if (S) handler.obtainMessage(EmulatorContext.HCE_STATUS_CHANGE, this.mStatus, -1).sendToTarget();
	}
	
	public void startService(){
		// connect tag, change status
		// start a new thread, try to communicate until tag disconnect, by the time of disconnect, change status.
		// new thread end.
		openTagConnection();
		mThread = new DummyReplyThreads(tagtech, handler);
		mThread.start();
	}
	
	public synchronized void  writeToReader(byte[] info){
		DummyReplyThreads drt = this.mThread;
		if (mThread == null) return;
		if (mStatus != STATUS_CONNECTED) return;
		drt.setBufferToReader(info);
	}
	
	private class DummyReplyThreads extends Thread{
		
		TagWrapper mtagtech;
		Handler mHandler;
		byte[] bufferToWrite;
		
		public DummyReplyThreads(TagWrapper tagtech, Handler handler){
			mtagtech = tagtech;
			mHandler = handler;
		}
		
		public void setBufferToReader(byte[] info){
			this.bufferToWrite = info;
		}
		
		private void clearBufferToReader(){
			this.bufferToWrite = null;
		}
		
		
		public void run(){
			// Transfer some dummy bytes[]
			byte[] cmd = null;
			byte[] dummyAnswer = new byte[] { (byte) 0x91, 0x00 };
			long startTime = 0; 
			long endTime = 0;
			// First time we connect to reader, we prepare an answer, which is irrelevant. 
			setBufferToReader(dummyAnswer);
			
	    	while (mtagtech.isConnected() ) synchronized (this){
	    		
	    		while(bufferToWrite != null){
	    			if (D) Log.d(DEBUG_NAME, "buffer has info" + NFCByteUtil.toHex(bufferToWrite));
	    			try {
		    			startTime = System.currentTimeMillis();
						cmd = mtagtech.transceive(bufferToWrite); // Transceive to/from reader.
						endTime = System.currentTimeMillis();
					} catch (TagLostException e) {
						if (D) Log.e(DEBUG_NAME, "DummyReplyThreads, TagLostException", e);
						break;
					} catch (IOException e) {
						if (D) Log.e(DEBUG_NAME, "DummyReplyThreads, IO exception", e);
						break;
					} 
		    		String debugMessage = "Reader: "+ NFCByteUtil.toHex(cmd)+ " We : "+NFCByteUtil.toHex(bufferToWrite)+", RTT: "+(endTime - startTime)+ "ms"; 
		    		if (D) Log.d(DEBUG_NAME, debugMessage);
		    		// Send to UI thread, to display
		    		if (S) mHandler.obtainMessage(EmulatorContext.HCE_MESSAGE_WRITE_TO_SCREEN, -1, -1, debugMessage).sendToTarget();
					// Send to UI thread, to forward to bluetooth thread
		    		mHandler.obtainMessage(EmulatorContext.HCE_GET_FROM_READER, cmd.length, -1, cmd).sendToTarget();
	    			// Clear this buffer to reader.
	    			this.clearBufferToReader();
	    		}
	    		
	    	}
	    	
	    	// otherwise, mtagtech is not connected.(lost)
	    	mtagtech = null;
	    	HCEDesFireService.this.setStatus(HCEDesFireService.STATUS_LOST);
	    	// Close Connection
	    	HCEDesFireService.this.closeTagConnection();
		}
	} 
}
