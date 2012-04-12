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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.PatternSyntaxException;

import org.tmatesoft.svn.cli.SVNCommandUtil;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNXMLUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
import org.tmatesoft.svn.core.wc.ISVNDiffGenerator;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNRevisionRange;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNLogCommand extends SVNXMLCommand implements ISVNLogEntryHandler {

    private static final String SEPARATOR = "------------------------------------------------------------------------\n";

    private LinkedList myMergeStack;
    private String myAuthorOfInterest;
    private String myLogRegularExpression;
    private SVNPath myTarget;
    private SVNDepth myDepth;
    
    public SVNLogCommand() {
        super("log", null);
    }
    
    public boolean acceptsRevisionRange() {
        return true;
    }

    protected Collection createSupportedOptions() {
        Collection options = new LinkedList();
        options.add(SVNOption.REVISION);
        options.add(SVNOption.DIFF);
        options.add(SVNOption.QUIET);
        options.add(SVNOption.VERBOSE);
        options.add(SVNOption.USE_MERGE_HISTORY);
        options.add(SVNOption.CHANGE);
        options.add(SVNOption.TARGETS);
        options.add(SVNOption.STOP_ON_COPY);
        options.add(SVNOption.INCREMENTAL);
        options.add(SVNOption.XML);
        options.add(SVNOption.LIMIT);
        options.add(SVNOption.WITH_ALL_REVPROPS);
        options.add(SVNOption.WITH_NO_REVPROPS);
        options.add(SVNOption.WITH_REVPROP);
        options.add(SVNOption.AUTHOR_OF_INTEREST);
        options.add(SVNOption.REGULAR_EXPRESSION);
        return options;
    }

    public void run() throws SVNException {
        if (!getSVNEnvironment().isXML()) {
            if (getSVNEnvironment().isAllRevisionProperties()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                        "'with-all-revprops' option only valid in XML mode");
                SVNErrorManager.error(err, SVNLogType.CLIENT);
            }
            if (getSVNEnvironment().getRevisionProperties() != null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                        "'with-revprop' option only valid in XML mode");
                SVNErrorManager.error(err, SVNLogType.CLIENT);
            }
        } else {
            if (getSVNEnvironment().isShowDiff()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR,
                        "'diff' option is not supported in XML mode");
                SVNErrorManager.error(err, SVNLogType.CLIENT);
            }
        }

        if (getSVNEnvironment().isQuiet() && getSVNEnvironment().isShowDiff()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR,
                    "'quiet' and 'diff' options are mutually exclusive");
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }

        if (getSVNEnvironment().getDiffCommand() != null && !getSVNEnvironment().isShowDiff()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR,
                    "'diff-cmd' option requires 'diff' option");
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }

        if (getSVNEnvironment().getExtensions() != null && !getSVNEnvironment().isShowDiff()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR,
                    "'extensions' option requires 'diff' option");
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }

        if (getSVNEnvironment().getDepth() != SVNDepth.UNKNOWN && !getSVNEnvironment().isShowDiff()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR,
                    "'depth' option requires 'diff' option");
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }

        List targets = new ArrayList();
        if (getSVNEnvironment().getTargets() != null) {
            targets.addAll(getSVNEnvironment().getTargets());
        }
        targets = getSVNEnvironment().combineTargets(targets, true);
        if (targets.isEmpty()) {
            targets.add("");
        }
        SVNPath target = new SVNPath((String) targets.get(0), true);
        
        SVNRevision start = getSVNEnvironment().getStartRevision();
        SVNRevision end = getSVNEnvironment().getEndRevision();
        List revisionRanges = getSVNEnvironment().getRevisionRanges();
        List editedRevisionRangesList = revisionRanges;
        if (getSVNEnvironment().isChangeOptionUsed()) {
            editedRevisionRangesList = new LinkedList();
            
            if (getSVNEnvironment().isRevisionOptionUsed() && revisionRanges.size() > 1) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "-c and -r are mutually exclusive");
                SVNErrorManager.error(err, SVNLogType.CLIENT);
            }
            
            for (Iterator revisionsIter = revisionRanges.iterator(); revisionsIter.hasNext();) {
                SVNRevisionRange revRange = (SVNRevisionRange) revisionsIter.next();
                SVNRevision startRev = revRange.getStartRevision();
                SVNRevision endRev = revRange.getEndRevision();
                if (startRev.getNumber() < endRev.getNumber() && startRev.getNumber() >= 0 && endRev.getNumber() >= 0) {
                    revRange = new SVNRevisionRange(SVNRevision.create(startRev.getNumber() + 1), endRev);
                } else {
                    revRange = new SVNRevisionRange(startRev, SVNRevision.create(endRev.getNumber() + 1));
                }
                editedRevisionRangesList.add(revRange);
            }

            if (start.getNumber() < end.getNumber()) {
                start = end;
            } else {
                end = start;
            }
        }
        
        if (start != SVNRevision.UNDEFINED && end == SVNRevision.UNDEFINED) {
            end = start;
        } else if (start == SVNRevision.UNDEFINED) {
            if (target.getPegRevision() == SVNRevision.UNDEFINED) {
                start = target.isURL() ? SVNRevision.HEAD : SVNRevision.BASE;
            } else {
                start = target.getPegRevision();
            }
            if (end == SVNRevision.UNDEFINED) {
                end = SVNRevision.create(0);
            }
        }
        
        if (target.isURL()) {
            for(int i = 1; i < targets.size(); i++) {
                if (SVNCommandUtil.isURL((String) targets.get(i))) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, 
                        "Only relative paths can be specified after a URL");
                    SVNErrorManager.error(err, SVNLogType.CLIENT);
                }
            }
        } else {
            if (targets.size() > 1) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, 
                        "When specifying working copy paths, only one target may be given");
                SVNErrorManager.error(err, SVNLogType.CLIENT);
            }
        }

        myAuthorOfInterest = getSVNEnvironment().getAuthorOfInterest();
        myLogRegularExpression = getSVNEnvironment().getRegularExpression();
        myDepth = getSVNEnvironment().getDepth() == SVNDepth.UNKNOWN ? SVNDepth.INFINITY : getSVNEnvironment().getDepth();
        myTarget = new SVNPath(target.getTarget(), target.getPegRevision() == SVNRevision.UNDEFINED ?
                (target.isURL() ? SVNRevision.HEAD : SVNRevision.WORKING) :
                target.getPegRevision());


        SVNLogClient client = getSVNEnvironment().getClientManager().getLogClient();
        if (!getSVNEnvironment().isQuiet()) {
            client.setEventHandler(new SVNNotifyPrinter(getSVNEnvironment()));
        }
        
        String[] revProps = null;
        if (getSVNEnvironment().isXML()) {
            if (!getSVNEnvironment().isIncremental()) {
                printXMLHeader("log");    
            }
            
            if (!getSVNEnvironment().isAllRevisionProperties() && 
                    getSVNEnvironment().getRevisionProperties() != null && 
                    !getSVNEnvironment().getRevisionProperties().isEmpty()) {
                SVNProperties revPropNames = getSVNEnvironment().getRevisionProperties();
                revProps = new String[revPropNames.size()];
                int i = 0;
                for (Iterator propNames = revPropNames.nameSet().iterator(); propNames.hasNext();) {
                    String propName = (String) propNames.next();
                    String propVal = revPropNames.getStringValue(propName);
                    if (propVal.length() > 0) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                                "cannot assign with 'with-revprop' option (drop the '=')");
                        SVNErrorManager.error(err, SVNLogType.CLIENT);
                    }
                    revProps[i++] = propName;
                }
            } else if (!getSVNEnvironment().isAllRevisionProperties()) {
                if (!getSVNEnvironment().isQuiet()) {
                    revProps = new String[3];
                    revProps[0] = SVNRevisionProperty.AUTHOR;
                    revProps[1] = SVNRevisionProperty.DATE;
                    revProps[2] = SVNRevisionProperty.LOG;
                } else {
                    revProps = new String[2];
                    revProps[0] = SVNRevisionProperty.AUTHOR;
                    revProps[1] = SVNRevisionProperty.DATE;
                }
            }
        } else {
            if (!getSVNEnvironment().isQuiet()) {
                revProps = new String[3];
                revProps[0] = SVNRevisionProperty.AUTHOR;
                revProps[1] = SVNRevisionProperty.DATE;
                revProps[2] = SVNRevisionProperty.LOG;
            } else {
                revProps = new String[2];
                revProps[0] = SVNRevisionProperty.AUTHOR;
                revProps[1] = SVNRevisionProperty.DATE;
            }
        }

        if (target.isFile()) {
            client.doLog(new File[] {target.getFile()}, editedRevisionRangesList, target.getPegRevision(), 
                    getSVNEnvironment().isStopOnCopy(), getSVNEnvironment().isVerbose(), 
                    getSVNEnvironment().isUseMergeHistory(), getSVNEnvironment().getLimit(), 
                    revProps, this);
        } else {
            targets.remove(0);
            String[] paths = (String[]) targets.toArray(new String[targets.size()]);
            client.doLog(target.getURL(), paths, target.getPegRevision(), editedRevisionRangesList, 
                    getSVNEnvironment().isStopOnCopy(), 
                    getSVNEnvironment().isVerbose(),
                    getSVNEnvironment().isUseMergeHistory(),
                    getSVNEnvironment().getLimit(), revProps, this);

        }

        if (getSVNEnvironment().isXML() && !getSVNEnvironment().isIncremental()) {
            printXMLFooter("log");
        } else if (!getSVNEnvironment().isIncremental()) {
            getSVNEnvironment().getOut().print(SEPARATOR);
        }
    }

    public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
        if (!getSVNEnvironment().isXML()) {
            printLogEntry(logEntry);
        } else {
            printLogEntryXML(logEntry);
        }
    }
    
    protected void printLogEntry(SVNLogEntry logEntry) throws SVNException {
        if (logEntry == null) {
            return;
        }

        if (myAuthorOfInterest != null && !"".equals(myAuthorOfInterest) && !myAuthorOfInterest.equals(logEntry.getAuthor())) {
            return;
        }
        
        SVNProperties revisionProperties = logEntry.getRevisionProperties();
        String author = revisionProperties.getStringValue(SVNRevisionProperty.AUTHOR);
        String message = revisionProperties.getStringValue(SVNRevisionProperty.LOG);
        String dateValue = revisionProperties.getStringValue(SVNRevisionProperty.DATE);
        Date dateObject = dateValue == null ? null : SVNDate.parseDate(dateValue); 

        if (message == null && logEntry.getRevision() == 0) {
            return;
        }

        if (!SVNRevision.isValidRevisionNumber(logEntry.getRevision())) {
            if (!myMergeStack.isEmpty()) {
                myMergeStack.removeLast();
            }
            return;
        }

        if (myLogRegularExpression != null) {
            if (message == null) {
                return;
            }
            String[] result = null;
            try {
                 result = message.split(myLogRegularExpression);
            } catch (PatternSyntaxException psy) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "you specified an invalid regular expression: {0}", 
                        psy.getMessage());
                SVNErrorManager.error(err, SVNLogType.CLIENT);
            }
            if (result.length == 1 && message.equals(result[0]) && !message.equals(myLogRegularExpression)) {
                return;
            }
        }
        
        StringBuffer buffer = new StringBuffer();
        if (author == null) {
            author = "(no author)";
        }
            
        String date = dateObject == null ? "(no date)" : SVNDate.formatHumanDate(dateObject,
                getSVNEnvironment().getClientManager().getOptions());
        if (!getSVNEnvironment().isQuiet() && message == null) {
            message = "";
        }
        buffer.append(SEPARATOR);
        buffer.append("r" + Long.toString(logEntry.getRevision()) + " | " + author + " | " + date);
        if (message != null) {
            int count = SVNCommandUtil.getLinesCount(message);
            buffer.append(" | " + count + (count == 1 ? " line" : " lines"));
        }
        buffer.append("\n");
        if (getSVNEnvironment().isVerbose() && logEntry.getChangedPaths() != null) {
            Map sortedPaths = new TreeMap(logEntry.getChangedPaths());
            buffer.append("Changed paths:\n");
            for (Iterator paths = sortedPaths.keySet().iterator(); paths.hasNext();) {
                String path = (String) paths.next();
                SVNLogEntryPath lPath = (SVNLogEntryPath) sortedPaths.get(path);
                buffer.append("   " + lPath.getType() + " " + path);
                if (lPath.getCopyPath() != null) {
                    buffer.append(" (from " + lPath.getCopyPath() + ":" + lPath.getCopyRevision() + ")");
                }
                buffer.append("\n");
            }
        }
        
        if (myMergeStack != null && !myMergeStack.isEmpty()) {
            
            buffer.append(logEntry.isSubtractiveMerge() ? "Reverse merged via:" : "Merged via:");
            
            for (Iterator revs = myMergeStack.iterator(); revs.hasNext();) {
                long rev = ((Long) revs.next()).longValue();
                buffer.append(" r");
                buffer.append(rev);
                if (revs.hasNext()) {
                    buffer.append(',');
                } else {
                    buffer.append('\n');
                }
            }
        }
        
        if (message != null) {
            buffer.append("\n" + message + "\n");
        }

        if (logEntry.hasChildren()) {
            Long rev = new Long(logEntry.getRevision());
            if (myMergeStack == null) {
                myMergeStack = new LinkedList();
            }
            myMergeStack.addLast(rev);
        }
        getSVNEnvironment().getOut().print(buffer.toString());

        if (getSVNEnvironment().isShowDiff()) {

            getSVNEnvironment().getOut().println();

            SVNClientManager diffClientManager = getEnvironment().createClientManager();
            try {
                //TODO: parse diff options here
                SVNDiffClient client = diffClientManager.getDiffClient();
                client.setShowCopiesAsAdds(false);

                final ISVNDiffGenerator diffGenerator = SVNDiffCommand.createDiffGenerator(getSVNEnvironment());
                diffGenerator.setDiffDeleted(false);

                client.setDiffGenerator(diffGenerator);

                try {
                    doDiff(client, logEntry, myTarget, myDepth);
                } catch (SVNException e) {
                    if (e.getErrorMessage().getErrorCode() == SVNErrorCode.FS_NOT_FOUND) {
                        SVNPath parent = getParentPath(myTarget);
                        while (!myTarget.equals(parent)) {
                            try {
                                doDiff(client, logEntry, parent, myDepth);
                            } catch (SVNException e1) {
                                if (e1.getErrorMessage().getErrorCode() == SVNErrorCode.FS_NOT_FOUND) {
                                    parent = getParentPath(parent);
                                } else if (e1.getErrorMessage().getErrorCode() == SVNErrorCode.RA_ILLEGAL_URL
                                        || e1.getErrorMessage().getErrorCode() == SVNErrorCode.AUTHZ_UNREADABLE
                                        || e1.getErrorMessage().getErrorCode() == SVNErrorCode.RA_LOCAL_REPOS_OPEN_FAILED) {
                                    break;
                                } else {
                                    throw e1;
                                }
                            }
                            break;
                        }
                    } else {
                        throw e;
                    }
                }
            } finally {
                diffClientManager.dispose();
            }

            getSVNEnvironment().getOut().println();
        }
    }

    private SVNPath getParentPath(SVNPath target) {
        return new SVNPath(SVNPathUtil.removeTail(target.getTarget()), target.getPegRevision());
    }

    private void doDiff(SVNDiffClient client, SVNLogEntry logEntry, SVNPath target, SVNDepth depth) throws SVNException {
        if (target.isFile()) {
        client.doDiff(target.getFile(),
                target.getPegRevision(),
                SVNRevision.create(logEntry.getRevision() - 1),
                SVNRevision.create(logEntry.getRevision()),
                depth,
                true,
                getSVNEnvironment().getOut(),
                getSVNEnvironment().getChangelistsCollection());
        } else {
            client.doDiff(target.getURL(),
                    target.getPegRevision(),
                    SVNRevision.create(logEntry.getRevision() - 1),
                    SVNRevision.create(logEntry.getRevision()),
                    depth,
                    true,
                    getSVNEnvironment().getOut());
        }
    }

    protected void printLogEntryXML(SVNLogEntry logEntry) throws SVNException {
        if (logEntry == null) {
            return;
        }

        if (myAuthorOfInterest != null && !"".equals(myAuthorOfInterest) && !myAuthorOfInterest.equals(logEntry.getAuthor())) {
            return;
        }

        SVNProperties revProps = logEntry.getRevisionProperties();
        String author = revProps.getStringValue(SVNRevisionProperty.AUTHOR);
        String message = revProps.getStringValue(SVNRevisionProperty.LOG);
        String dateValue = revProps.getStringValue(SVNRevisionProperty.DATE);
        Date dateObject = dateValue == null ? null : SVNDate.parseDate(dateValue);
        
        if (logEntry.getRevision() == 0 && message == null) {
            return;
        }

        if (myLogRegularExpression != null) {
            if (message == null) {
                return;
            }

            String[] result = null;
            try {
                 result = message.split(myLogRegularExpression);
            } catch (PatternSyntaxException psy) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "you specified an invalid regular expression: {0}", 
                        psy.getMessage());
                SVNErrorManager.error(err, SVNLogType.CLIENT);
            }
            if (result.length == 1 && message.equals(result[0]) && !message.equals(myLogRegularExpression)) {
                return;
            }
        }

        if (author != null) {
            author = SVNEncodingUtil.fuzzyEscape(author);
        }
        if (message != null) {
            message = SVNEncodingUtil.fuzzyEscape(message);
        }
        
        StringBuffer buffer = new StringBuffer();
        if (!SVNRevision.isValidRevisionNumber(logEntry.getRevision())) {
            buffer = closeXMLTag("logentry", null);
            getSVNEnvironment().getOut().print(buffer.toString());
            if (!myMergeStack.isEmpty()) {
                myMergeStack.removeLast();
            }
            return;
        }
        
        buffer = openXMLTag("logentry", SVNXMLUtil.XML_STYLE_NORMAL, "revision", Long.toString(logEntry.getRevision()), buffer);
        buffer = openCDataTag("author", author, buffer);
        if (dateObject != null && dateObject.getTime() != 0) {
            String dateString = SVNEncodingUtil.fuzzyEscape(((SVNDate) dateObject).format());
            buffer = openCDataTag("date", dateString, buffer);
        }
        if (logEntry.getChangedPaths() != null && !logEntry.getChangedPaths().isEmpty()) {
            buffer = openXMLTag("paths", SVNXMLUtil.XML_STYLE_NORMAL, null, buffer);
            for (Iterator paths = logEntry.getChangedPaths().keySet().iterator(); paths.hasNext();) {
                String key = (String) paths.next();
                SVNLogEntryPath path = (SVNLogEntryPath) logEntry.getChangedPaths().get(key);
                Map attrs = new LinkedHashMap();
                attrs.put("action", path.getType() + "");
                if (path.getCopyPath() != null) {
                    attrs.put("copyfrom-path", path.getCopyPath());
                    attrs.put("copyfrom-rev", Long.toString(path.getCopyRevision()));
                }
                buffer = openXMLTag("path", SVNXMLUtil.XML_STYLE_PROTECT_CDATA, attrs, buffer);
                buffer.append(SVNEncodingUtil.xmlEncodeCDATA(path.getPath()));
                buffer = closeXMLTag("path", buffer);
            }
            buffer = closeXMLTag("paths", buffer);
        }
        
        if (message != null) {
            buffer = openCDataTag("msg", message, buffer);
        }
        
        if (revProps != null) {
            revProps.remove(SVNRevisionProperty.AUTHOR);
            revProps.remove(SVNRevisionProperty.DATE);
            revProps.remove(SVNRevisionProperty.LOG);
        }
        if (revProps != null && !revProps.isEmpty()) {
            buffer = openXMLTag("revprops", SVNXMLUtil.XML_STYLE_NORMAL, null, buffer);
            buffer = printXMLPropHash(buffer, revProps, false);
            buffer = closeXMLTag("revprops", buffer);
        }
        if (logEntry.hasChildren()) {
            if (myMergeStack == null) {
                myMergeStack = new LinkedList();
            }
            myMergeStack.addLast(new Long(logEntry.getRevision()));
        } else {
            buffer = closeXMLTag("logentry", buffer);
        }
        
        getSVNEnvironment().getOut().print(buffer.toString());
    }

}
