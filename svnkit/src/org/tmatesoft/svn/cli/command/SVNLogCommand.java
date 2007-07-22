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

package org.tmatesoft.svn.cli.command;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.SVNChangeList;
import org.tmatesoft.svn.core.wc.SVNCompositePathList;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNPathList;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.xml.AbstractXMLHandler;
import org.tmatesoft.svn.core.wc.xml.SVNXMLLogHandler;
import org.tmatesoft.svn.core.wc.xml.SVNXMLSerializer;

/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class SVNLogCommand extends SVNCommand implements ISVNLogEntryHandler {

    private static final String SEPARATOR = "------------------------------------------------------------------------\n";
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");

    private PrintStream myPrintStream;
    private boolean myReportPaths;
    private boolean myIsQuiet;
    private boolean myHasLogEntries;

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }
    
    public void run(PrintStream out, PrintStream err) throws SVNException {
        String changelistName = (String) getCommandLine().getArgumentValue(SVNArgument.CHANGELIST); 
        SVNChangeList changelist = null;
        if (changelistName != null) {
            changelist = SVNChangeList.create(changelistName, new File(".").getAbsoluteFile());
            changelist.setOptions(getClientManager().getOptions());
            changelist.setRepositoryPool(getClientManager().getRepositoryPool());
            if (changelist.getPaths() == null || changelist.getPathsCount() == 0) {
                SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                                    "no such changelist ''{0}''", changelistName); 
                SVNErrorManager.error(error);
            }
        }
        
        SVNRevision[] revRange = getStartEndRevisions();
        SVNRevision startRevision = revRange[0];
        SVNRevision endRevision = revRange[1];

        boolean stopOnCopy = getCommandLine().hasArgument(SVNArgument.STOP_ON_COPY);
        myReportPaths = getCommandLine().hasArgument(SVNArgument.VERBOSE);
        myIsQuiet = getCommandLine().hasArgument(SVNArgument.QUIET);
        String limitStr = (String) getCommandLine().getArgumentValue(SVNArgument.LIMIT);
        myPrintStream = out;
        long limit = 0;
        if (limitStr != null) {
            try {
                limit = Long.parseLong(limitStr);
                if (limit <= 0) {
                    err.println("svn: Argument to --limit must be positive number");
                    return;
                }
            } catch (NumberFormatException nfe) {
                err.println("svn: Argument to --limit must be positive number");
                return;
            }
        }
        SVNLogClient logClient = getClientManager().getLogClient();
        ISVNLogEntryHandler handler = this;
        SVNXMLSerializer serializer = null;
        if (getCommandLine().hasArgument(SVNArgument.XML)) {
            serializer = new SVNXMLSerializer(out);
            handler = new SVNXMLLogHandler(serializer);
            if (!getCommandLine().hasArgument(SVNArgument.INCREMENTAL)) {
                ((AbstractXMLHandler) handler).startDocument();
            }                
        }
        if (getCommandLine().hasURLs()) {
            if (getCommandLine().getURLCount() > 1) {
                SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Only relative paths can be specified after a URL");
                SVNErrorManager.error(error);
            }
            String url = getCommandLine().getURL(0);
            SVNRevision pegRevision = getCommandLine().getPegRevision(0);
            Collection targets = new LinkedList();
            for(int i = 0; i < getCommandLine().getPathCount(); i++) {
                targets.add(getCommandLine().getPathAt(i));
            }
            if (changelist != null) {
                File[] paths = changelist.getPaths();
                String thisPath = new File(".").getAbsolutePath().replace(File.separatorChar, '/');
                for (int i = 0; i < changelist.getPathsCount(); i++) {
                    String path = paths[i].getAbsolutePath().replace(File.separatorChar, '/');
                    String relativePath = path.substring(thisPath.length());
                    relativePath = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
                    targets.add(relativePath);
                }
            }
            String[] paths = (String[]) targets.toArray(new String[targets.size()]);
            logClient.doLog(SVNURL.parseURIEncoded(url), paths, pegRevision, startRevision ,endRevision, stopOnCopy, myReportPaths, limit, handler);
        } else if (getCommandLine().hasPaths()) {
            if (getCommandLine().getPathCount() > 1) {
                SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "When specifying working copy paths, only one target may be given");
                SVNErrorManager.error(error);
            }
            Collection targets = new ArrayList(getCommandLine().getPathCount());
            SVNRevision pegRevision = null;
            for(int i = 0; i < getCommandLine().getPathCount(); i++) {
                targets.add(new File(getCommandLine().getPathAt(i)).getAbsoluteFile());
                if (i == 0) {
                    pegRevision = getCommandLine().getPathPegRevision(i);
                    if (changelist != null) {
                        changelist.setPegRevision(pegRevision);
                    }
                }
            }
            File[] paths = (File[]) targets.toArray(new File[targets.size()]);
            SVNPathList pathList = SVNPathList.create(paths, pegRevision);
            SVNCompositePathList combinedPathList = SVNCompositePathList.create(pathList, changelist, false);
            logClient.doLog(combinedPathList, startRevision ,endRevision, stopOnCopy, myReportPaths, limit, handler);
        }
        if (getCommandLine().hasArgument(SVNArgument.XML)) {
            if (!getCommandLine().hasArgument(SVNArgument.INCREMENTAL)) {
                ((AbstractXMLHandler) handler).endDocument();
            }
            try {
                serializer.flush();
            } catch (IOException e) {
            }
        } else if (myHasLogEntries) {
            myPrintStream.print(SEPARATOR);
            myPrintStream.flush();
        }
    }

    public void handleLogEntry(SVNLogEntry logEntry) {
        if (logEntry == null || (logEntry.getMessage() == null && logEntry.getRevision() == 0)) {
            return;
        }
        myHasLogEntries = true;
        StringBuffer result = new StringBuffer();
        String author = logEntry.getAuthor() == null ? "(no author)" : logEntry.getAuthor();
        String date = logEntry.getDate() == null ? "(no date)" : DATE_FORMAT.format(logEntry.getDate());
        String message = logEntry.getMessage();
        if (!myIsQuiet && message == null) {
            message = "";
        }
        result.append(SEPARATOR);
        result.append("r" + Long.toString(logEntry.getRevision()) + " | " + author + " | " + date);
        if (!myIsQuiet) {
            int count = getLinesCount(message);
            result.append(" | " + count + (count == 1 ? " line" : " lines"));
        }
        result.append("\n");
        if (myReportPaths && logEntry.getChangedPaths() != null) {
            Map sortedPaths = new TreeMap(logEntry.getChangedPaths());
            result.append("Changed paths:\n");
            for (Iterator paths = sortedPaths.keySet().iterator(); paths.hasNext();) {
                String path = (String) paths.next();
                SVNLogEntryPath lPath = (SVNLogEntryPath) sortedPaths.get(path);
                result.append("   " + lPath.getType() + " " + path);
                if (lPath.getCopyPath() != null) {
                    result.append(" (from " + lPath.getCopyPath() + ":" + lPath.getCopyRevision() + ")");
                }
                result.append("\n");
            }
        }
        if (!myIsQuiet) {
            result.append("\n" + message + "\n");
        }
        myPrintStream.print(result.toString());
        myPrintStream.flush();
    }
}
