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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.cli.SVNCommandUtil;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNXMLUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext.ConflictInfo;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusClient;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNStatusCommand extends SVNXMLCommand implements ISVNStatusHandler {

    private SVNStatusPrinter myStatusPrinter;
    private Map myStatusCache;
    
    private int textConflicts;
    private int propConflicts;
    private int treeConflicts;

    public SVNStatusCommand() {
        super("status", new String[] {"stat", "st"});
    }

    protected Collection createSupportedOptions() {
        Collection options = new ArrayList();

        options.add(SVNOption.UPDATE);
        options.add(SVNOption.VERBOSE);
        options.add(SVNOption.NON_RECURSIVE);
        options.add(SVNOption.DEPTH);
        options.add(SVNOption.QUIET);
        options.add(SVNOption.NO_IGNORE);
        options.add(SVNOption.INCREMENTAL);
        options.add(SVNOption.XML);
        options.add(SVNOption.IGNORE_EXTERNALS);
        options.add(SVNOption.CHANGELIST);
        return options;
    }

    public void run() throws SVNException {
        Collection targets = new ArrayList();
        targets = getSVNEnvironment().combineTargets(targets, true);
        if (targets.isEmpty()) {
            targets.add("");
        }
        myStatusPrinter = new SVNStatusPrinter(getSVNEnvironment());
        SVNStatusClient client = getSVNEnvironment().getClientManager().getStatusClient();
        if (!getSVNEnvironment().isXML()) {
            client.setEventHandler(new SVNNotifyPrinter(getSVNEnvironment()));
        }
        if (getSVNEnvironment().isXML()) {
            if (!getSVNEnvironment().isIncremental()) {
                printXMLHeader("status");
            }
        } else if (getSVNEnvironment().isIncremental()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "'incremental' option only valid in XML mode");
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }

        Collection changeLists = getSVNEnvironment().getChangelistsCollection();
        for (Iterator ts = targets.iterator(); ts.hasNext();) {
            String target = (String) ts.next();
            SVNPath commandTarget = new SVNPath(target);

            if (getSVNEnvironment().isXML()) {
                StringBuffer xmlBuffer = openXMLTag("target", SVNXMLUtil.XML_STYLE_NORMAL, "path", SVNCommandUtil.getLocalPath(target), null);
                getSVNEnvironment().getOut().print(xmlBuffer);
            }

            try {
                long rev = client.doStatus(commandTarget.getFile(), SVNRevision.HEAD,
                        getSVNEnvironment().getDepth(), getSVNEnvironment().isUpdate(),
                        getSVNEnvironment().isVerbose(), getSVNEnvironment().isNoIgnore(),
                        false, this, changeLists);

                if (getSVNEnvironment().isXML()) {
                    StringBuffer xmlBuffer = new StringBuffer();
                    if (rev >= 0) {
                        xmlBuffer = openXMLTag("against", SVNXMLUtil.XML_STYLE_SELF_CLOSING, "revision", Long.toString(rev), xmlBuffer);
                    }
                    xmlBuffer = closeXMLTag("target", xmlBuffer);
                    getSVNEnvironment().getOut().print(xmlBuffer);
                }
            } catch (SVNException e) {
                getSVNEnvironment().handleWarning(e.getErrorMessage(), new SVNErrorCode[] {SVNErrorCode.WC_NOT_DIRECTORY},
                        getSVNEnvironment().isQuiet());
            }
        }
        if (myStatusCache != null) {
            for (Iterator changelists = myStatusCache.keySet().iterator(); changelists.hasNext();) {
                String changelist = (String) changelists.next();
                Map statuses = (Map) myStatusCache.get(changelist);
                getSVNEnvironment().getOut().println("\n--- Changelist '" + changelist + "':");
                for (Iterator paths = statuses.keySet().iterator(); paths.hasNext();) {
                    String path = (String) paths.next();
                    SVNStatus status = (SVNStatus) statuses.get(path);
                    myStatusPrinter.printStatus(path, status,
                            getSVNEnvironment().isVerbose() || getSVNEnvironment().isUpdate(),
                            getSVNEnvironment().isVerbose(), getSVNEnvironment().isQuiet(), getSVNEnvironment().isUpdate());
                }
            }
        }
        if (getSVNEnvironment().isXML() && !getSVNEnvironment().isIncremental()) {
            printXMLFooter("status");
        }
        if (! getSVNEnvironment().isQuiet() && ! getSVNEnvironment().isXML()) {
            printConflictStats();
        }

    }

    public void handleStatus(SVNStatus status) throws SVNException {
        countConflicts(status);
        
        String path = getSVNEnvironment().getRelativePath(status.getFile());
        path = SVNCommandUtil.getLocalPath(path);
        if (status != null && status.getChangelistName() != null) {
            if (myStatusCache == null) {
                myStatusCache = new TreeMap();
            }
            if (!myStatusCache.containsKey(status.getChangelistName())) {
                myStatusCache.put(status.getChangelistName(), new LinkedHashMap());
            }
            ((Map) myStatusCache.get(status.getChangelistName())).put(path, status);
            return;
        }
        if (getSVNEnvironment().isXML()) {
            if (SVNStatusPrinter.combineStatus(status) == SVNStatusType.STATUS_NONE && status.getRemoteContentsStatus() == SVNStatusType.STATUS_NONE) {
                return;
            }
            StringBuffer xmlBuffer = printXMLStatus(status, path);
            getSVNEnvironment().getOut().print(xmlBuffer);
        } else {
            myStatusPrinter.printStatus(path, status,
                getSVNEnvironment().isVerbose() || getSVNEnvironment().isUpdate(),
                getSVNEnvironment().isVerbose(), getSVNEnvironment().isQuiet(), getSVNEnvironment().isUpdate());
        }
    }

    private void countConflicts(SVNStatus status) throws SVNException {
        if (status.isConflicted()) {
            if (status.getPropRejectFile() != null) {
                propConflicts++;
            } 
            if (status.getConflictWrkFile() != null || status.getConflictOldFile() != null || status.getConflictNewFile() != null) {
                textConflicts++;
            }
            if (status.getTreeConflict() != null) {
                treeConflicts++;
            }
        }
    }

    protected StringBuffer printXMLStatus(SVNStatus status, String path) {
        StringBuffer xmlBuffer = openXMLTag("entry", SVNXMLUtil.XML_STYLE_NORMAL, "path", path, null);
        Map xmlMap = new LinkedHashMap();
        xmlMap.put("props", status.getNodeStatus() != SVNStatusType.STATUS_DELETED ?
                status.getPropertiesStatus().toString() : SVNStatusType.STATUS_NONE.toString());
        xmlMap.put("item", status.getCombinedNodeAndContentsStatus().toString());
        if (status.isLocked()) {
            xmlMap.put("wc-locked", "true");
        }
        if (status.isCopied()) {
            xmlMap.put("copied", "true");
        }
        if (status.isSwitched()) {
            xmlMap.put("switched", "true");
        }
        if (status.isFileExternal()) {
            xmlMap.put("file-external", "true");
        }
        if (status.isVersioned() && !status.isCopied()) {
            xmlMap.put("revision", status.getRevision().toString());
        }
        if (status.getTreeConflict() != null) {
            xmlMap.put("tree-conflicted", "true");
        }
        xmlBuffer = openXMLTag("wc-status", SVNXMLUtil.XML_STYLE_NORMAL, xmlMap, xmlBuffer);
        if (status.isVersioned() && status.getCommittedRevision().isValid()) {
            xmlBuffer = openXMLTag("commit", SVNXMLUtil.XML_STYLE_NORMAL, "revision", status.getCommittedRevision().toString(), xmlBuffer);
            xmlBuffer = openCDataTag("author", status.getAuthor(), xmlBuffer);
            if (status.getCommittedDate() != null) {
                xmlBuffer = openCDataTag("date", SVNDate.formatDate(status.getCommittedDate()), xmlBuffer);
            }
            xmlBuffer = closeXMLTag("commit", xmlBuffer);
        }
        if (status.isVersioned() && status.getLocalLock() != null) {
            xmlBuffer = openXMLTag("lock", SVNXMLUtil.XML_STYLE_NORMAL, null, xmlBuffer);
            xmlBuffer = openCDataTag("token", status.getLocalLock().getID(), xmlBuffer);
            xmlBuffer = openCDataTag("owner", status.getLocalLock().getOwner(), xmlBuffer);
            xmlBuffer = openCDataTag("comment", status.getLocalLock().getComment(), xmlBuffer);
            xmlBuffer = openCDataTag("created", SVNDate.formatDate(status.getLocalLock().getCreationDate()), xmlBuffer);
            xmlBuffer = closeXMLTag("lock", xmlBuffer);
        }
        xmlBuffer = closeXMLTag("wc-status", xmlBuffer);
        if (status.getRemoteNodeStatus() != SVNStatusType.STATUS_NONE || status.getRemotePropertiesStatus() != SVNStatusType.STATUS_NONE ||
                status.getRemoteLock() != null) {
            xmlMap.put("props", status.getRemotePropertiesStatus().toString());
            xmlMap.put("item", status.getCombinedRemoteNodeAndContentsStatus().toString());
            xmlBuffer = openXMLTag("repos-status", SVNXMLUtil.XML_STYLE_NORMAL, xmlMap, xmlBuffer);
            if (status.getRemoteLock() != null) {
                xmlBuffer = openXMLTag("lock", SVNXMLUtil.XML_STYLE_NORMAL, null, xmlBuffer);
                xmlBuffer = openCDataTag("token", status.getRemoteLock().getID(), xmlBuffer);
                xmlBuffer = openCDataTag("owner", status.getRemoteLock().getOwner(), xmlBuffer);
                xmlBuffer = openCDataTag("comment", status.getRemoteLock().getComment(), xmlBuffer);
                xmlBuffer = openCDataTag("created", SVNDate.formatDate(status.getRemoteLock().getCreationDate()), xmlBuffer);
                if (status.getRemoteLock().getExpirationDate() != null) {
                    xmlBuffer = openCDataTag("expires", SVNDate.formatDate(status.getRemoteLock().getExpirationDate()), xmlBuffer);
                }
                xmlBuffer = closeXMLTag("lock", xmlBuffer);
            }
            xmlBuffer = closeXMLTag("repos-status", xmlBuffer);
        }
        xmlBuffer = closeXMLTag("entry", xmlBuffer);
        return xmlBuffer;
    }
    
    private void printConflictStats()
    {
      if (textConflicts > 0 || propConflicts > 0 ||
          treeConflicts > 0)
      getEnvironment().getOut().println("Summary of conflicts:");

      if (textConflicts > 0)
          getEnvironment().getOut().println(
          "  Text conflicts: " + textConflicts);

      if (propConflicts > 0)
          getEnvironment().getOut().println(
          "  Property conflicts: " + propConflicts);

      if (treeConflicts > 0)
          getEnvironment().getOut().println(
          "  Tree conflicts: " + treeConflicts);

    }

    
}