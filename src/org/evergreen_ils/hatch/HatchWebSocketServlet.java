package org.evergreen_ils.hatch;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import javax.servlet.annotation.WebServlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

public class HatchWebSocketServlet extends WebSocketServlet {

    static final Logger logger = Log.getLogger("WebSocketServlet");

    @Override
    public void configure(WebSocketServletFactory factory) {
        factory.register(HatchWebSocketHandler.class);
    }

    @Override
    public void init(ServletConfig config) throws ServletException {  
        super.init(config); // required for WS
        //HatchWebSocketHandler.configure(config);
        HatchWebSocketHandler.configure();
    }  
}

