package com.laalaguer.util;

public class NFCByteUtil {
	// Format byte[] content to String type 
	public static String toHex(byte[] bytes) {
        StringBuffer buff = new StringBuffer();
        for (byte b : bytes) {
            buff.append(String.format("%02X ", b));
        }

        return buff.toString();
    }
}
