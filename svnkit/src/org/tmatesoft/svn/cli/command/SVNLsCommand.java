/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
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
import java.util.Date;
import java.util.TimeZone;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.xml.SVNXMLDirEntryHandler;
import org.tmatesoft.svn.core.wc.xml.SVNXMLSerializer;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNLsCommand extends SVNCommand implements ISVNDirEntryHandler {

    private PrintStream myPrintStream;
    private boolean myIsVerbose;
    
    private static final DateFormat LONG_DATE_FORMAT = new SimpleDateFormat("MM' 'dd'  'yyyy");
    private static final DateFormat SHORT_DATE_FORMAT = new SimpleDateFormat("MM' 'dd'  'HH:mm");
    
    static {
        SHORT_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
        LONG_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
    }
    
    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

    public void run(PrintStream out, PrintStream err) throws SVNException {
        boolean recursive = getCommandLine().hasArgument(SVNArgument.RECURSIVE);
        myIsVerbose = getCommandLine().hasArgument(SVNArgument.VERBOSE);
        myPrintStream = out;
        
        boolean isXml = getCommandLine().hasArgument(SVNArgument.XML);
        SVNXMLSerializer serializer = isXml ? new SVNXMLSerializer(myPrintStream) : null;
        SVNXMLDirEntryHandler handler = isXml ? new SVNXMLDirEntryHandler(serializer) : null;

        SVNRevision revision = parseRevision(getCommandLine());
        SVNLogClient logClient = getClientManager().getLogClient();
        if (!getCommandLine().hasURLs() && !getCommandLine().hasPaths()) {
            getCommandLine().setPathAt(0, "");
        }
        if (handler != null) {
            if (!getCommandLine().hasArgument(SVNArgument.INCREMENTAL)) {
                handler.startDocument();
            }
        }
        for(int i = 0; i < getCommandLine().getURLCount(); i++) {
            String url = getCommandLine().getURL(i);
            if (handler != null) {
                handler.startTarget(url);
            }
            logClient.doList(SVNURL.parseURIEncoded(url), getCommandLine().getPegRevision(i), revision == null ? SVNRevision.UNDEFINED : revision, myIsVerbose || isXml, recursive, isXml ? handler : (ISVNDirEntryHandler) this);
            if (handler != null) {
                handler.endTarget();
            }
        }
        for(int i = 0; i < getCommandLine().getPathCount(); i++) {
            File path = new File(getCommandLine().getPathAt(i)).getAbsoluteFile();
            if (handler != null) {
                handler.startTarget(path.getAbsolutePath().replace(File.separatorChar, '/'));
            }
            logClient.doList(path, getCommandLine().getPathPegRevision(i), revision == null || !revision.isValid() ? SVNRevision.BASE : revision, myIsVerbose || isXml, recursive, isXml ? handler : (ISVNDirEntryHandler) this);
            if (handler != null) {
                handler.endTarget();
            }
        }
        if (handler != null) {
            if (!getCommandLine().hasArgument(SVNArgument.INCREMENTAL)) {
                handler.endDocument();
            }
            try {
                serializer.flush();
            } catch (IOException e) {
            }
        }
    }

    public void handleDirEntry(SVNDirEntry dirEntry) {
        if (myIsVerbose) {
            StringBuffer verbose = new StringBuffer();
            verbose.append(SVNCommand.formatString(dirEntry.getRevision() + "", 7, false));
            verbose.append(' ');
            verbose.append(SVNCommand.formatString(dirEntry.getAuthor() == null ? " ? " : dirEntry.getAuthor(), 16, true));
            verbose.append(' ');
            verbose.append(dirEntry.getLock() != null ? 'O' : ' ');
            verbose.append(' ');
            verbose.append(SVNCommand.formatString(dirEntry.getKind() == SVNNodeKind.DIR ? "" : dirEntry.getSize() + "", 10, false));
            verbose.append(' ');
            // time now.
            Date d = dirEntry.getDate();
            String timeStr = "";
            if (d != null) {
                if (System.currentTimeMillis() - d.getTime() < 365 * 1000 * 86400 / 2) {
                    timeStr = SHORT_DATE_FORMAT.format(d);
                } else {
                    timeStr = LONG_DATE_FORMAT.format(d);
                }
            }
            verbose.append(SVNCommand.formatString(timeStr, 12, false));
            verbose.append(' ');
            myPrintStream.print(verbose.toString());
        }
        myPrintStream.print(dirEntry.getRelativePath());
        if (dirEntry.getKind() == SVNNodeKind.DIR) {
            myPrintStream.print('/');
        }
        myPrintStream.println();
    }

}
