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

// data structures
import java.util.Map;
import java.util.List;
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

