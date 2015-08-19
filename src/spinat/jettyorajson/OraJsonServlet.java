package spinat.jettyorajson;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import oracle.jdbc.OracleConnection;
import org.json.simple.JSONObject;

public class OraJsonServlet extends HttpServlet {

    String dburl = null;
    Map<String, String> procedures;

    @Override
    public void init() {
        this.dburl = getInitParameter("dburl");
        try {
            this.procedures = loadProcedures(getInitParameter("procedures"));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String auth = request.getHeader("Authorization");
        OracleConnection con = authorize(auth);
        if (con == null) {
            response.setHeader("WWW-Authenticate", "BASIC realm=\"" + getInitParameter("realm") + "\"");
            response.sendError(response.SC_UNAUTHORIZED);
        }
        String jsonstring = request.getParameter("data");
        org.json.simple.parser.JSONParser p = new org.json.simple.parser.JSONParser();
        JSONObject mo = null;
        try {
            JSONObject m = (JSONObject) p.parse(jsonstring);
            String proc = (String) m.get("procedure");
            String realproc = this.procedures.get(proc);
            if (realproc == null) {
                throw new RuntimeException("unknown procedure: " + proc);
            }

            JSONObject args = (JSONObject) m.get("arguments");
            Object res = new ProcedureCaller(con).call(realproc, args);
            mo = new JSONObject();
            mo.put("result", res);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        response.setBufferSize(100000);
        response.setContentType("text/text;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {

            out.append(mo.toString());
        }
    }

    OracleConnection getOracleConnection(String user, String pw) throws SQLException {
        OracleConnection connection = (OracleConnection) DriverManager.getConnection(this.dburl, user, pw);
        return connection;
    }

    public static class UserPw {

        public final String user;
        public final String pw;

        public UserPw(String user, String pw) {
            this.user = user;
            this.pw = pw;
        }
    }

    OracleConnection authorize(String auth) throws IOException {
        UserPw up = userPwWithBasicAuthentication(auth);
        if (up != null) {
            try {
                return getOracleConnection(up.user, up.pw);
            } catch (SQLException ex) {
                ex.printStackTrace(System.err);
            }
        }
        return null;
    }

    static UserPw userPwWithBasicAuthentication(String authHeader) {
        if (authHeader != null) {
            StringTokenizer st = new StringTokenizer(authHeader);
            if (st.hasMoreTokens()) {
                String basic = st.nextToken();

                if (basic.equalsIgnoreCase("Basic")) {
                    String credentials;
                    try {
                        credentials = new String(Util.decodeBase64(st.nextToken()), "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        throw new Error(e);
                    }
                    int p = credentials.indexOf(":");
                    if (p != -1) {
                        String login = credentials.substring(0, p).trim();
                        String password = credentials.substring(p + 1).trim();
                        return new UserPw(login, password);
                    } else {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    static Map<String, String> loadProcedures(String filename) throws IOException {
        File f = new File(filename);
        java.util.Properties props = new java.util.Properties();
        FileReader fr = new FileReader(f);
        props.load(fr);
        HashMap<String, String> res = new HashMap<>();
        for (String s : props.stringPropertyNames()) {
            String p = props.getProperty(s);
            res.put(s, p);
        }
        return res;
    }
}
