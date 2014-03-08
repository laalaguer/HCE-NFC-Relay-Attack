package com.laalaguer.cyreaderdesfirebluetooth;

import java.util.Arrays;
import java.util.List;

import com.laalaguer.bluetooth.BluetoothChatService;
import com.laalaguer.bluetooth.EmulatorContext;
import com.laalaguer.isodep.ReadDesFireService;
import com.laalaguer.util.NFCByteUtil;


import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.app.Activity;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class AsReaderActivity extends Activity {

	private static final boolean D = true; // Debug flag
	private static final boolean S = true; // Show on UI flag
	private static final String TAG = "AsCardActivity";
	private static final int REQUEST_CONNECT_BT_DEVICE = 1;
	private static final int REQUEST_ENABLE_BT = 2;
	private static final int REQUEST_ENABLE_NFC = 3;
	private static final String TECH_ISO_DEP = "android.nfc.tech.IsoDep";
	
	private ArrayAdapter<String> mLogArrayAdapter;
	private ListView mLogArrayView;
	private TextView mBluetoothStatus;
	private TextView mNFCStatus;
	
	// Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
	// Member object for the chat services
    private BluetoothChatService mChatService = null;
    // String buffer for outgoing messages
    private StringBuffer mOutStringBuffer;
    // Name of the connected device
    private String mConnectedDeviceName = null;
    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";
    
    private NfcAdapter mNfcAdapter = null; // The NFC controller of Phone.
    private ReadDesFireService readerService; // Our Desfire Card Reader Serive.
    private PendingIntent pendingIntent; // Android system will populate it with detail of tag it received.
    private IntentFilter[] filters; // The type of intent our app wants to handle.
    private String[][] techLists; // The type of technology our app wants to handle.
    
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_as_reader);
		
		// Set views
		mBluetoothStatus = (TextView) findViewById(R.id.textViewBTstatus);
		mBluetoothStatus.setText("BT: Not connected.");
		
		mNFCStatus = (TextView) findViewById(R.id.textViewNFCStatus);
		mNFCStatus.setText("NFC: Not connected.");
		
		mLogArrayView = (ListView) findViewById(R.id.LogList);
		mLogArrayAdapter = new ArrayAdapter<String>(this, R.layout.log_message);
		mLogArrayView.setAdapter(mLogArrayAdapter);
		
        
        // Get local NFC adapter, if null, finish()
		mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (mNfcAdapter == null){
			Toast.makeText(this, "NFC required, close program.", Toast.LENGTH_LONG).show();
			finish(); // quit android program.
			return;
		}else{
			mNfcAdapter.setNdefPushMessage(null, this); // Disable the beam feature of NFC.
		}
        
		// Get local Bluetooth adapter, if null, finish()
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
		// Set foreground dispatch
		pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this,
                getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0); // register for "singletop" activity
        filters = new IntentFilter[] { new IntentFilter(
                NfcAdapter.ACTION_TECH_DISCOVERED) }; // only get us the NFC tags.
        techLists = new String[][] { { TECH_ISO_DEP } }; // We want to use this technology.
        
        // Check who initiated the application
        Intent onCreateIntent = getIntent();
        String action = onCreateIntent.getAction();
        if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {
        	handleNewTag(onCreateIntent);
        }
	}
	
	@Override
    public void onStart() {
		// Called every time after activity brings to front.
        super.onStart();
        if(D) Log.e(TAG, "++ ON START ++");
        
        if(isAdaptersEnabled()){
        	if (mChatService == null) setupChat();
        };
    }
	
	private void setupChat() {
        Log.d(TAG, "setupChat()");

        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new BluetoothChatService(this, mHandler);
        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");
    }
	
	// The Handler that gets information back from the BluetoothChatService
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case EmulatorContext.BT_MESSAGE_STATE_CHANGE:
                if(D) Log.i(TAG, "BT_MESSAGE_STATE_CHANGE: " + msg.arg1);
                switch (msg.arg1) {
                case BluetoothChatService.STATE_CONNECTED:
                	mBluetoothStatus.setText("Connected to:");
                	mBluetoothStatus.append(mConnectedDeviceName);
                	mLogArrayAdapter.clear();
                    break;
                case BluetoothChatService.STATE_CONNECTING:
                	mBluetoothStatus.setText("Connecting ...");
                    break;
                case BluetoothChatService.STATE_LISTEN:
                case BluetoothChatService.STATE_NONE:
                	mBluetoothStatus.setText("BT Not Connected:");
                    break;
                }
                break;
            case EmulatorContext.BT_MESSAGE_WRITE: // user input some message, we send out via BT.
                byte[] writeBuf = (byte[]) msg.obj;
                // construct a string from the buffer
                String writeMessage = NFCByteUtil.toHex(writeBuf);
                mLogArrayAdapter.add("Me:  " + writeMessage);
                break;
            case EmulatorContext.BT_MESSAGE_READ: // other device send us some message via BT.
                byte[] readBuf = Arrays.copyOfRange((byte[]) msg.obj, 0, msg.arg1 );
                // TODO
                // Send to card, try to get some response
                readerService.writeToCard(readBuf);
                // construct a string from the valid bytes in the buffer
                String readMessage = NFCByteUtil.toHex(readBuf);
                mLogArrayAdapter.add(mConnectedDeviceName+":  " + readMessage);
                break;
            case EmulatorContext.BT_MESSAGE_DEVICE_NAME:
                // save the connected device's name
                mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                Toast.makeText(getApplicationContext(), "Connected to "
                               + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                break;
            case EmulatorContext.BT_MESSAGE_TOAST:
                Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                               Toast.LENGTH_SHORT).show();
                break;
                
            case EmulatorContext.RELAY_GET_FROM_CARD:
            	byte[] hceInComing = Arrays.copyOfRange((byte[]) msg.obj, 0, msg.arg1);
            	AsReaderActivity.this.sendMessage(hceInComing);
            	break;
            case EmulatorContext.RELAY_STATUS_CHANGE:
            	if(D) Log.i(TAG, "HCE_STATE_CHANGE: " + msg.arg1);
                switch (msg.arg1) {
                case ReadDesFireService.STATUS_CONNECTED:
                	mNFCStatus.setText("NFC Card Connected. ");
                	mLogArrayAdapter.clear();
                	break;
                case ReadDesFireService.STATUS_ERROR:
                	mNFCStatus.setText("NFC connection ERROR.");
                	break;
                case ReadDesFireService.STATUS_LOST:
                	mNFCStatus.setText("NFC connection lost.");
                	break;
                case ReadDesFireService.STATUS_CLOSED:
                case ReadDesFireService.STATUS_NOTING:
                	mNFCStatus.setText("NFC connection closed.");
                	break;
                }
            }
        }
    };
	
    
    
    /**
     * Sends a message.
     * @param message  A string of text to send.
     */
    private synchronized void sendMessage(byte[] message) {
        // Check that we're actually connected before trying anything
        if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {
            Toast.makeText(this, "Not Connected", Toast.LENGTH_SHORT).show();
            return;
        }
        // Check that there's actually something to send
        if (message.length > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            mChatService.write(message);
        }
    }
	
	private boolean isAdaptersEnabled(){
		boolean flag = false;
		// If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
        	
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        // Otherwise, setup the chat session
        } else {
        	flag = true;
            if (mChatService == null) setupChat();
        }
        
        // If NFC is not on, request that it be enabled. 
        if(!mNfcAdapter.isEnabled()){
        	Toast.makeText(getApplicationContext(), "Please activate NFC and press Back to return to the application!", Toast.LENGTH_LONG).show();
			startActivityForResult(new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS), REQUEST_ENABLE_NFC);
        } else {
        	flag = true;
        }
        return flag;
	}
	
	// System invokes this every time it approaches a tag/reader
	@Override
	public void onNewIntent(Intent intent) {
		Log.d(TAG, "onNewIntent()");
	    setIntent(intent); // Set the global intent, optional.
	    handleNewTag(intent); // Start to deal with NFC reader
	}
	
	private void handleNewTag(Intent intent){
		if(D) Log.d(TAG, "handleNewTag()");
		Tag tag = null;
		// Get Tag object
		if (intent.getExtras() != null) {
            tag = (Tag) intent.getExtras().get(NfcAdapter.EXTRA_TAG);
            if(D) Log.d(TAG, "Tag: "+ tag.toString());
            
        }
		if (tag == null) {
            return;
        }
		// tag ID?
		if (tag.getId() != null) {
        	Log.d(TAG, "Tag ID: " + NFCByteUtil.toHex(tag.getId()));
        	// This usually doesn't return anything in reader.
        }
		// tag support ISOPCDA?
		List<String> techList = Arrays.asList(tag.getTechList());
        if (!techList.contains(TECH_ISO_DEP)){
        	return;
        } else {
        	Log.d(TAG, "Contains: TECH_ISO_DEP");
        	// Connect the reader
        	// connect the card
            IsoDep tagtech = IsoDep.get(tag);
            //Log.d(DEBUG_NAME, "isConnected() " + tagtech.isConnected());
            // From this part and below, we try to put it into a separate thread.
            readerService = new ReadDesFireService(tagtech, mHandler);
            readerService.startService();        }
        
	}
	
	
	// Lock screen or another activity happens, foreground dispatch close
	@Override
	public void onPause(){
		super.onPause();
		Log.d(TAG,"onPause");
		if (mNfcAdapter != null) {
            Log.d(TAG, "disabling foreground dispatch");
            mNfcAdapter.disableForegroundDispatch(this);
        }
	}
	
	// When we return from unlock or another activity
	@Override
    public void onResume() {
		super.onResume();
		Log.d(TAG,"onResume");
			
		if (mNfcAdapter != null) {
			Log.d(TAG, "enabling foreground dispatch");
			mNfcAdapter.enableForegroundDispatch(this, pendingIntent, filters,
                    techLists);
        }
		
		if (mChatService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mChatService.getState() == BluetoothChatService.STATE_NONE) {
              // Start the Bluetooth chat services
              mChatService.start();
            }
        }
	}
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.option_menu, menu);
		return true;
	}
	
	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.scan:
            // Launch the DeviceListActivity to see devices and do scan
            Intent serverIntent = new Intent(this, DeviceListActivity.class);
            startActivityForResult(serverIntent, REQUEST_CONNECT_BT_DEVICE);
            return true;
        case R.id.discoverable:
            // Ensure this device is discoverable by others
            ensureDiscoverable();
            return true;
        }
        return false;
    }
	
	// Ensure discover of bluetooth.
	private void ensureDiscoverable() {
        if(D) Log.d(TAG, "ensure discoverable");
        if (mBluetoothAdapter.getScanMode() !=
            BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(D) Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
        case REQUEST_CONNECT_BT_DEVICE:
            // When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK) {
                // Get the device MAC address
                String address = data.getExtras()
                                     .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                // Get the BLuetoothDevice object
                BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                // Attempt to connect to the device
                mChatService.connect(device);
            }
            break;
        case REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                // Bluetooth is now enabled, so set up a chat session
                setupChat();
            } else {
                // User did not enable Bluetooth or an error occured
                Log.d(TAG, "BT not enabled");
                Toast.makeText(this, "BT not enabled, quit program", Toast.LENGTH_SHORT).show();
                finish();
            }
            break;
            
        case REQUEST_ENABLE_NFC:
			if (!mNfcAdapter.isEnabled()){
				Toast.makeText(getApplicationContext(), "NFC required, close program.", Toast.LENGTH_LONG).show();
				finish();
			}
			break;
        }
    }
	
	@Override
    public void onDestroy() {
        super.onDestroy();
        // Stop the Bluetooth chat services
        if (mChatService != null) mChatService.stop();
        if(D) Log.e(TAG, "--- ON DESTROY ---");
    }

}
