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
import java.util.Date;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNFormatUtil;
import org.tmatesoft.svn.core.wc.ISVNAnnotateHandler;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.xml.AbstractXMLHandler;
import org.tmatesoft.svn.core.wc.xml.SVNXMLAnnotateHandler;
import org.tmatesoft.svn.core.wc.xml.SVNXMLSerializer;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNAnnotateCommand extends SVNCommand implements ISVNAnnotateHandler {

    private boolean myIsVerbose;
    private PrintStream myPrintStream;

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

    public void run(PrintStream out, PrintStream err) throws SVNException {
        SVNLogClient logClient = getClientManager().getLogClient();
        if (getCommandLine().hasArgument(SVNArgument.EXTENSIONS)) {
            SVNDiffOptions diffOptions = new SVNDiffOptions(getCommandLine().hasArgument(SVNArgument.IGNORE_ALL_WS),
                    getCommandLine().hasArgument(SVNArgument.IGNORE_WS_CHANGE), 
                    getCommandLine().hasArgument(SVNArgument.IGNORE_EOL_STYLE));
            logClient.setDiffOptions(diffOptions);
        }
        myIsVerbose = getCommandLine().hasArgument(SVNArgument.VERBOSE);
        myPrintStream = out;
        SVNRevision startRevision = SVNRevision.UNDEFINED;
        SVNRevision endRevision = SVNRevision.UNDEFINED;
        String revStr = (String) getCommandLine().getArgumentValue(SVNArgument.REVISION);
        boolean force = getCommandLine().hasArgument(SVNArgument.FORCE);
        if (revStr != null && revStr.indexOf(':') > 0) {
            startRevision = SVNRevision.parse(revStr.substring(0, revStr.indexOf(':')));
            endRevision = SVNRevision.parse(revStr.substring(revStr.indexOf(':') + 1));
        } else if (revStr != null) {
            endRevision = SVNRevision.parse(revStr);
        }
        ISVNAnnotateHandler handler = this;
        SVNXMLSerializer serializer = null;
        if (getCommandLine().hasArgument(SVNArgument.XML)) {
            serializer = new SVNXMLSerializer(System.out);
            handler = new SVNXMLAnnotateHandler(serializer);
            if (!getCommandLine().hasArgument(SVNArgument.INCREMENTAL)) {
                ((AbstractXMLHandler) handler).startDocument();
            }
        }
        
        for(int i = 0; i < getCommandLine().getURLCount(); i++) {
            String url = getCommandLine().getURL(i);
            SVNRevision pegRevision = getCommandLine().getPegRevision(i);
            if (serializer != null) {
                ((SVNXMLAnnotateHandler) handler).startTarget(url);
            }
            SVNRevision endRev = endRevision;
            if ((endRevision == null || !endRevision.isValid()) && (pegRevision == null || !pegRevision.isValid())) {
                endRev = SVNRevision.HEAD;
            }
            try {
                logClient.doAnnotate(SVNURL.parseURIEncoded(url), pegRevision, startRevision, endRev, force, handler, null);
            } catch (SVNException e) {
                if (e.getMessage() != null && e.getMessage().indexOf("binary") >= 0) {
                    out.println("Skipping binary file: '" + url + "'");
                } else {
                    throw e;
                }
            }
            if (serializer != null) {
                ((SVNXMLAnnotateHandler) handler).endTarget();
            }
        }
        endRevision = parseRevision(getCommandLine());
        for(int i = 0; i < getCommandLine().getPathCount(); i++) {
            File path = new File(getCommandLine().getPathAt(i)).getAbsoluteFile();
            SVNRevision pegRevision = getCommandLine().getPathPegRevision(i);
            if (serializer != null) {
                ((SVNXMLAnnotateHandler) handler).startTarget(getCommandLine().getPathAt(i));
            }
            SVNRevision endRev = endRevision;
            if ((endRevision == null || !endRevision.isValid()) && (pegRevision == null || !pegRevision.isValid())) {
                endRev = SVNRevision.BASE;
            }
            try {
                logClient.doAnnotate(path, pegRevision, startRevision, endRev, force, handler);
            } catch (SVNException e) {
                if (e.getMessage() != null && e.getMessage().indexOf("binary") >= 0) {
                    err.println("Skipping binary file: '" + SVNFormatUtil.formatPath(path) + "'");
                } else {
                    throw e;
                }
            }
            if (serializer != null) {
                ((SVNXMLAnnotateHandler) handler).endTarget();
            }
        }
        if (getCommandLine().hasArgument(SVNArgument.XML)) {
            if (!getCommandLine().hasArgument(SVNArgument.INCREMENTAL)) {
                ((AbstractXMLHandler) handler).endDocument();
            }
            try {
                serializer.flush();
            } catch (IOException e) {
            }
        }
    }

    public void handleLine(Date date, long revision, String author, String line) {
        StringBuffer result = new StringBuffer();
        if (myIsVerbose) {
            if (revision >= 0) {
                result.append(SVNFormatUtil.formatString(Long.toString(revision), 6, false));
                result.append(' ');
            } else {
                result.append("     -");
            }
            result.append(' ');
            result.append(author != null ? SVNFormatUtil.formatString(author, 10, false) : "         -");
            result.append(' ');
            if (date != null) {
                result.append(SVNFormatUtil.formatHumanDate(date, getClientManager().getOptions()));
            } else {
                result.append("                                           -");
            }
            result.append(' ');
        } else {
            result.append(SVNFormatUtil.formatString(Long.toString(revision), 6, false));
            result.append(' ');
            result.append(author != null ? SVNFormatUtil.formatString(author, 10, false) : "         -");
            result.append(' ');
        }
        result.append(line);
        myPrintStream.println(result.toString());
    }
}
