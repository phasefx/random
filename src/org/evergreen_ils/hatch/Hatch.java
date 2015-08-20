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

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.xml.XmlConfiguration;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.scene.Node;
import javafx.stage.Stage;
import javafx.scene.transform.Scale;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Worker;
import javafx.concurrent.Worker.State;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.concurrent.WorkerStateEvent;
import java.util.concurrent.LinkedBlockingQueue;

import org.eclipse.jetty.util.ajax.JSON;

import java.util.Map;

import java.io.FileInputStream;

/**
 * Main class for Hatch.
 *
 * This class operates as a two-headed beast, whose heads will occasionally
 * communicate with each other.
 *
 * It runs a JavaFX thread for printing HTML documents and runs a Jetty
 * server thread for handling communication with external clients.
 *
 * Most of the work performed happens solely in the Jetty server thread.
 * Attempts to print, however, are passed into the JavaFX thread so that
 * the HTML may be loaded into a WebView for printing, which must happen
 * within the JavaFX thread.
 *
 * Messages are passed from the Jetty thread to the JavaFX thread via a
 * blocking thread queue, observed by a separate Service thread, whose 
 * job is only to pull messages from the queue.
 *
 * Beware: On Mac OS, the "FX Application Thread" is renamed to 
 * "AppKit Thread" when the first call to print() or showPrintDialog() 
 * [in PrintManager] is made.  This is highly confusing when viewing logs.
 *
 */
public class Hatch extends Application {

    /** Browser Region for rendering and printing HTML */
    private BrowserView browser;

    /** BrowserView requires a stage for rendering */
    private Stage primaryStage;
    
    /** Our logger instance */
    static final Logger logger = Log.getLogger("Hatch");

    /** Message queue for passing messages from the Jetty thread into
     * the JavaFX Application thread */
    private static LinkedBlockingQueue<Map> requestQueue =
        new LinkedBlockingQueue<Map>();

    /**
     * Printable region containing a browser
     */
    class BrowserView extends Region {
        WebView webView = new WebView();
        WebEngine webEngine = webView.getEngine();
        public BrowserView() {
            getChildren().add(webView);
        }
    }

    /**
     * Service task which listens for inbound messages from the
     * servlet.
     *
     * The code blocks on the concurrent queue, so it must be
     * run in a separate thread to avoid locking the main FX thread.
     */
    private static class MsgListenService extends Service<Map<String,Object>> {
        protected Task<Map<String,Object>> createTask() {
            return new Task<Map<String,Object>>() {
                protected Map<String,Object> call() {
                    while (true) {
                        logger.info("MsgListenService waiting for a message...");
                        try {
                            // take() blocks until a message is available
                            return requestQueue.take();
                        } catch (InterruptedException e) {
                            // interrupted, go back and listen
                            continue;
                        }
                    }
                }
            };
        }
    }


    /**
     * JavaFX startup call
     */
    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        startMsgTask();
    }

    /**
     * Queues a message for processing by the queue processing thread.
     */
    public static void enqueueMessage(Map<String,Object> params) {
        logger.debug("queueing print message");
        requestQueue.offer(params);
    }

    /**
     * Build a browser view from the print content, tell the
     * browser to print itself.
     */
    private void handlePrint(Map<String,Object> params) {
        String content = (String) params.get("content");
        String contentType = (String) params.get("contentType");

        if (content == null) {
            logger.warn("handlePrint() called with no content");
            return;
        }

        browser = new BrowserView();
        Scene scene = new Scene(browser);
        primaryStage.setScene(scene);

        browser.webEngine.getLoadWorker()
            .stateProperty()
            .addListener( (ChangeListener) (obsValue, oldState, newState) -> {
                logger.info("browser load state " + newState);
                if (newState == State.SUCCEEDED) {
                    logger.info("Print browser page load completed");

                    // Avoid nested UI event loops -- runLater
                    Platform.runLater(new Runnable() {
                        @Override public void run() {
                            new PrintManager().print(browser.webEngine, params);
                        }
                    });
                }
            });

        logger.info("printing " + content.length() + " bytes of " + contentType);
        browser.webEngine.loadContent(content, contentType);

        // After queueing up the HTML for printing, go back to listening
        // for new messages.
        startMsgTask();
    }

    /**
     * Fire off the Service task, which checks for queued messages.
     *
     * When a queued message is found, it's sent off for printing.
     */
    public void startMsgTask() {

        MsgListenService service = new MsgListenService();

        logger.info("starting MsgTask");

        service.setOnSucceeded(
            new EventHandler<WorkerStateEvent>() {

            @Override
            public void handle(WorkerStateEvent t) {
                logger.info("MsgTask handling message.. ");
                Map<String,Object> message = 
                    (Map<String,Object>) t.getSource().getValue();

                // avoid nesting UI event loops by kicking off the print
                // operation from the main FX loop after this event handler 
                // has exited.
                Platform.runLater(
                    new Runnable() {
                        @Override public void run() {
                            handlePrint(message);
                        }
                    }
                );
            }
        });

        service.start();
    }

    /**
     * Hatch main.
     *
     * Reads the Jetty configuration, starts the Jetty server thread, 
     * then launches the JavaFX Application thread.
     */
    public static void main(String[] args) throws Exception {

        // build a server from our hatch.xml configuration file
        XmlConfiguration configuration =
            new XmlConfiguration(new FileInputStream("hatch.xml"));

        Server server = (Server) configuration.configure();

        logger.info("Starting Jetty server");

        // start our server, but do not join(), since we want to server
        // to continue running in its own thread
        server.start();

        logger.info("Launching FX Application");

        // launch the FX Application thread
        launch(args);
    }
}
