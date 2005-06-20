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

package org.tmatesoft.svn.cli.command;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.io.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNLogEntry;
import org.tmatesoft.svn.core.io.SVNLogEntryPath;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.DebugLog;

import java.io.File;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author TMate Software Ltd.
 */
public class LogCommand extends SVNCommand implements ISVNLogEntryHandler {

    private static final String SEPARATOR = "------------------------------------------------------------------------\n";
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");

    private PrintStream myPrintStream;
    private boolean myReportPaths;
    private boolean myIsQuiet;

    public void run(PrintStream out, PrintStream err) throws SVNException {
        // parse revisions range.
        String revStr = (String) getCommandLine().getArgumentValue(SVNArgument.REVISION);
        SVNRevision startRevision = SVNRevision.UNDEFINED;
        SVNRevision endRevision = SVNRevision.UNDEFINED;
        if (revStr != null && revStr.indexOf(':') > 0) {
            startRevision = SVNRevision.parse(revStr.substring(0, revStr.indexOf(':')));
            endRevision = SVNRevision.parse(revStr.substring(revStr.indexOf(':') + 1));
        } else if (revStr != null) {
            endRevision = SVNRevision.parse(revStr);
        } else {
            startRevision = SVNRevision.create(1);
        }
        boolean stopOnCopy = getCommandLine().hasArgument(SVNArgument.STOP_ON_COPY);
        myReportPaths = getCommandLine().hasArgument(SVNArgument.VERBOSE);
        myIsQuiet = getCommandLine().hasArgument(SVNArgument.QUIET);
        final StringBuffer buffer = new StringBuffer();
        myPrintStream = new PrintStream(System.out, true) {
            public void print(String s) {
                super.print(s);
                buffer.append(s);
            }
        };

        SVNLogClient logClient = new SVNLogClient(getCredentialsProvider(), getOptions(), null);
        if (getCommandLine().hasURLs()) {
            String url = getCommandLine().getURL(0);
            Collection targets = new ArrayList();
            for(int i = 0; i < getCommandLine().getPathCount(); i++) {
                targets.add(getCommandLine().getPathAt(i));
            }
            String[] paths = (String[]) targets.toArray(new String[targets.size()]);
            logClient.doLog(url, paths, startRevision ,endRevision, stopOnCopy, myReportPaths, this);
        } else if (getCommandLine().hasPaths()) {
            Collection targets = new ArrayList();
            for(int i = 0; i < getCommandLine().getPathCount(); i++) {
                targets.add(new File(getCommandLine().getPathAt(i)).getAbsoluteFile());
            }
            File[] paths = (File[]) targets.toArray(new File[targets.size()]);
            logClient.doLog(paths, startRevision ,endRevision, stopOnCopy, myReportPaths, this);
        }
        DebugLog.log(buffer.toString());
        myPrintStream.print(SEPARATOR);
    }

    public void handleLogEntry(SVNLogEntry logEntry) {
        if (logEntry == null || (logEntry.getMessage() == null && logEntry.getRevision() == 0)) {
            return;
        }
        String author = logEntry.getAuthor() == null ? "(no author)" : logEntry.getAuthor();
        String date = logEntry.getDate() == null ? "(no date)" : DATE_FORMAT.format(logEntry.getDate());
        String message = logEntry.getMessage();
        if (!myIsQuiet && message == null) {
            message = "";
        }
        myPrintStream.print(SEPARATOR);
        myPrintStream.print("r" + Long.toString(logEntry.getRevision()) + " | " + author + " | " + date);
        if (!myIsQuiet) {
            int count = getLinesCount(message);
            myPrintStream.print(" | " + count + (count == 1 ? " line" : " lines"));
        }
        myPrintStream.print("\n");
        if (myReportPaths && logEntry.getChangedPaths() != null) {
            Map sortedPaths = new TreeMap(logEntry.getChangedPaths());
            myPrintStream.print("Changed paths:\n");
            for (Iterator paths = sortedPaths.keySet().iterator(); paths.hasNext();) {
                String path = (String) paths.next();
                SVNLogEntryPath lPath = (SVNLogEntryPath) sortedPaths.get(path);
                myPrintStream.print("   " + lPath.getType() + " " + path);
                if (lPath.getCopyPath() != null) {
                    myPrintStream.print(" (from " + lPath.getCopyPath() + ":" + lPath.getCopyRevision() + ")");
                }
                myPrintStream.print("\n");
            }
        }
        if (!myIsQuiet) {
            myPrintStream.print("\n" + message + "\n");
        }
        myPrintStream.flush();
    }
}
