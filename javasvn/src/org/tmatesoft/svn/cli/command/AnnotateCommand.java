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

import java.io.File;
import java.io.PrintStream;
import java.util.Date;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.ISVNAnnotateHandler;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.TimeUtil;
import org.tmatesoft.svn.util.SVNUtil;

/**
 * @author TMate Software Ltd.
 */
public class AnnotateCommand extends SVNCommand implements ISVNAnnotateHandler {

    private boolean myIsVerbose;
    private PrintStream myPrintStream;

    public void run(PrintStream out, PrintStream err) throws SVNException {
        SVNLogClient logClient = getClientManager().getLogClient();
        myIsVerbose = getCommandLine().hasArgument(SVNArgument.VERBOSE);
        myPrintStream = out;
        SVNRevision endRevision = parseRevision(getCommandLine());
        if (endRevision == null || !endRevision.isValid()) {
            endRevision = SVNRevision.HEAD;
        }
        for(int i = 0; i < getCommandLine().getURLCount(); i++) {
            String url = getCommandLine().getURL(i);
            SVNRevision pegRevision = getCommandLine().getPegRevision(i);
            try {
                logClient.doAnnotate(url, pegRevision, SVNRevision.UNDEFINED, endRevision, this);
            } catch (SVNException e) {
                if (e.getMessage() != null && e.getMessage().indexOf("binary") >= 0) {
                    out.println("Skipping binary file: '" + url + "'");
                } else {
                    throw e;
                }
            }
        }
        endRevision = parseRevision(getCommandLine());
        if (endRevision == null || !endRevision.isValid()) {
            endRevision = SVNRevision.BASE;
        }
        for(int i = 0; i < getCommandLine().getPathCount(); i++) {
            File path = new File(getCommandLine().getPathAt(i)).getAbsoluteFile();
            SVNRevision pegRevision = getCommandLine().getPathPegRevision(i);
            try {
                logClient.doAnnotate(path, pegRevision, SVNRevision.UNDEFINED, endRevision, this);
            } catch (SVNException e) {
                if (e.getMessage() != null && e.getMessage().indexOf("binary") >= 0) {
                    out.println("Skipping binary file: '" + SVNUtil.getPath(path) + "'");
                } else {
                    throw e;
                }
            }
        }
    }
    public void handleLine(Date date, long revision, String author, String line) {
        StringBuffer result = new StringBuffer();
        if (myIsVerbose) {
            result.append(Long.toString(revision));
            result.append(' ');
            result.append(author != null ? SVNUtil.formatString(author, 10, false) : "         -");
            result.append(' ');
            if (date != null) {
                result.append(TimeUtil.toHumanDate(TimeUtil.formatDate(date)));
            } else {
                result.append("                                           -");
            }
            result.append(' ');
        } else {
            result.append(Long.toString(revision));
            result.append(author != null ? SVNUtil.formatString(author, 10, false) : "         -");
            result.append(' ');
        }
        result.append(line);
        myPrintStream.println(result.toString());


    }
}
