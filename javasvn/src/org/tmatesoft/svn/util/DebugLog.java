/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.util;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * @author TMate Software Ltd.
 */
public class DebugLog {
    
    public static void log(String message) {
        Logger.getLogger("svn").log(Level.FINE, message);
    }

    public static void log(Level level, String message) {
        Logger.getLogger("svn").log(level, message);
    }

    public static void benchmark(String message) {
        Logger.getLogger("svn").log(Level.CONFIG, message);
    }

    public static void error(String message) {
        Logger.getLogger("svn").log(Level.SEVERE, message);
    }

    public static void error(Throwable th) {
        Logger.getLogger("svn").log(Level.SEVERE, th.getMessage(), th);
    }
    
    public static boolean isSafeMode() {
        if (isSafeModeDefault() && System.getProperty("javasvn.safemode") == null) {
            return true;
        }
        return Boolean.getBoolean("javasvn.safemode");
    }
    
    public static boolean isGeneratorDisabled() {
        if (isSafeModeDefault()) {
            // have to enable explicitly
            return !Boolean.getBoolean("javasvn.generator.enabled");
        }
        if (System.getProperty("javasvn.generator.enabled") == null) {
            return false;
        }
        return !Boolean.getBoolean("javasvn.generator.enabled");
    }
    
    private static boolean isFileLoggingEnabled() {
        if (System.getProperty("javasvn.log.file") == null) {
            return true;
        }
        return Boolean.getBoolean("javasvn.log.file"); 
    }
    
    private static final File ourSafeModeTrigger = new File(".javasvn.safemode");
    private static final DateFormat ourDateFormat = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss.SSS");

    static {
        try {
            Logger.getLogger("svn").setUseParentHandlers(false);
            SimpleFormatter f = new SimpleFormatter() {
                public synchronized String format(LogRecord record) {
                    StringBuffer sb = new StringBuffer();
                    String message = formatMessage(record);
                    sb.append(ourDateFormat.format(new Date(record.getMillis())));                    
                    sb.append(": ");
                    sb.append(message);
                    sb.append(System.getProperty("line.separator"));
                    if (record.getThrown() != null) {
                        try {
                            StringWriter sw = new StringWriter();
                            PrintWriter pw = new PrintWriter(sw);
                            record.getThrown().printStackTrace(pw);
                            pw.close();
                            sb.append(sw.toString());
                        } catch (Exception e) {
                        }
                    }                    
                    return sb.toString();
                }
            };
            String levelStr = System.getProperty("javasvn.log.level");
            Level level;
            try {
                level = Level.parse(levelStr);
            } catch (Throwable th) {
                level = isSafeModeDefault() ? Level.FINEST : Level.FINEST;
            }
            if (isFileLoggingEnabled()) {
                String path = System.getProperty("javasvn.log.path");
                if (path == null) {
                    path = "%home%/.javasvn/.javasvn.%g.%u.log";
                } 
                path = path.replace(File.separatorChar, '/');
                path = path.replaceAll("%home%", System.getProperty("user.home").replace(File.separatorChar, '/'));
                path = path.replace('/', File.separatorChar);
                File dir = new File(path).getParentFile();
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                
                Handler handler = new FileHandler(path, 1024*1024, 10, true);
                handler.setFormatter(f);
                handler.setLevel(level);
                Logger.getLogger("svn").addHandler(handler);
            }
            if (Boolean.getBoolean("javasvn.log.console")) {
                ConsoleHandler cHandler = new ConsoleHandler();
                cHandler.setLevel(level);
                cHandler.setFilter(null);
                cHandler.setFormatter(f);                
                Logger.getLogger("svn").addHandler(cHandler);
            }
            Logger.getLogger("svn").setLevel(level);
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private static boolean isSafeModeDefault() {
        return ourSafeModeTrigger.exists();
    }
    
}
