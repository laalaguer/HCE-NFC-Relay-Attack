package fi.aalto.cyhcebasic;


import java.util.Arrays;

import java.util.List;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.view.Menu;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class MainActivity extends Activity {
	
	private static final String DEBUG_NAME = MainActivity.class.getSimpleName();
	private static final String TECH_ISO_PCDA = "android.nfc.tech.IsoPcdA";
	
	private PendingIntent pendingIntent; // Android system will populate it with detail of tag it received.
    private IntentFilter[] filters; // The type of intent our app wants to handle.
    private String[][] techLists; // The type of technology our app wants to handle.
    
    private NfcAdapter adapter; // The NFC controller of Phone.
    private HCEDesFireService hceService;
    
    private ArrayAdapter<String> mLogArrayAdapter;
	private ListView mLogArrayView;
	private TextView mNFCStatus;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		Log.d(DEBUG_NAME, "OnCreate");
		
		/* Initial block */
		adapter = NfcAdapter.getDefaultAdapter(this); // Get the Phone NFC controller.
        adapter.setNdefPushMessage(null, this); // Disable the beam feature of NFC.
        
        // initial the list view 
        mLogArrayView = (ListView) findViewById(R.id.logList);
		mLogArrayAdapter = new ArrayAdapter<String>(this, R.layout.log_message);
		mLogArrayView.setAdapter(mLogArrayAdapter);
		// Text view
		mNFCStatus = (TextView) findViewById(R.id.textViewNFCStatus);
		mNFCStatus.setText("NFC: Not connected.");
        
		pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this,
                getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0); // register for "singletop" activity
		
        filters = new IntentFilter[] { new IntentFilter(
                NfcAdapter.ACTION_TECH_DISCOVERED) }; // only get us the NFC tags.
        
        techLists = new String[][] { { TECH_ISO_PCDA } }; // We want to use this technology.
        
        Intent intent = getIntent(); // How does the system call us?
        String action = intent.getAction(); // Get action code
        Log.d(DEBUG_NAME, "Intent: " + intent);
        Log.d(DEBUG_NAME, "Action: "+ action);
        
        // if it is an NFC intent
        if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {
            this.handleTag(intent);
        }// else do nothing
	}
	
	@Override
    public void onNewIntent(Intent intent) {
        Log.d(DEBUG_NAME, "onNewIntent()");
        this.setIntent(intent); // Set the global intent, optional.
        this.handleTag(intent);
    }
	
	
	public void handleTag(Intent intent){
		Log.d(DEBUG_NAME, "handleTag()");
		Log.d(DEBUG_NAME, "Intent: "+ intent);
		// Get the "reader" as a "Tag"
		Tag tag = null;
		if (intent.getExtras() != null) {
            tag = (Tag) intent.getExtras().get(NfcAdapter.EXTRA_TAG);
            Log.d(DEBUG_NAME, "Tag: "+ tag.toString());
        }
        if (tag == null) {
            return;
        }
        
        if (tag.getId() != null) {
        	Log.d(DEBUG_NAME, "Tag ID: " + NFCByteUtil.toHex(tag.getId()));
        	// This usually doesn't return anything in reader.
        }
        
        // Check the existence of IsoPcdA technology
        List<String> techList = Arrays.asList(tag.getTechList());
        if (techList.contains(TECH_ISO_PCDA)){
        	Log.d(DEBUG_NAME, "Contains: ISOPcdA");
        	// Connect the reader
            TagWrapper tw = new TagWrapper(tag, TECH_ISO_PCDA);
            hceService = new HCEDesFireService(tw , mHandler);
            hceService.startService();
        }
	}
	
	
	private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
        	switch (msg.what) {
        	case GlobalContext.HCE_STATUS_CHANGE:
        		switch(msg.arg1){
        			case HCEDesFireService.STATUS_CLOSED:
        				mNFCStatus.setText("Reader Closed.");
        				break;
        			case HCEDesFireService.STATUS_CONNECTED:
        				mNFCStatus.setText("Reader Connected.");
        				break;
        			case HCEDesFireService.STATUS_ERROR:
        				mNFCStatus.setText("Transmit Error.");
        				break;
        			case HCEDesFireService.STATUS_LOST:
        				mNFCStatus.setText("Reader Lost.");
        				break;
        			case HCEDesFireService.STATUS_NOTING:
        				mNFCStatus.setText("NFC Nothing.");
        				break;
        		}
        		break;
        		
        	case GlobalContext.HCE_MESSAGE_WRITE_TO_SCREEN:
        		// construct a string from the buffer
                String writeMessage = (String) msg.obj;
                mLogArrayAdapter.add(writeMessage);
                break;
        	}
        }
    };
	
	@Override
	public void onPause(){
		super.onPause();
		Log.d(DEBUG_NAME,"onPause");
		
		if (adapter != null) {
            Log.d(DEBUG_NAME, "disabling foreground dispatch");
            adapter.disableForegroundDispatch(this);
        }
	}
	
	@Override
    public void onResume() {
		super.onResume();
		Log.d(DEBUG_NAME,"onResume");
		
		if (adapter != null) {
			Log.d(DEBUG_NAME, "enabling foreground dispatch");
            adapter.enableForegroundDispatch(this, pendingIntent, filters,
                    techLists);
        }
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

}
