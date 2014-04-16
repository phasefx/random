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
        HatchWebSocketHandler.configure();
    }
}

