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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.cli2.SVNCommandTarget;
import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.SVNLogClient;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNListCommand extends SVNXMLCommand implements ISVNDirEntryHandler {

    private static final DateFormat LONG_DATE_FORMAT = new SimpleDateFormat("MM' 'dd'  'yyyy");
    private static final DateFormat SHORT_DATE_FORMAT = new SimpleDateFormat("MM' 'dd'  'HH:mm");


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
        options = SVNOption.addAuthOptions(options);
        options.add(SVNOption.CONFIG_DIR);
        return options;
    }

    public void run() throws SVNException {
        List targets = getSVNEnvironment().combineTargets(null);
        if (targets.isEmpty()) {
            targets.add("");
        }
        if (getSVNEnvironment().isXML()) {
            if (getSVNEnvironment().isVerbose()) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "'verbose' option invalid in XML mode"));
            }
            if (!getSVNEnvironment().isIncremental()) {
                printXMLHeader("lists");
            }
        } else if (getSVNEnvironment().isIncremental()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "'incremental' option only valid in XML mode"));
        }
        
        SVNDepth depth = getSVNEnvironment().getDepth();
        if (depth == SVNDepth.UNKNOWN) {
            depth = SVNDepth.IMMEDIATES;
        }
        int fields = getSVNEnvironment().isXML() || getSVNEnvironment().isVerbose() ? 
                SVNDirEntry.DIRENT_ALL : SVNDirEntry.DIRENT_KIND | SVNDirEntry.DIRENT_TIME;
        boolean fetchLocks = getSVNEnvironment().isXML() || getSVNEnvironment().isVerbose();
        SVNLogClient client = getSVNEnvironment().getClientManager().getLogClient();
        for (int i = 0; i < targets.size(); i++) {
            String targetName = (String) targets.get(i);
            SVNCommandTarget target = new SVNCommandTarget(targetName, true);
            if (getSVNEnvironment().isXML()) {
                StringBuffer buffer = openXMLTag("list", XML_STYLE_NORMAL, "path", 
                        "".equals(target.getTarget()) ? "." : target.getTarget(), new StringBuffer());
                getSVNEnvironment().getOut().print(buffer.toString());
            }
            if (!target.isURL()) {
                client.doList(target.getFile(), target.getPegRevision(), getSVNEnvironment().getStartRevision(), fetchLocks, depth, fields, this);
            } else {
                client.doList(target.getURL(), target.getPegRevision(), getSVNEnvironment().getStartRevision(), fetchLocks, depth, fields, this);
            }
            if (getSVNEnvironment().isXML()) {
                StringBuffer buffer = closeXMLTag("list", new StringBuffer());
                getSVNEnvironment().getOut().print(buffer.toString());
            }
        }
        
        if (getSVNEnvironment().isXML() && !getSVNEnvironment().isIncremental()) {
            printXMLFooter("lists");
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
            buffer.append(SVNCommand.formatString(dirEntry.getRevision() + "", 7, false));
            buffer.append(' ');
            buffer.append(SVNCommand.formatString(dirEntry.getAuthor() == null ? " ? " : dirEntry.getAuthor(), 16, true));
            buffer.append(' ');
            buffer.append(dirEntry.getLock() != null ? 'O' : ' ');
            buffer.append(' ');
            buffer.append(SVNCommand.formatString(dirEntry.getKind() == SVNNodeKind.DIR ? "" : dirEntry.getSize() + "", 10, false));
            buffer.append(' ');
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
            buffer.append(SVNCommand.formatString(timeStr, 12, false));
            buffer.append(' ');
        }
        buffer.append(path);
        if (dirEntry.getKind() == SVNNodeKind.DIR && !".".equals(path)) {
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
        buffer = openXMLTag("entry", XML_STYLE_NORMAL, "kind", dirEntry.getKind().toString(), buffer);
        buffer = openCDataTag("name", path, buffer);
        if (dirEntry.getKind() == SVNNodeKind.FILE) {
            buffer = openCDataTag("size", Long.toString(dirEntry.getSize()), buffer);
        }
        buffer = openXMLTag("commit", XML_STYLE_NORMAL, "revision", Long.toString(dirEntry.getRevision()), buffer);
        buffer = openCDataTag("author", dirEntry.getAuthor(), buffer);
        if (dirEntry.getDate() != null) {
            buffer = openCDataTag("date", ((SVNDate) dirEntry.getDate()).format(), buffer);
        }
        buffer = closeXMLTag("commit", buffer);
        
        SVNLock lock = dirEntry.getLock();
        if (lock != null) {
            buffer = openXMLTag("lock", XML_STYLE_NORMAL, null, buffer);
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
