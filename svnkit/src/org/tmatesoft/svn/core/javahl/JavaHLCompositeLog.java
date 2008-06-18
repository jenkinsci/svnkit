/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.javahl;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.tmatesoft.svn.util.ISVNDebugLog;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNDebugLogAdapter;


/**
 * @author TMate Software Ltd.
 * @version 1.1.1
 */
public class JavaHLCompositeLog extends SVNDebugLogAdapter {

    Set myLoggers;

    public JavaHLCompositeLog() {
        myLoggers = new HashSet();
        myLoggers.add(SVNDebugLog.getDefaultLog());
    }

    public void addLogger(ISVNDebugLog debugLog) {
        myLoggers.add(debugLog);
    }

    public void removeLogger(ISVNDebugLog debugLog) {
        myLoggers.remove(debugLog);        
    }

    public void logInfo(String message) {
        for (Iterator iterator = myLoggers.iterator(); iterator.hasNext();) {
            ISVNDebugLog log = (ISVNDebugLog) iterator.next();
            log.logInfo(message);
        }
    }

    public void logInfo(Throwable th) {
        for (Iterator iterator = myLoggers.iterator(); iterator.hasNext();) {
            ISVNDebugLog log = (ISVNDebugLog) iterator.next();
            log.logInfo(th);
        }
    }

    public void logSevere(String message) {
        for (Iterator iterator = myLoggers.iterator(); iterator.hasNext();) {
            ISVNDebugLog log = (ISVNDebugLog) iterator.next();
            log.logSevere(message);
        }
    }

    public void logSevere(Throwable th) {
        for (Iterator iterator = myLoggers.iterator(); iterator.hasNext();) {
            ISVNDebugLog log = (ISVNDebugLog) iterator.next();
            log.logSevere(th);
        }
    }

    public void log(String message, byte[] data) {
        for (Iterator iterator = myLoggers.iterator(); iterator.hasNext();) {
            ISVNDebugLog log = (ISVNDebugLog) iterator.next();
            log.log(message, data);
        }
    }

    public void logFine(Throwable th) {
        for (Iterator iterator = myLoggers.iterator(); iterator.hasNext();) {
            ISVNDebugLog log = (ISVNDebugLog) iterator.next();
            log.logFine(th);
        }
    }

    public void logFine(String message) {
        for (Iterator iterator = myLoggers.iterator(); iterator.hasNext();) {
            ISVNDebugLog log = (ISVNDebugLog) iterator.next();
            log.logFine(message);
        }
    }

    public void logFiner(Throwable th) {
        for (Iterator iterator = myLoggers.iterator(); iterator.hasNext();) {
            ISVNDebugLog log = (ISVNDebugLog) iterator.next();
            log.logFiner(th);
        }
    }

    public void logFiner(String message) {
        for (Iterator iterator = myLoggers.iterator(); iterator.hasNext();) {
            ISVNDebugLog log = (ISVNDebugLog) iterator.next();
            log.logFiner(message);
        }
    }

    public void logFinest(Throwable th) {
        for (Iterator iterator = myLoggers.iterator(); iterator.hasNext();) {
            ISVNDebugLog log = (ISVNDebugLog) iterator.next();
            log.logFinest(th);
        }
    }

    public void logFinest(String message) {
        for (Iterator iterator = myLoggers.iterator(); iterator.hasNext();) {
            ISVNDebugLog log = (ISVNDebugLog) iterator.next();
            log.logFinest(message);
        }
    }
}
