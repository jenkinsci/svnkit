/*
 * ====================================================================
 * Copyright (c) 2004-2012 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.cli.svn;

import java.io.File;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.cli.SVNCommandUtil;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNFormatUtil;
import org.tmatesoft.svn.core.internal.util.SVNXMLUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
import org.tmatesoft.svn.core.wc.ISVNAnnotateHandler;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
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
        options.add(SVNOption.USE_MERGE_HISTORY);
        options.add(SVNOption.INCREMENTAL);
        options.add(SVNOption.XML);
        options.add(SVNOption.EXTENSIONS);
        options.add(SVNOption.FORCE);
        return options;
    }

    public void run() throws SVNException {
        List targets = getSVNEnvironment().combineTargets(null, true);
        if (targets.isEmpty()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS), SVNLogType.CLIENT);
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
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "'verbose' option invalid in XML mode"), SVNLogType.CLIENT);
            }
            if (!getSVNEnvironment().isIncremental()) {
                printXMLHeader("blame");
            }
        } else if (getSVNEnvironment().isIncremental()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "'incremental' option only valid in XML mode"), SVNLogType.CLIENT);
        }
        
        myBuffer = new StringBuffer();
        SVNLogClient client = getSVNEnvironment().getClientManager().getLogClient();
        client.setDiffOptions(getSVNEnvironment().getDiffOptions());
        boolean hasMissingTargets = false;
        for (Iterator ts = targets.iterator(); ts.hasNext();) {
            String targetName = (String) ts.next();
            SVNPath target = new SVNPath(targetName, true);
            SVNRevision endRev = end;
            if (endRev == SVNRevision.UNDEFINED) {
                if (target.getPegRevision() != SVNRevision.UNDEFINED) {
                    endRev = target.getPegRevision();
                }
            }
            if (getSVNEnvironment().isXML()) {
                myBuffer = openXMLTag("target", SVNXMLUtil.XML_STYLE_NORMAL, "path", SVNCommandUtil.getLocalPath(target.getTarget()), myBuffer);
            }
            try {
                if (target.isFile()) {
                    client.doAnnotate(target.getFile(), target.getPegRevision(), 
                                      start, endRev, getSVNEnvironment().isForce(), 
                                      getSVNEnvironment().isUseMergeHistory(), 
                                      this, null);
                } else {
                    client.doAnnotate(target.getURL(), target.getPegRevision(), 
                                      start, endRev, getSVNEnvironment().isForce(), 
                                      getSVNEnvironment().isUseMergeHistory(), 
                                      this, null);
                }
                if (getSVNEnvironment().isXML()) {
                    myBuffer = closeXMLTag("target", myBuffer);
                    getSVNEnvironment().getOut().print(myBuffer);
                }
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode() == SVNErrorCode.CLIENT_IS_BINARY_FILE) {
                    getSVNEnvironment().getErr().println("Skipping binary file: '" + SVNCommandUtil.getLocalPath(targetName) + "'");
                } else {
                    getSVNEnvironment().handleWarning(e.getErrorMessage(), 
                            new SVNErrorCode[] {SVNErrorCode.WC_PATH_NOT_FOUND, SVNErrorCode.FS_NOT_FILE, SVNErrorCode.FS_NOT_FOUND}, 
                            getSVNEnvironment().isQuiet());
                    hasMissingTargets = true;
                }
            }
            myBuffer = myBuffer.delete(0, myBuffer.length());
        }
        if (getSVNEnvironment().isXML() && !getSVNEnvironment().isIncremental()) {
            printXMLFooter("blame");
        }
        if (hasMissingTargets) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "Could not perform blame on all targets because some " +
            		"targets don't exist");
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }
    }
    
    private int myCurrentLineNumber = 0;

    public void handleLine(Date date, long revision, String author, String line) throws SVNException {
        myCurrentLineNumber++;
        handleLine(date, revision, author, line, null, -1, null, null, myCurrentLineNumber);
    }

    public void handleLine(Date date, long revision, String author, String line, 
                           Date mergedDate, long mergedRevision, String mergedAuthor, 
                           String mergedPath, int lineNumber) throws SVNException {
        if (getSVNEnvironment().isXML()) {
            myBuffer = openXMLTag("entry", SVNXMLUtil.XML_STYLE_NORMAL, "line-number",
                                  Long.toString(lineNumber + 1), myBuffer);
            if (SVNRevision.isValidRevisionNumber(revision)) {
                myBuffer = openXMLTag("commit", SVNXMLUtil.XML_STYLE_NORMAL, "revision", Long.toString(revision), myBuffer);
                myBuffer = openCDataTag("author", author, myBuffer);
                myBuffer = openCDataTag("date", ((SVNDate) date).format(), myBuffer);
                myBuffer = closeXMLTag("commit", myBuffer);
            }
            
            if (getSVNEnvironment().isUseMergeHistory() && 
                SVNRevision.isValidRevisionNumber(mergedRevision)) {
                myBuffer = openXMLTag("merged", SVNXMLUtil.XML_STYLE_NORMAL, "path",
                                      mergedPath, myBuffer);
                myBuffer = openXMLTag("commit", SVNXMLUtil.XML_STYLE_NORMAL, "revision", 
                                      Long.toString(mergedRevision), myBuffer);
                myBuffer = openCDataTag("author", mergedAuthor, myBuffer);
                myBuffer = openCDataTag("date", ((SVNDate) mergedDate).format(), myBuffer);
                myBuffer = closeXMLTag("commit", myBuffer);
                myBuffer = closeXMLTag("merged", myBuffer);
            }
            myBuffer = closeXMLTag("entry", myBuffer);
        } else {
            String mergedStr = "";
            if (getSVNEnvironment().isUseMergeHistory()) {
                if (revision > mergedRevision) {
                    mergedStr = "G ";
                    date = mergedDate;
                    revision = mergedRevision;
                    author = mergedAuthor;
                } else {
                    mergedStr = "  ";
                }
            } 
           
            String revStr = revision >= 0 ? SVNFormatUtil.formatString(Long.toString(revision), 6, false) : "     -";
            String authorStr = author != null ? SVNFormatUtil.formatString(author, 10, false) : "         -";
            if (getSVNEnvironment().isVerbose()) {
                String dateStr = "                                           -"; 
                if (date != null) {
                    dateStr = SVNDate.formatHumanDate(date, getSVNEnvironment().getClientManager().getOptions());
                }
                getSVNEnvironment().getOut().print(mergedStr + revStr + " " + authorStr + " " + dateStr + " ");
                if (getSVNEnvironment().isUseMergeHistory() && mergedPath != null) {
                    String pathStr = SVNFormatUtil.formatString(mergedPath, 14, true);
                    getSVNEnvironment().getOut().print(pathStr + " ");
                }
                getSVNEnvironment().getOut().println(line);
            } else {
                getSVNEnvironment().getOut().println(mergedStr + revStr + " " + authorStr + " " + line);
            }
        }
    }

    public boolean handleRevision(Date date, long revision, String author, File contents) throws SVNException {
        return false;
    }

    public void handleEOF() {
    }
}
