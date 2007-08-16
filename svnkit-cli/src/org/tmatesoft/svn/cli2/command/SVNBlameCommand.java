/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.cli2.command;

import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.cli2.SVNCommandTarget;
import org.tmatesoft.svn.cli2.SVNCommandUtil;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNFormatUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.ISVNAnnotateHandler;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNBlameCommand extends SVNXMLCommand implements ISVNAnnotateHandler {

    private StringBuffer myBuffer;

    public SVNBlameCommand() {
        super("blame", new String[] {"praise", "annotate", "ann"});
    }

    public boolean acceptsRevisionRange() {
        return true;
    }

    protected Collection createSupportedOptions() {
        Collection options = new LinkedList();
        options.add(SVNOption.REVISION);
        options.add(SVNOption.VERBOSE);
        options.add(SVNOption.INCREMENTAL);
        options.add(SVNOption.XML);
        options.add(SVNOption.EXTENSIONS);
        options.add(SVNOption.FORCE);
        options = SVNOption.addAuthOptions(options);
        options.add(SVNOption.CONFIG_DIR);
        return options;
    }

    public void run() throws SVNException {
        List targets = getSVNEnvironment().combineTargets(null);
        if (targets.isEmpty()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS));
        }
        SVNRevision start = getSVNEnvironment().getStartRevision();
        SVNRevision end = getSVNEnvironment().getEndRevision();
        if (end == SVNRevision.UNDEFINED) {
            if (start != SVNRevision.UNDEFINED) {
                end = start;
                start = SVNRevision.create(1);
            }
        }
        if (start == SVNRevision.UNDEFINED) {
            start = SVNRevision.create(1);
        }
        if (getSVNEnvironment().isXML()) {
            if (getSVNEnvironment().isVerbose()) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "'verbose' option invalid in XML mode"));
            }
            if (!getSVNEnvironment().isIncremental()) {
                printXMLHeader("blame");
            }
        } else if (getSVNEnvironment().isIncremental()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "'incremental' option only valid in XML mode"));
        }
        
        myBuffer = new StringBuffer();
        SVNLogClient client = getSVNEnvironment().getClientManager().getLogClient();
        client.setDiffOptions(getSVNEnvironment().getDiffOptions());
        for (Iterator ts = targets.iterator(); ts.hasNext();) {
            String targetName = (String) ts.next();
            SVNCommandTarget target = new SVNCommandTarget(targetName, true);
            SVNRevision endRev = end;
            if (endRev == SVNRevision.UNDEFINED) {
                if (target.getPegRevision() != SVNRevision.UNDEFINED) {
                    endRev = target.getPegRevision();
                } else if (target.isURL()) {
                    endRev = SVNRevision.HEAD;
                } else {
                    endRev = SVNRevision.BASE;
                }
            }
            if (getSVNEnvironment().isXML()) {
                myBuffer = openXMLTag("target", XML_STYLE_NORMAL, "path", SVNCommandUtil.getLocalPath(target.getTarget()), myBuffer);
            }
            try {
                if (target.isFile()) {
                    client.doAnnotate(target.getFile(), target.getPegRevision(), start, endRev, getSVNEnvironment().isForce(), this);
                } else {
                    client.doAnnotate(target.getURL(), target.getPegRevision(), start, endRev, getSVNEnvironment().isForce(), this, null);
                }
                if (getSVNEnvironment().isXML()) {
                    myBuffer = closeXMLTag("target", myBuffer);
                    getSVNEnvironment().getOut().print(myBuffer);
                }
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode() == SVNErrorCode.CLIENT_IS_BINARY_FILE) {
                    getSVNEnvironment().getErr().println("Skipping binary file: '" + SVNCommandUtil.getLocalPath(targetName) + "'");
                } else {
                    throw e;
                }
            }
            myBuffer = myBuffer.delete(0, myBuffer.length());
        }
        if (getSVNEnvironment().isXML() && !getSVNEnvironment().isIncremental()) {
            printXMLFooter("blame");
        }
    }
    
    private long myCurrentLineNumber = 0;

    public void handleLine(Date date, long revision, String author, String line) throws SVNException {
        myCurrentLineNumber++;
        if (getSVNEnvironment().isXML()) {
            myBuffer = openXMLTag("entry", XML_STYLE_NORMAL, "line-number", Long.toString(myCurrentLineNumber), myBuffer);
            if (revision >= 0) {
                myBuffer = openXMLTag("commit", XML_STYLE_NORMAL, "revision", Long.toString(revision), myBuffer);
                myBuffer = openCDataTag("author", author, myBuffer);
                myBuffer = openCDataTag("date", ((SVNDate) date).format(), myBuffer);
                myBuffer = closeXMLTag("commit", myBuffer);
            }
            myBuffer = closeXMLTag("entry", myBuffer);
        } else {
            String revStr = revision >= 0 ? SVNCommandUtil.formatString(Long.toString(revision), 6, false) : "     -";
            String authorStr = author != null ? SVNCommandUtil.formatString(author, 10, false) : "         -";
            if (getSVNEnvironment().isVerbose()) {
                String dateStr = "                                           -"; 
                if (date != null) {
                    dateStr = SVNFormatUtil.formatHumanDate(date, getSVNEnvironment().getClientManager().getOptions());
                }
                getSVNEnvironment().getOut().println(revStr + " " + authorStr + " " + dateStr + " " + line);
            } else {
                getSVNEnvironment().getOut().println(revStr + " " + authorStr + " " + line);
            }
        }
    }
}
