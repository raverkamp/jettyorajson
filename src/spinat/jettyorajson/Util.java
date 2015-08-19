package spinat.jettyorajson;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.xml.bind.DatatypeConverter;

public class Util {
    /*
     DateFormat df1 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
     String string1 = "2001-07-04T12:08:56.235-0700";
     Date result1 = df1.parse(string1);

     DateFormat df2 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
     String string2 = "2001-07-04T12:08:56.235-07:00";
     Date result2 = df2.parse(string2);
     */

    public static Date stringToDate(String s) {
        if (s == null || s.equals("")) {
            return null;
        }
        if (s.length() == 10) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            try {
                return sdf.parse(s);
            } catch (ParseException ex) {
                throw new RuntimeException("not in a valid date format:" + s);
            }
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

            try {
                return sdf.parse(s);
            } catch (ParseException ex) {
                throw new RuntimeException("not in a valid date format:" + s);
            }
        }
    }

    public static String dateToString(Date d) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(d);
    }

    public static String encodeBase64(byte[] b) {
        return DatatypeConverter.printBase64Binary(b);
    }

    public static byte[] decodeBase64(String s) {
        return DatatypeConverter.parseBase64Binary(s);
    }
}
