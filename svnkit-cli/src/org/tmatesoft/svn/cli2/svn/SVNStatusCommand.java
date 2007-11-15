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
package org.tmatesoft.svn.cli2.svn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.cli2.SVNCommandUtil;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNChangelistClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusClient;
import org.tmatesoft.svn.core.wc.SVNStatusType;

/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNStatusCommand extends SVNXMLCommand implements ISVNStatusHandler {

    private SVNStatusPrinter myStatusPrinter;
    private Map myStatusCache;

    public SVNStatusCommand() {
        super("status", new String[] {"stat", "st"});
    }
    
    protected Collection createSupportedOptions() {
        Collection options = new ArrayList();

        options.add(SVNOption.UPDATE);
        options.add(SVNOption.VERBOSE);
        options.add(SVNOption.NON_RECURSIVE);
        options.add(SVNOption.QUIET);
        options.add(SVNOption.DEPTH);
        options.add(SVNOption.NO_IGNORE);
        options.add(SVNOption.INCREMENTAL);
        options.add(SVNOption.XML);

        options = SVNOption.addAuthOptions(options);

        options.add(SVNOption.CONFIG_DIR);
        options.add(SVNOption.IGNORE_EXTERNALS);
        options.add(SVNOption.CHANGELIST);
        return options;
    }

    public void run() throws SVNException {
        Collection targets = new ArrayList(); 
        if (getSVNEnvironment().getChangelist() != null) {
            SVNPath target = new SVNPath("");
            SVNChangelistClient changelistClient = getSVNEnvironment().getClientManager().getChangelistClient();
            changelistClient.getChangelist(target.getFile(), getSVNEnvironment().getChangelist(), targets);
            if (targets.isEmpty()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN_CHANGELIST, 
                        "Unknown changelist ''{0}''", getSVNEnvironment().getChangelist());
                SVNErrorManager.error(err);
            }
        }
        targets = getSVNEnvironment().combineTargets(targets);
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
            SVNErrorManager.error(err);
        }
        for (Iterator ts = targets.iterator(); ts.hasNext();) {
            String target = (String) ts.next();
            SVNPath commandTarget = new SVNPath(target);

            if (getSVNEnvironment().isXML()) {
                StringBuffer xmlBuffer = openXMLTag("target", XML_STYLE_NORMAL, "path", SVNCommandUtil.getLocalPath(target), null);
                getSVNEnvironment().getOut().print(xmlBuffer);
            }
            
            try {
                long rev = client.doStatus(commandTarget.getFile(), SVNRevision.HEAD,
                        getSVNEnvironment().getDepth(), getSVNEnvironment().isUpdate(),
                        getSVNEnvironment().isVerbose(), getSVNEnvironment().isNoIgnore(),
                        false, this);

                if (getSVNEnvironment().isXML()) {
                    StringBuffer xmlBuffer = new StringBuffer();
                    if (rev >= 0) {
                        xmlBuffer = openXMLTag("against", XML_STYLE_SELF_CLOSING, "revision", Long.toString(rev), xmlBuffer);
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
    }

    public void handleStatus(SVNStatus status) throws SVNException {
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
            if (status.getContentsStatus() == SVNStatusType.STATUS_NONE && status.getRemoteContentsStatus() == SVNStatusType.STATUS_NONE) {
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

    protected StringBuffer printXMLStatus(SVNStatus status, String path) {
        StringBuffer xmlBuffer = openXMLTag("entry", XML_STYLE_NORMAL, "path", path, null);
        Map xmlMap = new LinkedHashMap();
        xmlMap.put("props", status.getPropertiesStatus().toString());
        xmlMap.put("item", status.getContentsStatus().toString());
        if (status.isLocked()) {
            xmlMap.put("wc-locked", "true");
        }
        if (status.isCopied()) {
            xmlMap.put("copied", "true");
        }
        if (status.isSwitched()) {
            xmlMap.put("switched", "true");
        }
        if (status.getEntry() != null && !status.isCopied()) {
            xmlMap.put("revision", status.getRevision().toString());
        }
        xmlBuffer = openXMLTag("wc-status", XML_STYLE_NORMAL, xmlMap, xmlBuffer);
        if (status.getEntry() != null && status.getCommittedRevision().isValid()) {
            xmlBuffer = openXMLTag("commit", XML_STYLE_NORMAL, "revision", status.getCommittedRevision().toString(), xmlBuffer);
            xmlBuffer = openCDataTag("author", status.getAuthor(), xmlBuffer);
            if (status.getCommittedDate() != null) {
                xmlBuffer = openCDataTag("date", ((SVNDate) status.getCommittedDate()).format(), xmlBuffer);
            }
            xmlBuffer = closeXMLTag("commit", xmlBuffer);
        }
        if (status.getEntry() != null && status.getLocalLock() != null) {
            xmlBuffer = openXMLTag("lock", XML_STYLE_NORMAL, null, xmlBuffer);
            xmlBuffer = openCDataTag("token", status.getLocalLock().getID(), xmlBuffer);
            xmlBuffer = openCDataTag("owner", status.getLocalLock().getOwner(), xmlBuffer);
            xmlBuffer = openCDataTag("comment", status.getLocalLock().getComment(), xmlBuffer);
            xmlBuffer = openCDataTag("created", ((SVNDate) status.getLocalLock().getCreationDate()).format(), xmlBuffer);
            xmlBuffer = closeXMLTag("lock", xmlBuffer);
        }
        xmlBuffer = closeXMLTag("wc-status", xmlBuffer);
        if (status.getRemoteContentsStatus() != SVNStatusType.STATUS_NONE || status.getRemotePropertiesStatus() != SVNStatusType.STATUS_NONE ||
                status.getRemoteLock() != null) {
            xmlMap.put("props", status.getRemotePropertiesStatus().toString());
            xmlMap.put("item", status.getRemoteContentsStatus().toString());
            xmlBuffer = openXMLTag("repos-status", XML_STYLE_NORMAL, xmlMap, xmlBuffer);
            if (status.getRemoteLock() != null) {
                xmlBuffer = openXMLTag("lock", XML_STYLE_NORMAL, null, xmlBuffer);
                xmlBuffer = openCDataTag("token", status.getRemoteLock().getID(), xmlBuffer);
                xmlBuffer = openCDataTag("owner", status.getRemoteLock().getOwner(), xmlBuffer);
                xmlBuffer = openCDataTag("comment", status.getRemoteLock().getComment(), xmlBuffer);
                xmlBuffer = openCDataTag("created", ((SVNDate) status.getRemoteLock().getCreationDate()).format(), xmlBuffer);
                if (status.getRemoteLock().getExpirationDate() != null) {
                    xmlBuffer = openCDataTag("expires", ((SVNDate) status.getRemoteLock().getExpirationDate()).format(), xmlBuffer);
                }
                xmlBuffer = closeXMLTag("lock", xmlBuffer);
            }
            xmlBuffer = closeXMLTag("repos-status", xmlBuffer);
        }
        xmlBuffer = closeXMLTag("entry", xmlBuffer);
        return xmlBuffer;
    }
}