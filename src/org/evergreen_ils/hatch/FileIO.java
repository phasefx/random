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
import java.util.Arrays;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class FileIO {

    /** All files are read from and written to this directory */
    String basePath;
    String originDomain;

    // routine for scrubbing invalid chars from file names / paths
    // http://stackoverflow.com/questions/1155107/is-there-a-cross-platform-java-method-to-remove-filename-special-chars
    final static int[] illegalChars = {
        34, 60, 62, 124, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 
        15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 
        58, 42, 63, 92, 47
    };

    static { Arrays.sort(illegalChars); }

    public static String cleanFileName(String badFileName) {
        char lastChar = 0;
        StringBuilder cleanName = new StringBuilder();
        for (int i = 0; i < badFileName.length(); i++) {
            int c = (int)badFileName.charAt(i);
            if (Arrays.binarySearch(illegalChars, c) < 0) {
                cleanName.append((char)c);
                lastChar = (char) c;
            } else {
                // avoid dupe-ing the placeholder chars, since that
                // relays no useful information (apart from the number
                // of illegal characters)
                // This is usefuf or things like https:// with dupe "/" chars
                if (lastChar != '_')
                    cleanName.append('_');
                lastChar = '_';
            }
        }
        return cleanName.toString();
    }
    // -------------------------------------------------- 

    // logger
    private static final Logger logger = Log.getLogger("FileIO");

    /**
     * Constructs a new FileIO with the provided base path.
     *
     * @param directory Directory to use in conjuction with the origin
     * (see below) as the base path for all file operations.
     * directory is assumed to be a valid path.
     * @param origin Origin domain of this request.  
     */
    public FileIO(String directory, String origin) {
        basePath = directory;  
        originDomain = cleanFileName(origin);
    }

    /**
     * Returns the base directory as a File for all file IO actions
     */
    protected File baseDir() {

        // basePath directory
        File dir = new File(basePath);
        if (!dir.exists()) {
            if (!dir.mkdir()) {
                logger.info("Unable to create directory: " + dir.getName());
                return null;
            }
        }

        // basePath + originDomain directory
        File subDir = new File(basePath, originDomain);
        if (!subDir.exists()) {
            if (!subDir.mkdir()) {
                logger.info("Unable to create directory: " + subDir.getName());
                return null;
            }
        }

        logger.info("baseDir: " + subDir.getName());
        return subDir;
    }

    /**
     * Locates the requested file by name within our configured base path.
     *
     * @param key The relative file name (key)
     * @return The File object if found.
     */
    protected File getFile(String key) {
        File baseDir = baseDir();
        if (baseDir == null) return null;
        key = cleanFileName(key);
        return new File(baseDir, key);
    }

    /**
     * Sets the content of a file.
     *
     * @param key The relative file name (key)
     * @param text The new file content
     *
     * @return success or failure
     */
    public boolean set(String key, String text) {
        logger.info("set => " + key);
        File file = getFile(key);

        if (text == null) return false;

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

    /**
     * Appends content to a file.
     *
     * If the file does not exist, it is first created.
     *
     * @param key The relative file name (key)
     * @param text The content to append to the file
     * @return success or failure
     */
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

    /**
     * Gets the text contents of a file.
     *
     * @param key The relative file name (key)
     * @return The text content of the file
     */
    public String get(String key) {
        logger.info("get => " + key);
        File file = getFile(key);
        if (!file.exists()) return null;

        String line;
        StringBuffer buf = new StringBuffer();

        try {
            BufferedReader reader = new BufferedReader(
                new FileReader(file.getAbsoluteFile()));

            while ( (line = reader.readLine()) != null) {
                buf.append(line);
            }
        } catch (IOException e) {
            logger.warn("Error reading key: " + key);
            logger.warn(e);
            return null;
        }

        return buf.toString();
    }

    /**
     * Removes (deletes) a file.
     *
     * @param The relative file name (key)
     * @return success or failure
     */
    public boolean remove(String key) {
        logger.info("remove => " + key);
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

    /**
     * Returns the full list of stored keys.
     *
     * @return Array of relative file names
     */
    public String[] keys() {
        return keys(null);
    }

    /**
     * Returns all keys begining with the specified prefix.
     *
     * @param prefix The initial substring used to limit the return set 
     * of keys.  If the prefix is null, all keys are returned.
     * @return Array of keys
     */
    public String[] keys(String prefix) {
        logger.info("keys => " + prefix);

        File dir = baseDir();
        if (dir == null || !dir.exists()) 
            return new String[0];

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
