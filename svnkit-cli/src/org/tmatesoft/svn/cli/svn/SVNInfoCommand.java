/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
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
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.cli.SVNCommandUtil;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNXMLUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
import org.tmatesoft.svn.core.wc.ISVNInfoHandler;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.2.0
 * @author  TMate Software Ltd.
 */
public class SVNInfoCommand extends SVNXMLCommand implements ISVNInfoHandler {

    public SVNInfoCommand() {
        super("info", null);
    }

    protected Collection createSupportedOptions() {
        Collection options = new LinkedList();
        options.add(SVNOption.REVISION);
        options.add(SVNOption.RECURSIVE);
        options.add(SVNOption.DEPTH);
        options.add(SVNOption.TARGETS);
        options.add(SVNOption.INCREMENTAL);
        options.add(SVNOption.XML);
        options.add(SVNOption.CHANGELIST);
        return options;
    }

    public void run() throws SVNException {
        List targets = new ArrayList(); 
        if (getSVNEnvironment().getTargets() != null) {
            targets.addAll(getSVNEnvironment().getTargets());
        }
        targets = getSVNEnvironment().combineTargets(targets, true);
        if (targets.isEmpty()) {
            targets.add("");
        }
        if (getSVNEnvironment().isXML()) {
            if (!getSVNEnvironment().isIncremental()) {
                printXMLHeader("info");
            }
        } else if (getSVNEnvironment().isIncremental()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "'incremental' option only valid in XML mode"), SVNLogType.CLIENT);
        }
        SVNDepth depth = getSVNEnvironment().getDepth();
        if (depth == SVNDepth.UNKNOWN) {
            depth = SVNDepth.EMPTY;
        }
        SVNWCClient client = getSVNEnvironment().getClientManager().getWCClient();
        for(int i = 0; i < targets.size(); i++) {
            String targetName = (String) targets.get(i);
            SVNPath target = new SVNPath(targetName, true);
            SVNRevision pegRevision = target.getPegRevision();
            if (target.isURL() && pegRevision == SVNRevision.UNDEFINED) {
                pegRevision = SVNRevision.HEAD;
            }
            try {
                if (target.isFile()) {
                    client.doInfo(target.getFile(), pegRevision, getSVNEnvironment().getStartRevision(), depth, 
                            getSVNEnvironment().getChangelistsCollection(), this);
                } else {
                    client.doInfo(target.getURL(), pegRevision, getSVNEnvironment().getStartRevision(), depth, this);
                }
            } catch (SVNException e) {
                SVNErrorMessage err = e.getErrorMessage();
                if (err.getErrorCode() == SVNErrorCode.UNVERSIONED_RESOURCE) {
                    getSVNEnvironment().getErr().print(SVNCommandUtil.getLocalPath(target.getTarget()) + ": (Not a versioned resource)\n\n");
                } else if (err.getErrorCode() == SVNErrorCode.RA_ILLEGAL_URL) {
                    getSVNEnvironment().getErr().print(target.getTarget() + ": (Not a valid URL)\n\n");
                } else {
                    throw e;
                }
            }
        }
        if (getSVNEnvironment().isXML() && !getSVNEnvironment().isIncremental()) {
            printXMLFooter("info");
        }
    }

    public void handleInfo(SVNInfo info) throws SVNException {
        if (!getSVNEnvironment().isXML()) {
            printInfo(info);
        } else {
            printInfoXML(info);
        }
    }
    
    protected void printInfo(SVNInfo info) {
        StringBuffer buffer = new StringBuffer();
        String path = null;
        if (info.getFile() != null) {
            path = getSVNEnvironment().getRelativePath(info.getFile());
            path = SVNCommandUtil.getLocalPath(path);
        } else {
            path = info.getPath();
        }
        buffer.append("Path: " + path + "\n");
        if (info.getKind() != SVNNodeKind.DIR) {
            buffer.append("Name: " + SVNPathUtil.tail(path.replace(File.separatorChar, '/')) + "\n");
        }
        buffer.append("URL: " + info.getURL() + "\n");
        if (info.getRepositoryRootURL() != null) {
            buffer.append("Repository Root: " + info.getRepositoryRootURL() + "\n");
        }
        if (info.getRepositoryUUID() != null) {
            buffer.append("Repository UUID: " + info.getRepositoryUUID() + "\n");
        }
        if (info.getRevision() != null && info.getRevision().isValid()) {
            buffer.append("Revision: " + info.getRevision() + "\n");
        }
        String kind = info.getKind() == SVNNodeKind.DIR ? "directory" : (info.getKind() != null ? info.getKind().toString() : "none");
        buffer.append("Node Kind: " + kind + "\n");
        if (!info.isRemote()) {
            if (info.getSchedule() == null) {
                buffer.append("Schedule: normal\n");
            } else {
                buffer.append("Schedule: " + info.getSchedule() + "\n");
            }
            if (info.getDepth() != null) {
                if (info.getDepth() != SVNDepth.UNKNOWN && info.getDepth() != SVNDepth.INFINITY) {
                    buffer.append("Depth: " + info.getDepth() + "\n");
                }
            }
            if (info.getCopyFromURL() != null) {
                buffer.append("Copied From URL: " + info.getCopyFromURL() + "\n");
            }
            if (info.getCopyFromRevision() != null && info.getCopyFromRevision().getNumber() >= 0) {
                buffer.append("Copied From Rev: " + info.getCopyFromRevision() + "\n");
            }
        }
        if (info.getAuthor() != null) {
            buffer.append("Last Changed Author: " + info.getAuthor() + "\n");
        }
        if (info.getCommittedRevision() != null && info.getCommittedRevision().getNumber() >= 0) {
            buffer.append("Last Changed Rev: " + info.getCommittedRevision() + "\n");
        }
        if (info.getCommittedDate() != null) {
            buffer.append("Last Changed Date: " + 
                    SVNDate.formatHumanDate(info.getCommittedDate(), getSVNEnvironment().getClientManager().getOptions()) + "\n");
        }
        if (!info.isRemote()) {
            if (info.getTextTime() != null) {
                buffer.append("Text Last Updated: " + 
                        SVNDate.formatHumanDate(info.getTextTime(), getSVNEnvironment().getClientManager().getOptions()) + "\n");
            }
            if (info.getPropTime() != null) {
                buffer.append("Properties Last Updated: " + 
                        SVNDate.formatHumanDate(info.getPropTime(), getSVNEnvironment().getClientManager().getOptions()) + "\n");
            }
            if (info.getChecksum() != null) {
                buffer.append("Checksum: " + info.getChecksum() + "\n");
            }
            if (info.getConflictOldFile() != null) {
                buffer.append("Conflict Previous Base File: " + info.getConflictOldFile().getName() + "\n");
            }
            if (info.getConflictWrkFile() != null) {
                buffer.append("Conflict Previous Working File: " + info.getConflictWrkFile().getName() + "\n");
            }
            if (info.getConflictNewFile() != null) {
                buffer.append("Conflict Current Base File: " + info.getConflictNewFile().getName() + "\n");
            }
            if (info.getPropConflictFile() != null) {
                buffer.append("Conflict Properties File: " + info.getPropConflictFile().getName() + "\n");
            }
        }
        if (info.getLock() != null) {
            SVNLock lock = info.getLock();
            if (lock.getID() != null) {
                buffer.append("Lock Token: " + lock.getID() + "\n");
            }
            if (lock.getOwner() != null) {
                buffer.append("Lock Owner: " + lock.getOwner() + "\n");
            }
            if (lock.getCreationDate() != null && lock.getCreationDate().getTime() != 0) {
                buffer.append("Lock Created: " + 
                        SVNDate.formatHumanDate(lock.getCreationDate(), getSVNEnvironment().getClientManager().getOptions()) + "\n");
            }
            if (lock.getExpirationDate() != null && lock.getExpirationDate().getTime() != 0) {
                buffer.append("Lock Expires: " + 
                        SVNDate.formatHumanDate(lock.getExpirationDate(), getSVNEnvironment().getClientManager().getOptions()) + "\n");
            }
            if (lock.getComment() != null) {
                buffer.append("Lock Comment "); 
                int lineCount = SVNCommandUtil.getLinesCount(lock.getComment());
                buffer.append(lineCount > 1 ? "(" + lineCount + " lines)" : "(1 line)");
                buffer.append(":\n");
                buffer.append(lock.getComment());
                buffer.append("\n");
            }
        }
        if (info.getChangelistName() != null) {
            buffer.append("Changelist: " + info.getChangelistName() + "\n");
        }
        buffer.append("\n");
        getSVNEnvironment().getOut().print(buffer.toString());
    }
    
    protected void printInfoXML(SVNInfo info) {
        StringBuffer buffer = new StringBuffer();
        String path = null;
        if (info.getFile() != null) {
            path = getSVNEnvironment().getRelativePath(info.getFile());
            path = SVNCommandUtil.getLocalPath(path);
        } else {
            path = info.getPath();
        }
        Map attrs = new LinkedHashMap();
        attrs.put("kind", info.getKind().toString());
        attrs.put("path", path);
        attrs.put("revision", info.getRevision().toString());
        buffer = openXMLTag("entry", SVNXMLUtil.XML_STYLE_NORMAL, attrs, buffer);
        
        String url = info.getURL() != null ? info.getURL().toString() : null;
        buffer = openCDataTag("url", url, buffer);
        
        String rootURL = info.getRepositoryRootURL() != null ? info.getRepositoryUUID().toString() : null;
        String uuid = info.getRepositoryUUID();
        if (rootURL != null || uuid != null) {
            buffer = openXMLTag("repository", SVNXMLUtil.XML_STYLE_NORMAL, null, buffer);
            buffer = openCDataTag("root", rootURL, buffer);
            buffer = openCDataTag("uuid", uuid, buffer);
            buffer = closeXMLTag("repository", buffer);
        }   
        if (info.getFile() != null) {
            buffer = openXMLTag("wc-info", SVNXMLUtil.XML_STYLE_NORMAL, null, buffer);
            String schedule = info.getSchedule();
            if (schedule == null || "".equals(schedule)) {
                schedule = "normal";
            }
            buffer = openCDataTag("schedule", schedule, buffer);
            if (info.getDepth() != null) {
                buffer = openCDataTag("depth", info.getDepth().getName(), buffer);
            }
            if (info.getCopyFromURL() != null) {
                buffer = openCDataTag("copy-from-url", info.getCopyFromURL().toString(), buffer);
            }
            if (info.getCopyFromRevision() != null && info.getCopyFromRevision().isValid()) {
                buffer = openCDataTag("copy-from-rev", info.getCopyFromRevision().toString(), buffer);
            }
            if (info.getTextTime() != null) {
                buffer = openCDataTag("text-updated", ((SVNDate) info.getTextTime()).format(), buffer);
            }
            if (info.getPropTime() != null) {
                buffer = openCDataTag("prop-updated", ((SVNDate) info.getPropTime()).format(), buffer);
            }
            buffer = openCDataTag("checksum", info.getChecksum(), buffer);
            buffer = openCDataTag("changelist", info.getChangelistName(), buffer);
            buffer = closeXMLTag("wc-info", buffer);
        }
        if (info.getAuthor() != null || info.getCommittedRevision().isValid() ||
                info.getCommittedDate() != null) {
            openXMLTag("commit", SVNXMLUtil.XML_STYLE_NORMAL, "revision", info.getCommittedRevision().toString(), buffer);
            buffer = openCDataTag("author", info.getAuthor(), buffer);
            if (info.getCommittedDate() != null) {
                buffer = openCDataTag("date", ((SVNDate) info.getCommittedDate()).format(), buffer);
            }
            buffer = closeXMLTag("commit", buffer);
        }
        
        if (info.getConflictNewFile() != null || info.getConflictOldFile() != null || info.getConflictWrkFile() != null ||
                info.getPropConflictFile() != null) {
            buffer = openXMLTag("conflict", SVNXMLUtil.XML_STYLE_NORMAL, null, buffer);
            if (info.getConflictOldFile() != null) {
                buffer = openCDataTag("prev-base-file", info.getConflictOldFile().getName(), buffer);
            }
            if (info.getConflictWrkFile() != null) {
                buffer = openCDataTag("prev-wc-file", info.getConflictWrkFile().getName(), buffer);
            }
            if (info.getConflictNewFile() != null) {
                buffer = openCDataTag("cur-base-file", info.getConflictNewFile().getName(), buffer);
            }
            if (info.getPropConflictFile() != null) {
                buffer = openCDataTag("prop-file", info.getPropConflictFile().getName(), buffer);
            }
            buffer = closeXMLTag("conflict", buffer);
        }
        
        if (info.getLock() != null) {
            SVNLock lock = info.getLock();
            buffer = openXMLTag("lock", SVNXMLUtil.XML_STYLE_NORMAL, null, buffer);
            buffer = openCDataTag("token", lock.getID(), buffer);
            buffer = openCDataTag("owner", lock.getOwner(), buffer);
            buffer = openCDataTag("comment", lock.getComment(), buffer);
            if (lock.getCreationDate() != null) {
                buffer = openCDataTag("created", ((SVNDate) lock.getCreationDate()).format(), buffer);
            }
            if (lock.getExpirationDate() != null) {
                buffer = openCDataTag("expires", ((SVNDate) lock.getExpirationDate()).format(), buffer);
            }
            buffer = closeXMLTag("lock", buffer);
        }
        buffer = closeXMLTag("entry", buffer);
        
        getSVNEnvironment().getOut().print(buffer.toString());
    }

}
