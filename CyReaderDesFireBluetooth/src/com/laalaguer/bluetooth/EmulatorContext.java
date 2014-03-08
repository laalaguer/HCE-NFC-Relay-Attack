package com.laalaguer.bluetooth;

public class EmulatorContext {
	// Message types sent from the BluetoothChatService Handler
    public static final int BT_MESSAGE_STATE_CHANGE = 1;
    public static final int BT_MESSAGE_READ = 2;
    public static final int BT_MESSAGE_WRITE = 3;
    public static final int BT_MESSAGE_DEVICE_NAME = 4;
    public static final int BT_MESSAGE_TOAST = 5;
        
    // Key names received from the BluetoothChatService Handler
    public static final String BT_EVICE_NAME = "device_name";
    public static final String TOAST = "toast";
    
    // Message types sent from the HCEService Handler    
    public static final int RELAY_STATUS_CHANGE = 6;
	public static final int RELAY_MESSAGE_WRITE_TO_SCREEN = 7 ;
	public static final int RELAY_GET_FROM_CARD = 8;
    
}
