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

import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNFormatUtil;
import org.tmatesoft.svn.core.internal.util.SVNXMLUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNListCommand extends SVNXMLCommand implements ISVNDirEntryHandler {

    public SVNListCommand() {
        super("list", new String[] {"ls"});
    }

    protected Collection createSupportedOptions() {
        Collection options = new LinkedList();
        options.add(SVNOption.REVISION);
        options.add(SVNOption.VERBOSE);
        options.add(SVNOption.RECURSIVE);
        options.add(SVNOption.DEPTH);
        options.add(SVNOption.INCREMENTAL);
        options.add(SVNOption.XML);
        return options;
    }

    public void run() throws SVNException {
        List targets = getSVNEnvironment().combineTargets(null, true);
        if (targets.isEmpty()) {
            targets.add("");
        }
        if (getSVNEnvironment().isXML()) {
            if (getSVNEnvironment().isVerbose()) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "'verbose' option invalid in XML mode"), SVNLogType.CLIENT);
            }
            if (!getSVNEnvironment().isIncremental()) {
                printXMLHeader("lists");
            }
        } else if (getSVNEnvironment().isIncremental()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "'incremental' option only valid in XML mode"), SVNLogType.CLIENT);
        }
        
        SVNDepth depth = getSVNEnvironment().getDepth();
        if (depth == SVNDepth.UNKNOWN) {
            depth = SVNDepth.IMMEDIATES;
        }
        int fields = getSVNEnvironment().isXML() || getSVNEnvironment().isVerbose() ? 
                SVNDirEntry.DIRENT_ALL : SVNDirEntry.DIRENT_KIND | SVNDirEntry.DIRENT_TIME;
        boolean fetchLocks = getSVNEnvironment().isXML() || getSVNEnvironment().isVerbose();
        SVNLogClient client = getSVNEnvironment().getClientManager().getLogClient();
        boolean seenNonExistentPaths = false;
        for (int i = 0; i < targets.size(); i++) {
            String targetName = (String) targets.get(i);
            SVNPath target = new SVNPath(targetName, true);
            if (getSVNEnvironment().isXML()) {
                StringBuffer buffer = openXMLTag("list", SVNXMLUtil.XML_STYLE_NORMAL, "path",
                        "".equals(target.getTarget()) ? "." : target.getTarget(), new StringBuffer());
                getSVNEnvironment().getOut().print(buffer.toString());
            }
            try {
                if (!target.isURL()) {
                    client.doList(target.getFile(), target.getPegRevision(), getSVNEnvironment().getStartRevision(), fetchLocks, depth, fields, this);
                } else {
                    client.doList(target.getURL(), target.getPegRevision(), getSVNEnvironment().getStartRevision(), fetchLocks, depth, fields, this);
                }
            } catch (SVNException e) {
                getSVNEnvironment().handleWarning(e.getErrorMessage(), 
                        new SVNErrorCode[] {SVNErrorCode.WC_PATH_NOT_FOUND, SVNErrorCode.FS_NOT_FOUND},
                        getSVNEnvironment().isQuiet());
                seenNonExistentPaths = true;
            }
            if (getSVNEnvironment().isXML()) {
                StringBuffer buffer = closeXMLTag("list", new StringBuffer());
                getSVNEnvironment().getOut().print(buffer.toString());
            }
        }
        
        if (getSVNEnvironment().isXML() && !getSVNEnvironment().isIncremental()) {
            printXMLFooter("lists");
        }
        if (seenNonExistentPaths) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "Could not list all targets because some targets don't exist");
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }
    }

    public void handleDirEntry(SVNDirEntry dirEntry) throws SVNException {
        if (getSVNEnvironment().isXML()) {
            printDirEntryXML(dirEntry);
        } else {
            printDirEntry(dirEntry);
        }
    }
    
    protected void printDirEntry(SVNDirEntry dirEntry) {
        String path = dirEntry.getRelativePath();
        if ("".equals(path)) {
            if (getSVNEnvironment().isVerbose() && dirEntry.getKind() == SVNNodeKind.DIR) {
                path = ".";
            } else if (dirEntry.getKind() == SVNNodeKind.DIR) {
                return;
            }
        }
        StringBuffer buffer = new StringBuffer();
        if (getSVNEnvironment().isVerbose()) {
            buffer.append(SVNFormatUtil.formatString(dirEntry.getRevision() + "", 7, false));
            buffer.append(' ');
            String author = dirEntry.getAuthor() == null ? " ? " : dirEntry.getAuthor();
            if (author.length() > 8) {
                author = author.substring(0, 8);
            }
            buffer.append(SVNFormatUtil.formatString(author, 8, true));
            buffer.append(' ');
            buffer.append(dirEntry.getLock() != null ? 'O' : ' ');
            buffer.append(' ');
            buffer.append(SVNFormatUtil.formatString(dirEntry.getKind() == SVNNodeKind.DIR ? "" : dirEntry.getSize() + "", 10, false));
            buffer.append(' ');
            // time now.
            Date d = dirEntry.getDate();
            String timeStr = "";
            if (d != null) {
                if (System.currentTimeMillis() - d.getTime() < 365 * 1000 * 86400 / 2) {
                    timeStr = SVNDate.formatConsoleShortDate(d);
                } else {
                    timeStr = SVNDate.formatConsoleLongDate(d);
                }
            }
            buffer.append(SVNFormatUtil.formatString(timeStr, 12, false));
            buffer.append(' ');
        }
        buffer.append(path);
        if (dirEntry.getKind() == SVNNodeKind.DIR) {
            buffer.append('/');
        }
        buffer.append('\n');
        getSVNEnvironment().getOut().print(buffer.toString());
    }

    protected void printDirEntryXML(SVNDirEntry dirEntry) {
        String path = dirEntry.getRelativePath();
        if ("".equals(path)) {
            if (getSVNEnvironment().isVerbose() && dirEntry.getKind() == SVNNodeKind.DIR) {
                path = ".";
            } else if (dirEntry.getKind() == SVNNodeKind.DIR) {
                return;
            }
        }
        StringBuffer buffer = new StringBuffer();
        buffer = openXMLTag("entry", SVNXMLUtil.XML_STYLE_NORMAL, "kind", dirEntry.getKind().toString(), buffer);
        buffer = openCDataTag("name", path, buffer);
        if (dirEntry.getKind() == SVNNodeKind.FILE) {
            buffer = openCDataTag("size", Long.toString(dirEntry.getSize()), buffer);
        }
        buffer = openXMLTag("commit", SVNXMLUtil.XML_STYLE_NORMAL, "revision", Long.toString(dirEntry.getRevision()), buffer);
        buffer = openCDataTag("author", dirEntry.getAuthor(), buffer);
        if (dirEntry.getDate() != null) {
            buffer = openCDataTag("date", ((SVNDate) dirEntry.getDate()).format(), buffer);
        }
        buffer = closeXMLTag("commit", buffer);
        
        SVNLock lock = dirEntry.getLock();
        if (lock != null) {
            buffer = openXMLTag("lock", SVNXMLUtil.XML_STYLE_NORMAL, null, buffer);
            buffer = openCDataTag("token", lock.getID(), buffer);
            buffer = openCDataTag("owner", lock.getOwner(), buffer);
            buffer = openCDataTag("comment", lock.getComment(), buffer);
            buffer = openCDataTag("created", ((SVNDate) lock.getCreationDate()).format(), buffer);
            if (lock.getExpirationDate() != null && lock.getExpirationDate().getTime() > 0) {
                buffer = openCDataTag("expires", ((SVNDate) lock.getExpirationDate()).format(), buffer);
            }
            buffer = closeXMLTag("lock", buffer);
        }
        buffer = closeXMLTag("entry", buffer);

        getSVNEnvironment().getOut().print(buffer.toString());
    }

}
