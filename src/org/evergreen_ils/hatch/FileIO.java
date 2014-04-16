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

import java.io.*;
import java.util.LinkedList;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class FileIO {

    String basePath;
    private static final Logger logger = Log.getLogger("FileIO");

    public FileIO(String directory) {
        basePath = directory;
    }

    protected File getFile(String key) {
        File dir = new File(basePath);
        if (!dir.exists()) {
            if (!dir.mkdir()) {
                logger.info("Unable to create director: " + basePath);
                return null;
            }
        }
        return new File(dir, key);
    }

    public boolean set(String key, String text) {
        logger.info("set => " + key);
        File file = getFile(key);

        try {

            // delete the file if it exists
            if (!file.exists() && !file.createNewFile()) {
                logger.info(
                    "Unable to create file: " + file.getCanonicalPath());
                return false;
            }

            // destructive write (replace existing text)
            Writer outStream = new BufferedWriter(
                new FileWriter(file.getAbsoluteFile()));

            outStream.write(text);
            outStream.close();

        } catch(IOException e) {
            logger.warn("Error calling set() with key " + key);
            logger.warn(e);
            return false;
        }

        return true;
    }

    public boolean append(String key, String text) {
        logger.info("append => " + key);
        File file = getFile(key);

        try {

            // create the file if it doesn's already exist
            if (!file.exists() && !file.createNewFile()) {
                logger.info(
                    "Unable to create file: " + file.getCanonicalPath());
                return false;
            }

            // non-destructive write (append)
            Writer outStream = new BufferedWriter(
                new FileWriter(file.getAbsoluteFile(), true));
            outStream.write(text);
            outStream.close();

        } catch(IOException e) {
            logger.warn("Error in append() with key " + key);
            logger.warn(e);
            return false;
        }

        return true;
    }

    public BufferedReader get(String key) {
        logger.info("get => " + key);
        File file = getFile(key);
        if (!file.exists()) return null;

        StringBuffer sbuf = new StringBuffer();
        try {
            return new BufferedReader(
                new FileReader(file.getAbsoluteFile()));
        } catch (IOException e) {
            logger.warn("Error reading key: " + key);
            logger.warn(e);
            return null;
        }
    }

    public boolean delete(String key) {
        logger.info("delete => " + key);
        File file = getFile(key);
        try {
            if (file.exists() && !file.delete()) {
                logger.info(
                    "Unable to delete file: " + file.getCanonicalPath());
                return false;
            }
            return true;
        } catch (IOException e) {
            logger.warn("Error deleting key: " + key);
            logger.warn(e);
            return false;
        }
    }

    public String[] keys() {
        return keys(null);
    }

    public String[] keys(String prefix) {
        logger.info("keys => " + prefix);
        File dir = new File(basePath);
        if (!dir.exists()) return new String[0];

        LinkedList<String> nameList = new LinkedList<String>();
        File[] files = dir.listFiles();

        for (File file : files) {
            if (file.isFile()) {
                String name = file.getName();
                if (prefix == null) {
                    nameList.add(name);
                } else {
                    if (name.startsWith(prefix)) {
                        nameList.add(name);
                    }
                }
            }
        }

        return (String[]) nameList.toArray(new String[0]);
    }
}
