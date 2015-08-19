package spinat.jettyorajson;

import java.io.File;
import java.io.FileReader;
import java.net.URL;
import java.util.HashSet;


import org.apache.log4j.PropertyConfigurator;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;

public class Main {

    private static void msg(String s) {
        System.out.println("### " + s);
    }

    public static void main(String[] args) throws Exception {

        if (args.length < 1) {
            throw new RuntimeException("Expecting one argument, the name of the configuration file");
        }
        String propFileName = args[0];
        
        File pf = new File(propFileName);
        File apf = pf.getAbsoluteFile();
        if (!apf.canRead()) {
            throw new RuntimeException("can not read file: " + apf);
        }
        msg("using property file: " + apf);
        File dpf = apf.getParentFile();
        msg("setting user.dir to " + dpf.toString());
        System.setProperty("user.dir", dpf.toString());

        java.util.Properties props = new java.util.Properties();
        FileReader fr = new FileReader(apf);
        props.load(fr);

        String log4jprops = props.getProperty("log4jconfig", "");
        if (log4jprops.equals("")) {
            log4jprops = "log4j.properties";
        }
        File l4jf = new File(log4jprops).getAbsoluteFile();
        URL l4ju = l4jf.toURI().toURL();
        msg("log4j config file " + l4jf);
        PropertyConfigurator.configure(l4ju);

        String port = props.getProperty("port", "");
        int intPort;
        if (port.equals("")) {
            intPort = 8080;
        } else {
            intPort = Integer.parseInt(port);
        }
        msg("using port " + intPort);
        // Create a basic jetty server object that will listen on port 8080.  Note that if you set this to port 0
        // then a randomly available port will be assigned that you can either look in the logs for the port,
        // or programmatically obtain it for use in test cases.
        Server server = new Server(intPort);
        HandlerList handlers = new HandlerList();
        server.setHandler(handlers);

        // The ServletHandler is a dead simple way to create a context handler that is backed by an instance of a
        // Servlet.  This handler then needs to be registered with the Server object.
        ServletHandler handler = new ServletHandler();
        // Passing in the class for the servlet allows jetty to instantite an instance of that servlet and mount it
        // on a given context path.

        // !! This is a raw Servlet, not a servlet that has been configured through a web.xml or anything like that !!
         ServletHolder holder = new ServletHolder(OraJsonServlet.class);

         holder.setInitParameter("dburl",  props.getProperty("dburl"));
         holder.setInitParameter("realm",  props.getProperty("realm"));
         String proceduresFileName = props.getProperty("procedures");
         File proceduresFile = new File(proceduresFileName);
         String a;
         if (proceduresFile.isAbsolute()) {
             a = proceduresFile.getCanonicalPath();
         } else {
             a = dpf.getCanonicalPath()+"/" + proceduresFile.getName();
         }
         
         holder.setInitParameter("procedures",a);
         
         handler.addServletWithMapping(holder, "/orajson/*");
         handlers.addHandler(handler);
         
        HashSet<String> set = new HashSet<>();
        for (String s : props.stringPropertyNames()) {
            if (s.startsWith("static.")) {
                String part = s.substring("static.".length());
                if (part.equals("")) {
                    continue;
                }
                int p = part.indexOf(".");
                if (p == 0) {
                    continue;
                }
                if (p < 0) {
                    set.add(part);
                } else {
                    set.add(part.substring(0, p));
                }
            }
        }
        for (String staticc : set) {
            String prefix = "static." + staticc + ".";
            String dir = props.getProperty(prefix + "dir", "");
            if (dir.equals("")) {
                // obviously nonsens
                continue;
            }
            File f = new File(dir).getAbsoluteFile();
            if (!f.isDirectory()) {
                throw new RuntimeException("not a dir " + f);
            }
            String mount = props.getProperty(prefix + "wdir", "");
            if (mount.equals("")) {
                continue;
            }
            ResourceHandler resource_handler = new ResourceHandler();
            resource_handler.setDirectoriesListed(true);
            resource_handler.setResourceBase(f.toString());

            String cc = props.getProperty(prefix + "cache-control", "");
            if (!cc.equals("")) {
                resource_handler.setCacheControl(cc);
            }
            ContextHandler contextHandler = new ContextHandler(mount);
            contextHandler.setHandler(resource_handler);
            msg("adding static dir " + mount + " -> " + f.toString());
            handlers.addHandler(contextHandler);
        }
        server.setHandler(handlers);
        // Start things up! By using the server.join() the server thread will join with the current thread.
        // See "http://docs.oracle.com/javase/1.5.0/docs/api/java/lang/Thread.html#join()" for more details.
        server.start();
        server.join();
    }
}
