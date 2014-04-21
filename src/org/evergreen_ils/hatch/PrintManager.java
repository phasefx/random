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

// logging
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

// printing
import javafx.print.*;
import javafx.scene.web.WebEngine;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.Attribute;
import javax.print.attribute.AttributeSet;
import javax.print.attribute.standard.Media;
import javax.print.attribute.standard.OrientationRequested;

// data structures
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

public class PrintManager {

    static final Logger logger = Log.getLogger("PrintManager");

    public void print(WebEngine engine) {
        debugPrintService(null); // testing

        Printer printer = Printer.getDefaultPrinter();
        PrinterJob job = PrinterJob.createPrinterJob();
        if (!job.showPrintDialog(null)) return; // print canceled by user
        engine.print(job);
        job.endJob();
    }

    private void debugPrintService(PrintService printer) {

        PrintService[] printServices;
        String defaultPrinter = "";

        if (printer != null) {
            printServices = new PrintService[] {printer};
        } else {
            printServices = PrintServiceLookup.lookupPrintServices(null, null);
            PrintService def = PrintServiceLookup.lookupDefaultPrintService();
            if (def != null) defaultPrinter = def.getName();
        }

        for (PrintService service : printServices) {
            logger.info("Printer Debug: found printer " + service.getName());
            if (service.getName().equals(defaultPrinter)) {
                logger.info("    Printer Debug: Is Default");
            }

            AttributeSet attributes = service.getAttributes();
            for (Attribute a : attributes.toArray()) {
                String name = a.getName();
                String value = attributes.get(a.getClass()).toString();
                logger.info("    Printer Debug: " + name + " => " + value);
            }
        }
    }

    public List<HashMap> getPrinters() {

        List<HashMap> printers = new LinkedList<HashMap>();
        PrintService[] printServices =
            PrintServiceLookup.lookupPrintServices(null, null);

        String defaultPrinter = "";
        PrintService def = PrintServiceLookup.lookupDefaultPrintService();
        if (def != null) defaultPrinter = def.getName();

        for (PrintService service : printServices) {
            HashMap<String, Object> printer = new HashMap<String, Object>();
            printers.add(printer);

            if (service.getName().equals(defaultPrinter))
                printer.put("is-default", new Boolean(true));

            // collect information about the printer attributes we care about
            Class[] attrClasses = {
                Media.class, 
                //OrientationRequested.class
            };

            for (Class c : attrClasses) {
                Attribute[] attrs = (Attribute[])
                    service.getSupportedAttributeValues(c, null, null);

                if (attrs.length > 0) {
                    ArrayList<String> values = new ArrayList<String>(attrs.length);
                    for (Attribute a : attrs) {
                        String s = a.toString();
                        if (!values.contains(s)) values.add(s);
                    }
                    printer.put(attrs[0].getName(), values);
                }
            }

            AttributeSet attributes = service.getAttributes();
            for (Attribute a : attributes.toArray()) {
                String name = a.getName();
                String value = attributes.get(a.getClass()).toString();
                printer.put(name, value);
            }
        }

        return printers;
    }
}

