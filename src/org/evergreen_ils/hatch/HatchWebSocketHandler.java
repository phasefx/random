/* -----------------------------------------------------------------------
 * Copyright 2014 Equinox Software, Inc.
 * Bill Erickson <berick@esilibrary.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * -----------------------------------------------------------------------
 */
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

    /** A single connection to a WebSockets client */
    private Session session;

    /** Current origin domain */
    private String origin;

    /** List of Origin domains from which we allow connections */
    private static String[] trustedDomains;

    /** True if we trust all Origin domains */
    private static boolean trustAllDomains = false;

    /** Root directory for all FileIO operations */
    private static String profileDirectory;

    /** Our logger instance */
    private static final Logger logger = Log.getLogger("WebSocketHandler");

    /**
     * Apply trusted domains.
     *
     * If the first domain in the list equals "*", that signifies that
     * all domains should be trusted.
     *
     * @param domains Array of domains to trust.
     */
    public static void setTrustedDomains(String[] domains) {
        trustedDomains = domains;

        if (domains.length > 0 ) {

            if ("*".equals(domains[0])) {
                logger.info("All domains trusted");
                trustAllDomains = true;

            } else {

                for(String domain : trustedDomains) {
                    logger.info("Trusted domain: " + domain);
                }
            }
        } else {
            logger.warn("No domains are trusted.  All requests will be denied");
        }
    }

    /**
     * Sets the profile directory
     *
     * @param directory Directory path as a String
     */
    public static void setProfileDirectory(String directory) {
        profileDirectory = directory;
    }


    /**
     * Runs the initial, global configuration for this handler.
     * TODO: move this into setProfileDirectory() (which will need to
     * be force-called regardless of config)?
     */
    public static void configure() {
        logger.info("WebSocketHandler.configure()");

        // default to ~/.evergreen
        if (profileDirectory == null) {
            String home = System.getProperty("user.home");
            profileDirectory = new File(home, ".evergreen").getPath();
            if (profileDirectory == null) {
                logger.info("Unable to set profile directory");
            }
        }
    }

    /**
     * Compares the Origin of the current WebSocket connection to the list
     * of allowed domains to determine if the current connection should
     * be allowed.
     *
     * @return True if the Origin domain is allowed, false otherwise.
     */
    protected boolean verifyOriginDomain() {
        logger.info("received connection from IP " +
            session.getRemoteAddress().getAddress());

        origin = session.getUpgradeRequest().getHeader("Origin");

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


    /**
     * WebSocket onConnect handler.
     *
     * Verify the Origin domain before any communication may take place
     */
    @OnWebSocketConnect
    public void onConnect(Session session) {
        this.session = session;
        if (!verifyOriginDomain()) session.close();
    }

    /**
     * WebSocket onClose handler.
     *
     * Clears our current session.
     */
    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        logger.info("onClose() statusCode=" + statusCode + ", reason=" + reason);
        this.session = null;
    }

    /**
     * Send a message to our connected client.
     *
     * @param json A JSON-encodable object to send to the caller.
     * @param msgid The message identifier
     */
    protected void reply(Object json, Long msgid) {
        reply(json, msgid, true);
    }

    /**
     * Send a message to our connected client.
     *
     * @param json A JSON-encodable object to send to the caller.
     * @param msgid The message identifier
     * @param success If false, the response will be packaged as an error 
     * message.
     */
    protected void reply(Object json, Long msgid, boolean success) {

        Map<String, Object> response = new HashMap<String, Object>();
        response.put("msgid", msgid);

        if (success) {
            response.put("content", json);
        } else {
            response.put("error", json);
        }

        String jsonString = JSON.toString(response);
        logger.info("replying with : " + jsonString);

        try {
            if (!success) logger.warn(jsonString);
            session.getRemote().sendString(jsonString);
        } catch (IOException e) {
            logger.warn(e);
        }
    }

    /**
     * WebSocket onMessage handler.
     *
     * Processes the incoming message and passes the request off to the 
     * necessary handler.  Messages must be encoded as JSON strings.
     */
    @OnWebSocketMessage
    @SuppressWarnings("unchecked") // direct casting JSON-parsed objects
    public void onMessage(String message) {
        if (session == null || !session.isOpen()) return;
        logger.info("onMessage() " + message);

        HashMap<String,Object> params = null;

        try {
            params = (HashMap<String,Object>) JSON.parse(message);
        } catch (ClassCastException e) {
            reply("Invalid WebSockets JSON message " + message, 
                new Long(-1), false);
        }

        Long msgid = (Long) params.get("msgid");
        String action = (String) params.get("action");
        String key = (String) params.get("key");
        String value = (String) params.get("value");
        String mime = (String) params.get("mime");

        logger.info("Received request for action " + action);

        // all requets require a message ID
        if (msgid == null) {
            reply("No msgid specified in request", msgid, false);
            return;
        }

        // all requests require an action
        if (action == null || action.equals("")) {
            reply("No action specified in request", msgid, false);
            return;
        }

        Object response = null;
        boolean error = false;
        FileIO io = new FileIO(profileDirectory, origin);

        switch (action) {
            case "keys":
                response = io.keys(key);
                break;

            case "printers":
                response = new PrintManager().getPrintersAsMaps();
                break;

            case "print":
                // pass ourselves off to the print handler so it can reply
                // for us after printing has completed.
                params.put("socket", this);
                Hatch.enqueueMessage(params);

                // we don't want to return a response below, since the 
                // FX thread will handle that for us.
                return;

            case "print-config":
                try {
                    response = new PrintManager().configurePrinter(params);
                } catch(IllegalArgumentException e) {
                    response = e.toString();
                    error = true;
                }
                break;

            case "get":
                String val = io.get(key);
                if (val != null) {
                    // set() stores bare JSON. We must pass an 
                    // Object to reply so that it may be embedded into
                    // a larger JSON response object, hence the JSON.parse().
                    try {
                        response = JSON.parse(val);
                    } catch(java.lang.IllegalStateException e) {
                        error = true;
                        response = "Error JSON-parsing stored value " + val;
                    }
                }
                break;

            case "remove":
                response = io.remove(key);
                break;

            case "set" :
                response = io.set(key, value);
                break;

            case "append" :
                response = io.append(key, value);
                break;

            default:
                response = "No such action: " + action;
                error = true;
        }

        reply(response, msgid, !error);
    }
}
