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
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.Attribute;
import javax.print.attribute.AttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Media;
import javax.print.attribute.standard.OrientationRequested;

import java.lang.IllegalArgumentException;

// data structures
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Set;
import java.util.LinkedHashSet;

public class PrintManager {

    /** Our logger instance */
    static final Logger logger = Log.getLogger("PrintManager");

    /**
     * Shows the print dialog, allowing the user to modify settings,
     * but performs no print.
     *
     * @param params Print request parameters.  This is the top-level
     * request object, containing the action, etc.  Within 'params'
     * will be a sub-object under the "config" key, which contains
     * the Printer configuration options (if any are already set).
     * @return A Map of printer settings extracted from the print dialog.
     */
    public Map<String,Object> configurePrinter(
        Map<String,Object> params) throws IllegalArgumentException {

        Map<String,Object> settings = 
            (Map<String,Object>) params.get("config");

        PrinterJob job = buildPrinterJob(settings);
        
        boolean approved = job.showPrintDialog(null);

        // no printing needed
        job.endJob(); 

        if (approved) {
            // extract modifications to the settings applied within the dialog
            return extractSettingsFromJob(job);
        } else {
            // return the unmodified settings back to the caller
            return settings;
        }
    }

    /**
     * Print the requested page using the provided settings
     *
     * @param engine The WebEngine instance to print
     * @param params Print request parameters
     */
    public void print(WebEngine engine, Map<String,Object>params) {

        Long msgid = (Long) params.get("msgid");
        Boolean showDialog = (Boolean) params.get("showDialog");

        Map<String,Object> settings = 
            (Map<String,Object>) params.get("config");

        HatchWebSocketHandler socket = 
            (HatchWebSocketHandler) params.get("socket");

        PrinterJob job = null;

        try {
            job = buildPrinterJob(settings);
        } catch(IllegalArgumentException e) {
            socket.reply(e.toString(), msgid, false);
            return;
        }

        if (showDialog != null && showDialog.booleanValue()) {
            logger.info("Print dialog requested");

            if (!job.showPrintDialog(null)) {
                // job canceled by user
                logger.info("after dialog");
                job.endJob();
                socket.reply("Print job canceled", msgid);
                return;
            }
        } else {
            logger.info("No print dialog requested");
        }

        Thread[] all = new Thread[100];
        int count = Thread.currentThread().enumerate(all);
        logger.info(count + " active threads in print");
        logger.info("Thread " + Thread.currentThread().getId() + " printing...");

        engine.print(job);
        logger.info("after print");

        job.endJob();

        socket.reply("Print job succeeded", msgid);
    }

    /**
     * Constructs a PrinterJob based on the provided settings.
     *
     * @param settings The printer configuration Map.
     * @return The newly created printer job.
     */
    public PrinterJob buildPrinterJob(
        Map<String,Object> settings) throws IllegalArgumentException {

        String name = (String) settings.get("printer");
        Printer printer = getPrinterByName(name);

        if (printer == null) 
            throw new IllegalArgumentException("No such printer: " + name);

        PageLayout layout = buildPageLayout(settings, printer);
        PrinterJob job = PrinterJob.createPrinterJob(printer);

        if (layout != null) job.getJobSettings().setPageLayout(layout);

        // apply any provided settings to the job
        applySettingsToJob(settings, job);

        return job;
    }

    /**
     * Builds a PageLayout for the requested printer, using the
     * provided settings.
     *
     * @param settings The printer configuration settings
     * @param printer The printer from which to spawn the PageLayout
     * @return The newly constructed PageLayout object.
     */
    protected PageLayout buildPageLayout(
            Map<String,Object> settings, Printer printer) {

        // modify the default page layout with our settings
        Map<String,Object> layoutMap = 
            (Map<String,Object>) settings.get("pageLayout");

        if (layoutMap == null) {
            // Start with a sane default.
            // The Java default is wonky
            return printer.createPageLayout(
                Paper.NA_LETTER,
                PageOrientation.PORTRAIT,
                Printer.MarginType.DEFAULT
            );
        }

        PrinterAttributes printerAttrs = printer.getPrinterAttributes();

        // find the paper by name
        Paper paper = null;
        String paperName = (String) layoutMap.get("paper");
        Set<Paper> papers = printerAttrs.getSupportedPapers();
        for (Paper source : papers) {
            if (source.getName().equals(paperName)) {
                logger.info("Found matching paper for " + paperName);
                paper = source;
                break;
            }
        }

        if (paper == null) 
            paper = printerAttrs.getDefaultPaper();

        return printer.createPageLayout(
            paper,
            PageOrientation.valueOf((String) layoutMap.get("pageOrientation")),
            ((Number) layoutMap.get("leftMargin")).doubleValue(),
            ((Number) layoutMap.get("rightMargin")).doubleValue(),
            ((Number) layoutMap.get("topMargin")).doubleValue(),
            ((Number) layoutMap.get("bottomMargin")).doubleValue()
        );
    }

    /**
     * Applies the provided settings to the PrinterJob.
     *
     * @param settings The printer configuration settings map.
     * @param job A PrinterJob, constructed from buildPrinterJob()
     */
    protected void applySettingsToJob(
            Map<String,Object> settings, PrinterJob job) {

        JobSettings jobSettings = job.getJobSettings();

        PrinterAttributes printerAttrs = 
            job.getPrinter().getPrinterAttributes();

        String collation = (String) settings.get("collation");
        Long copies = (Long) settings.get("copies");
        String printColor = (String) settings.get("printColor");
        String printQuality = (String) settings.get("printQuality");
        String printSides = (String) settings.get("printSides");
        String paperSource = (String) settings.get("paperSource");
        Object[] pageRanges = (Object[]) settings.get("pageRanges");

        if (collation != null) 
            jobSettings.setCollation(Collation.valueOf(collation));

        if (copies != null) 
            jobSettings.setCopies(((Long) settings.get("copies")).intValue());

        if (printColor != null) 
            jobSettings.setPrintColor(PrintColor.valueOf(printColor));

        if (printQuality != null) 
            jobSettings.setPrintQuality(PrintQuality.valueOf(printQuality));

        if (printSides != null) 
            jobSettings.setPrintSides(PrintSides.valueOf(printSides));

        // find the paperSource by name
        if (paperSource != null) {
            Set<PaperSource> paperSources = 
                printerAttrs.getSupportedPaperSources();

            // note: "Automatic" appears to be a virtual source,
            // meaning no source.. meaning let the printer decide.
            for (PaperSource source : paperSources) {
                if (source.getName().equals(paperSource)) {
                    logger.info("matched paper source for " + paperSource);
                    jobSettings.setPaperSource(source);
                    break;
                }
            }
        }


        if (pageRanges != null) {
            logger.info("pageRanges = " + pageRanges.toString());
            List<PageRange> builtRanges = new LinkedList<PageRange>();
            int i = 0, start = 0, end = 0;
            do {
                if (i % 2 == 0 && i > 0)
                    builtRanges.add(new PageRange(start, end));

                if (i == pageRanges.length) break;

                int current = ((Long) pageRanges[i]).intValue();
                if (i % 2 == 0) start = current; else end = current;

            } while (++i > 0);

            jobSettings.setPageRanges(builtRanges.toArray(new PageRange[0]));
        }
    }

    /**
     * Extracts and flattens the various configuration values from a 
     * PrinterJob and its associated printer and stores the values in a Map.
     *
     * @param job The PrinterJob whose attributes are to be extracted.
     * @return The extracted printer settings map.
     */
    protected Map<String,Object> extractSettingsFromJob(PrinterJob job) {
        Map<String,Object> settings = new HashMap<String,Object>();
        JobSettings jobSettings = job.getJobSettings();

        logger.info("Extracting print job settings from " + job);

        settings.put(
            jobSettings.collationProperty().getName(),
            jobSettings.collationProperty().getValue()
        );
        settings.put(
            jobSettings.copiesProperty().getName(),
            jobSettings.copiesProperty().getValue()
        );
        settings.put(
            "paperSource", 
            jobSettings.getPaperSource().getName()
        );
        settings.put(
            jobSettings.printColorProperty().getName(),
            jobSettings.printColorProperty().getValue()
        );
        settings.put(
            jobSettings.printQualityProperty().getName(),
            jobSettings.printQualityProperty().getValue()
        );
        settings.put(
            jobSettings.printSidesProperty().getName(),
            jobSettings.printSidesProperty().getValue()
        );

        // nested properties...
        
        // page layout --------------
        PageLayout layout = jobSettings.getPageLayout();
        Map<String,Object> layoutMap = new HashMap<String,Object>();
        layoutMap.put("bottomMargin", layout.getBottomMargin());
        layoutMap.put("leftMargin", layout.getLeftMargin());
        layoutMap.put("topMargin", layout.getTopMargin());
        layoutMap.put("rightMargin", layout.getRightMargin());
        layoutMap.put("pageOrientation", layout.getPageOrientation().toString());
        layoutMap.put("printableHeight", layout.getPrintableHeight());
        layoutMap.put("printableWidth", layout.getPrintableWidth());
        layoutMap.put("paper", layout.getPaper().getName());

        settings.put("pageLayout", layoutMap);

        // page ranges --------------
        PageRange[] ranges = jobSettings.getPageRanges();
        if (ranges != null) {
            List<Integer> pageRanges = new LinkedList<Integer>();

            if (ranges.length == 1 &&
                ranges[0].getStartPage() == 1 && 
                ranges[0].getEndPage() == Integer.MAX_VALUE) {
                // full range -- no need to store

            } else {
                for (PageRange range : ranges) {
                    pageRanges.add(range.getStartPage());
                    pageRanges.add(range.getEndPage());
                }
                settings.put("pageRanges", pageRanges);
            }
        }

        logger.info("compiled printer properties: " + settings.toString());
        return settings;
    }

    /**
     * Returns all known Printer's.
     *
     * @return Array of all printers
     */
    protected Printer[] getPrinters() {
        ObservableSet<Printer> printerObserver = Printer.getAllPrinters();

        if (printerObserver == null) return new Printer[0];

        return (Printer[]) printerObserver.toArray(new Printer[0]);
    }

    /**
     * Returns a list of all known printers, with their attributes 
     * encoded as a simple key/value Map.
     *
     * @return Map of printer information.
     */
    protected List<Map<String,Object>> getPrintersAsMaps() {
        Printer[] printers = getPrinters();

        List<Map<String,Object>> printerMaps = 
            new LinkedList<Map<String,Object>>();

        Printer defaultPrinter = Printer.getDefaultPrinter();

        for (Printer printer : printers) {
            HashMap<String, Object> printerMap = new HashMap<String, Object>();
            printerMaps.add(printerMap);
            printerMap.put("name", printer.getName());
            if (defaultPrinter != null && 
                printer.getName().equals(defaultPrinter.getName())) {
                printerMap.put("is-default", new Boolean(true));
            }
            logger.info("found printer " + printer.getName());            
        }

        return printerMaps;
    }


    /**
     * Returns the Printer with the specified name.
     *
     * @param name The printer name
     * @return The printer whose name matches the provided name, or null
     * if no such printer is found.
     */
    protected Printer getPrinterByName(String name) {
        Printer[] printers = getPrinters();
        for (Printer printer : printers) {
            if (printer.getName().equals(name))
                return printer;
        }
        return null;
    }
}

