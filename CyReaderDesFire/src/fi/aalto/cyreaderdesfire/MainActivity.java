package fi.aalto.cyreaderdesfire;


import java.util.Arrays;
import java.util.List;


import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.TagTechnology;
import android.nfc.NfcAdapter;

import android.os.Bundle;
import android.os.Handler;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.view.Menu;

public class MainActivity extends Activity {
	
	private static final String DEBUG_NAME = MainActivity.class.getSimpleName();
	private static final String TECH_ISO_DEP = "android.nfc.tech.IsoDep";
	
	private PendingIntent pendingIntent; // Android system will populate it with detail of tag it received.
    private IntentFilter[] filters; // The type of intent our app wants to handle.
    private String[][] techLists; // The type of technology our app wants to handle.
    
    private NfcAdapter adapter; // The NFC controller of Phone.
    private ReadDesFireService readerService; // Our Desfire Card Reader Serive.
    

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		Log.d(DEBUG_NAME, "OnCreate");
		
		/* Initial block */
		adapter = NfcAdapter.getDefaultAdapter(this); // Get the Phone NFC controller.
        adapter.setNdefPushMessage(null, this); // Disable the beam feature of NFC.
        
		pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this,
                getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0); // register for "singletop" activity
		
        filters = new IntentFilter[] { new IntentFilter(
                NfcAdapter.ACTION_TECH_DISCOVERED) }; // only get us the NFC tags.
        
        techLists = new String[][] { { TECH_ISO_DEP } }; // We want to use this technology.
        
        Intent intent = getIntent(); // How does the system call us?
        String action = intent.getAction(); // Get action code
        Log.d(DEBUG_NAME, "Intent: " + intent);
        Log.d(DEBUG_NAME, "Action: "+ action);
        
        // if it is an NFC intent
        if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {
            this.handleNewTag(intent);
        }// else do nothing
	}
	
	@Override
    public void onNewIntent(Intent intent) {
        Log.d(DEBUG_NAME, "onNewIntent()");
        this.setIntent(intent); // Set the global intent, optional.
        this.handleNewTag(intent);
    }
	
	
	public void handleNewTag(Intent intent){
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
        	// This usually doesn't return anything if tag is a reader.
        	// If tag is a card, it will have some value here.
        }
        
        // Check the existence of IsoDEP technology on tag
        List<String> techList = Arrays.asList(tag.getTechList());
        if (techList.contains(TECH_ISO_DEP)){
        	Log.d(DEBUG_NAME, "Contains: ISoDep");
        	// connect the card
            IsoDep tagtech = IsoDep.get(tag);
            //Log.d(DEBUG_NAME, "isConnected() " + tagtech.isConnected());
            // From this part and below, we try to put it into a separate thread.
            readerService = new ReadDesFireService(tagtech);
            readerService.startService();
        }
        
        
	}
	
	private Handler mHandler = new Handler(){
		
		
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
