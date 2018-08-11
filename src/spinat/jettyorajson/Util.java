package spinat.jettyorajson;

import javax.xml.bind.DatatypeConverter;

public class Util {
 
    public static String encodeBase64(byte[] b) {
        return DatatypeConverter.printBase64Binary(b);
    }

    public static byte[] decodeBase64(String s) {
        return DatatypeConverter.parseBase64Binary(s);
    }
}
