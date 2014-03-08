package com.laalaguer.isodep;
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

import com.laalaguer.bluetooth.EmulatorContext;
import com.laalaguer.util.NFCByteUtil;

import android.nfc.TagLostException;
import android.nfc.tech.IsoDep;
import android.os.Handler;
import android.util.Log;

public class ReadDesFireService {
	// For debug
	private final String DEBUG_NAME = "ReadDesFireService";
	private final boolean D = true;
	private final boolean S = true;
	
	// Member Fields
	IsoDep tagtech;
	private int mStatus;
	private DummyReplyThreads mThread;
	private final Handler handler;
	
	// Status of connection
	public static final int STATUS_NOTING = 0;
	public static final int STATUS_CONNECTED = 1;
	public static final int STATUS_CLOSED = 2;
	public static final int STATUS_ERROR = 3;
	public static final int STATUS_LOST = 4;
	
	public ReadDesFireService(IsoDep tagtech, Handler handler){
		this.tagtech = tagtech;
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
			if (S) handler.obtainMessage(EmulatorContext.RELAY_STATUS_CHANGE, STATUS_CONNECTED, -1).sendToTarget();
		}catch (IOException e) {
			if (D) Log.e(DEBUG_NAME, "openTagConnection", e);
			mStatus = STATUS_ERROR;
			if (S) handler.obtainMessage(EmulatorContext.RELAY_STATUS_CHANGE, STATUS_ERROR, -1).sendToTarget();
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
			if (S) handler.obtainMessage(EmulatorContext.RELAY_STATUS_CHANGE, STATUS_CLOSED, -1).sendToTarget();
		} catch (IOException e) {
			if (D) Log.e(DEBUG_NAME, "closeTagConnection", e);
			mStatus = STATUS_ERROR;
			// Send to UI thread
			if (S) handler.obtainMessage(EmulatorContext.RELAY_STATUS_CHANGE, STATUS_ERROR, -1).sendToTarget();			
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
		if (S) handler.obtainMessage(EmulatorContext.RELAY_STATUS_CHANGE, this.mStatus, -1).sendToTarget();		
	}
	
	public synchronized void  writeToCard(byte[] info){
		DummyReplyThreads drt = this.mThread;
		if (mThread == null) return;
		if (mStatus != STATUS_CONNECTED) return;
		drt.setBufferToCard(info);
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
		
		IsoDep mtagtech;
		Handler mhandler;
		byte[] bufferToWrite;
		
		public DummyReplyThreads(IsoDep tagtech, Handler handler){
			mtagtech = tagtech;
			mhandler = handler;
		}
		
		public void setBufferToCard(byte[] info){
			this.bufferToWrite = info;
		}
		
		private void clearBufferToCard(){
			this.bufferToWrite = null;
		}
		
		public void run(){
			// Transfer some dummy bytes[]
			byte[] cmd = null;
			byte[] dummyquestion = new byte[] { (byte) 0x90, 0x5A, 0x00, 0x00, 0x03, 0x00, 0x00, 0x00, 0x00};
			byte[] dummyquestion2 = new byte[] {0x5a, 0x03, 0x00, 0x00};
			long startTime = 0; 
			long endTime = 0;
			
			// First time we set a buffer to send to card
			// setBufferToReader(dummyquestion);
			
	    	while (mtagtech.isConnected()) synchronized (this){
	    		
	    		while (bufferToWrite != null){
	    			if (D) Log.d(DEBUG_NAME, "Start to transceive");
		    		try {
		    			startTime = System.currentTimeMillis();
						cmd = mtagtech.transceive(bufferToWrite);
						endTime = System.currentTimeMillis();
					} catch (TagLostException e) {
						if (D) Log.e(DEBUG_NAME, "DummyReplyThreads, TagLostException", e);
						break;
					} catch (IOException e) {
						if (D) Log.e(DEBUG_NAME, "DummyReplyThreads, IO exception", e);
						break;
					} 
		    		String debugMessage = "We: "+ NFCByteUtil.toHex(bufferToWrite) +" Card: " + NFCByteUtil.toHex(cmd)+ ", RTT: "+(endTime - startTime)+"ms";
		    		if (D) Log.d(DEBUG_NAME, debugMessage);
		    		// Send to UI thread, to display
		    		if (S) mhandler.obtainMessage(EmulatorContext.RELAY_MESSAGE_WRITE_TO_SCREEN, -1, -1, debugMessage).sendToTarget();
					// Send to UI thread, to forward to bluetooth thread
		    		mhandler.obtainMessage(EmulatorContext.RELAY_GET_FROM_CARD, cmd.length, -1, cmd).sendToTarget();
		    		this.clearBufferToCard();
	    		}
	    		
	    		
	    		
	    	}
	    	
	    	// otherwise, mtagtech is not connected.(lost)
	    	mtagtech = null;
	    	ReadDesFireService.this.setStatus(ReadDesFireService.STATUS_LOST);
	    	// Close Connection
	    	ReadDesFireService.this.closeTagConnection();
		}
	} 
	
	
	

}
