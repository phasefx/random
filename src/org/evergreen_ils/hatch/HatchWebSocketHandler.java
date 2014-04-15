package org.evergreen_ils.hatch;

import java.io.IOException;
import java.io.File;
import java.io.BufferedReader;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import javax.servlet.ServletConfig;

import org.eclipse.jetty.util.ajax.JSON;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

@WebSocket
public class HatchWebSocketHandler {

    private Session session;
    static String[] trustedDomains;
    static String trustedDomainsString = null;
    static boolean trustAllDomains = false;
    static String profileDirectory;
    private static final Logger logger = Log.getLogger("WebSocketHandler");

    /**
     * config is passed in from our WebSocketServlet container, 
     * hence the public+static.  Possible to access directly?
     */
    //public static void configure(ServletConfig config) {
    public static void configure() {
        logger.info("WebSocketHandler.configure()");

        /*
        trustedDomainsString = 
            config.getServletContext().getInitParameter("trustedDomains");

        logger.info("trusted domains " + trustedDomainsString);

        profileDirectory = 
            config.getServletContext().getInitParameter("profileDirectory");
            */

        // default to ~/.evergreen
        if (profileDirectory == null) {
            String home = System.getProperty("user.home");
            profileDirectory = new File(home, ".evergreen").getPath();
            if (profileDirectory == null) {
                logger.info("Unable to set profile directory");
            }
        }   

        if (trustedDomainsString == null) {
            logger.info("No trusted domains configured");

        } else {

            if (trustedDomainsString.equals("*")) {
                trustAllDomains = true;
                logger.info("All domains trusted");

            } else {

                trustedDomains = trustedDomainsString.split(",");
                for(String domain : trustedDomains) {
                    logger.info("Trusted domain: " + domain);
                }
            }
        }
    }  

    protected boolean verifyOriginDomain() {
        logger.info("received connection from IP " + 
            session.getRemoteAddress().getAddress());

        String origin = session.getUpgradeRequest().getHeader("Origin");

        if (origin == null) {
            logger.warn("No Origin header in request; Dropping connection");
            return false;
        } 

        logger.info("connection origin is " + origin);

        if (trustAllDomains) return true;

        if (java.util.Arrays.asList(trustedDomains).indexOf(origin) < 0) {
            logger.warn("Request from un-trusted domain: " + origin);
            return false;
        }

        return true;
    }


    @OnWebSocketConnect
    public void onConnect(Session session) {
        this.session = session;
        if (!verifyOriginDomain()) session.close();
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        logger.info("onClose() statusCode=" + statusCode + ", reason=" + reason);
        this.session = null;
    }

    private void reply(Object json, String msgid) {
        reply(json, msgid, true);
    }

    private void reply(Object json, String msgid, boolean success) {

        Map<String, Object> response = new HashMap<String, Object>();
        response.put("msgid", msgid);
        if (success) {
            response.put("success", json);
        } else {
            response.put("error", json);
        }

        try {
            String jsonString = JSON.toString(response);
            if (!success) logger.warn(jsonString);
            session.getRemote().sendString(jsonString);
        } catch (IOException e) {
            logger.warn(e);
        }
    }

    @OnWebSocketMessage
    @SuppressWarnings("unchecked") // direct casting JSON-parsed objects
    public void onMessage(String message) {
        if (session == null || !session.isOpen()) return;
        logger.info("onMessage() " + message);

        HashMap<String,String> params = null;

        try {
            params = (HashMap<String,String>) JSON.parse(message);
        } catch (ClassCastException e) {
            reply("Invalid WebSockets JSON message " + message, "", false);
        }

        FileIO io;
        String msgid = params.get("msgid");
        String action = params.get("action");
        String key = params.get("key");
        String value = params.get("value");
        String mime = params.get("mime");

        // all requets require a message ID
        if (msgid == null || msgid.equals("")) {
            reply("No msgid specified in request", msgid, false);
            return;
        }

        // all requests require an action
        if (action == null || action.equals("")) {
            reply("No action specified in request", msgid, false);
            return;
        }

        if (action.equals("keys")) {
            io = new FileIO(profileDirectory);
            String[] keys = io.keys(key); // OK for key to be null
            if (keys != null) {
                reply(keys, msgid);
            } else {
                reply("key lookup error", msgid, false);
            }
            return;
        }

        if (action.equals("printers")) {
            List printers = new PrintManager().getPrinters();
            reply(printers, msgid);
            return;
        }

        if (action.equals("print")) {
            // TODO: validate the print target first so we can respond
            // with an error if the requested printer / attributes are
            // not supported.  Printing occurs in a separate thread,
            // so for now just assume it succeeded.  Maybe later add
            // a response queue and see if this handler is capable of
            // responding from an alternate thread.
            Hatch.enqueueMessage(params);
            reply("print succeeded", msgid);
            return;
        }

        // all remaining requests require a key
        if (key == null || key.equals("")) {
            reply("No key specified in request", msgid, false);
            return;
        }

        if (action.equals("get")) {
            io = new FileIO(profileDirectory);
            BufferedReader reader = io.get(key);
            if (reader != null) {
                String line;
                try {
                    while ( (line = reader.readLine()) != null) {
                        // relay lines of text to the caller as we read them
                        // assume the text content is JSON and return it
                        // un-JSON-ified.
                        reply(line, msgid);
                    }
                } catch (IOException e) {
                    logger.warn(e);
                }
            } else {
                reply("Error accessing property " + key, msgid, false);
            }
            return;
        }

        if (action.equals("delete")) {
            io = new FileIO(profileDirectory);
            if (io.delete(key)) {
                reply("Delete of " + key + " successful", msgid);
            } else {
                reply("Delete of " + key + " failed", msgid, false);
            }
            return;
        }

        // all remaining actions require value
        if (value == null) {
            reply("No value specified in request", msgid, false);
            return;
        }

        switch (action) {

            case "set" :
                io = new FileIO(profileDirectory);
                if (io.set(key, value)) {
                    reply("setting value for " + key + " succeeded", msgid);
                } else {
                    reply("setting value for " + key + " succeeded", msgid, false);
                }
                break;

            case "append" :
                io = new FileIO(profileDirectory);
                if (io.append(key, value)) {
                    reply("appending value for " + key + " succeeded", msgid);
                } else {
                    reply("appending value for " + key + " succeeded", msgid, false);
                }
                break;

            default:
                reply("No such action: " + action, msgid, false);
        }
    }
}
