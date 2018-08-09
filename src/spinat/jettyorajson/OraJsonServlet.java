package spinat.jettyorajson;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import oracle.jdbc.OracleCallableStatement;
import oracle.jdbc.OracleConnection;
import oracle.jdbc.OracleTypes;
import org.json.simple.JSONObject;

public class OraJsonServlet extends HttpServlet {

    // these are the init parameters for the servlet
    String dburl = null;
    Map<String, String> procedures;
    String current_schema;
    String realm;
    String dbuser;
    String dbpassword;
    String allowOrigin;
    boolean tryWithoutTranslation;

    @Override
    public void init() {
        this.dburl = getInitParameter("dburl");
        this.current_schema = getInitParameter("current_schema");
        this.realm = getInitParameter("realm");
        this.dbuser = getInitParameter("dbuser");
        this.dbpassword = getInitParameter("dbpassword");
        try {
            this.procedures = loadProcedures(getInitParameter("procedures"));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        this.allowOrigin = getInitParameter("allow-origin");
        this.tryWithoutTranslation = "true".equals(getInitParameter("try-without-translation"));
    }

    void errorReply(HttpServletResponse response, String txt) throws IOException {
        response.setStatus(400);
        response.setContentType("text/text;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().append("error in request: " + txt);
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        setAccessControlHeaders(resp);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    private void setAccessControlHeaders(HttpServletResponse resp) {
        resp.setHeader("Access-Control-Allow-Origin", this.allowOrigin);
        resp.setHeader("Access-Control-Allow-Methods", "POST");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
    }

    // if the input is correct, a post parameter data which is JSON object 
    // which contains two slots, procedure argument
    // then in case of error return status 200 with an JSON object which conatins a slot error with some messae
    // otherwise return status 400 with some error message
    // reasoning: if the format of the request is correct, the client is expected to
    // analyse the response correctly
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        setAccessControlHeaders(response);
        final OracleConnection con;
        if (!this.realm.equals("")) {
            String auth = request.getHeader("Authorization");
            con = authorize(auth);
            if (con == null) {
                response.setHeader("WWW-Authenticate", "BASIC realm=\"" + this.realm + "\"");
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        } else {
            try {
                con = getOracleConnection(this.dbuser, this.dbpassword);
            } catch (SQLException ex) {
                errorReply(response, "can not login to database: " + ex.getMessage());
                return;
            }
        }
        try {
            setHeaders(request, con);
        } catch (SQLException ex) {
            errorReply(response, "can not set headers to database: " + ex.getMessage());
            ex.printStackTrace(System.err);
            return;
        }

        try {
            BufferedReader r = request.getReader();
            StringBuilder sb = new StringBuilder();
            char[] cb = new char[10000];
            while (true) {
                int n = r.read(cb);
                if (n > 0) {
                    sb.append(cb, 0, n);
                } else {
                    break;
                }
            }
            String jsonstring = sb.toString();

            if (jsonstring == null || jsonstring.isEmpty()) {
                errorReply(response, "expecting JSON data");
                return;
            }
            JSONObject m;
            try {
                org.json.simple.parser.JSONParser p = new org.json.simple.parser.JSONParser();
                m = (JSONObject) p.parse(jsonstring);
            } catch (org.json.simple.parser.ParseException ex) {
                errorReply(response, "value must be a JSON object, error:" + ex.toString());
                return;
            }
            final String procedureName;
            final JSONObject args;
            String pathinfo = request.getPathInfo();
            if (pathinfo == null || pathinfo.equals("") || pathinfo.equals("/")) {
                Object o = m.get("procedure");
                if (o == null || !(o instanceof String)) {
                    errorReply(response, "expecting a slot \"procedure\" which is a string value in JSON data");
                    return;
                }
                procedureName = (String) m.get("procedure");

                Object o2 = m.get("arguments");
                if (o2 == null || !(o2 instanceof JSONObject)) {
                    errorReply(response, "expecting a slot \"arguments\" which is a Object value in JSON data");
                    return;
                }
                args = (JSONObject) o2;
            } else {
                if (pathinfo.startsWith("/")) {
                    pathinfo = pathinfo.substring(1);
                }
                procedureName = pathinfo;
                args = m;
            }
            JSONObject mo = new JSONObject();
            String realproc = this.procedures.get(procedureName);
            if (realproc == null) {
                if (!this.tryWithoutTranslation) {
                    errorReply(response, "unknown procedure: " + procedureName
                            + ", procedure must be added to file " + getInitParameter("procedures"));
                    return;
                } else {
                    realproc = procedureName;
                }
            }
            try {
                Map<String, Object> res = new ProcedureCaller(con).call(realproc, args);
                mo.put("result", res);
            } catch (Exception e) {
                mo.put("error", e.toString());
                e.printStackTrace(System.err);
            }

            response.setBufferSize(100000);
            response.setContentType("application/json;charset=UTF-8");
            response.setCharacterEncoding("UTF-8");
            try (PrintWriter out = response.getWriter()) {
                out.append(mo.toString());
            }
        } finally {
            try {
                con.close();
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }
        }
    }

    private void setHeaders(HttpServletRequest request, OracleConnection con)
            throws SQLException {

        /*procedure init_cgi_env (num_params in number,
                           param_name in vc_arr,
                           param_val  in vc_arr);*/
        ArrayList<String> headerNames = new ArrayList<>();
        ArrayList<String> headerValues = new ArrayList<>();
        Enumeration<String> hn = request.getHeaderNames();
        while (hn.hasMoreElements()) {
            String headerName = hn.nextElement();
            headerNames.add(headerName);
            headerValues.add(request.getHeader(headerName));
        }

        try (OracleCallableStatement pstm
                = (OracleCallableStatement) con
                        .prepareCall("begin owa.init_cgi_env(?,?,?); end;")) {
                    int len = headerNames.size();
                    pstm.setInt(1, len);
                    pstm.setPlsqlIndexTable(2, headerNames.toArray(new String[0]), len, len, OracleTypes.VARCHAR, 1024);
                    pstm.setPlsqlIndexTable(3, headerValues.toArray(new String[0]), len, len, OracleTypes.VARCHAR, 1024);
                    pstm.execute();
                }
    }

    OracleConnection getOracleConnection(String user, String pw) throws SQLException {
        OracleConnection connection = (OracleConnection) DriverManager.getConnection(this.dburl, user, pw);
        if (this.current_schema != null) {
            try (Statement s = connection.createStatement()) {
                s.execute("ALTER SESSION SET CURRENT_SCHEMA=" + this.current_schema);
            }
        }
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
