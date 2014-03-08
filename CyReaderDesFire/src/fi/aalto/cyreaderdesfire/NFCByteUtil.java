package fi.aalto.cyreaderdesfire;

public class NFCByteUtil {
	public static String toHex(byte[] bytes) {
        StringBuffer buff = new StringBuffer();
        for (byte b : bytes) {
            buff.append(String.format("%02X ", b));
        }

        return buff.toString();
    }
}
