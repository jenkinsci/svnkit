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
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNFormatUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.ISVNInfoHandler;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.core.wc.xml.SVNXMLInfoHandler;
import org.tmatesoft.svn.core.wc.xml.SVNXMLSerializer;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNInfoCommand extends SVNCommand implements ISVNInfoHandler {

    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z (EE, d MMM yyyy)", Locale.getDefault());

    private PrintStream myOut;
    private File myBaseFile;

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

    public final void run(final PrintStream out, PrintStream err) throws SVNException {
        final boolean recursive = getCommandLine().hasArgument(SVNArgument.RECURSIVE);
        SVNRevision revision = SVNRevision.UNDEFINED;

        if (getCommandLine().hasArgument(SVNArgument.REVISION)) {
            revision = SVNRevision.parse((String) getCommandLine().getArgumentValue(SVNArgument.REVISION));
        }
        SVNWCClient wcClient = getClientManager().getWCClient();
        myOut = out;
        SVNXMLSerializer serializer = new SVNXMLSerializer(myOut);
        SVNXMLInfoHandler handler = new SVNXMLInfoHandler(serializer);
        if (getCommandLine().hasArgument(SVNArgument.XML) && !getCommandLine().hasArgument(SVNArgument.INCREMENTAL)) {
            handler.startDocument();
        }
        ISVNInfoHandler infoHandler = getCommandLine().hasArgument(SVNArgument.XML) ? handler : (ISVNInfoHandler) this;
        for (int i = 0; i < getCommandLine().getPathCount(); i++) {
            myBaseFile = new File(getCommandLine().getPathAt(i));
            SVNRevision peg = getCommandLine().getPathPegRevision(i);
            handler.setTargetPath(myBaseFile);
            wcClient.doInfo(myBaseFile, peg, revision, recursive, infoHandler);
        }
        myBaseFile = null;
        for (int i = 0; i < getCommandLine().getURLCount(); i++) {
            String url = getCommandLine().getURL(i);
            SVNRevision peg = getCommandLine().getPegRevision(i);
            wcClient.doInfo(SVNURL.parseURIEncoded(url), peg, revision, recursive, infoHandler);
        }
        if (getCommandLine().hasArgument(SVNArgument.XML)&& !getCommandLine().hasArgument(SVNArgument.INCREMENTAL)) {
            handler.endDocument();
        }
        if (getCommandLine().hasArgument(SVNArgument.XML)) {
            try {
                serializer.flush();
            } catch (IOException e) {
            }
        }
    }

    private static void print(String str, PrintStream out) {
        out.println(str);
    }

    public void handleInfo(SVNInfo info) {
        if (!info.isRemote()) {
            print("Path: " + SVNFormatUtil.formatPath(info.getFile()), myOut);
        } else if (info.getPath() != null) {
            String path = info.getPath();
            if (myBaseFile != null) {
                File file = new File(myBaseFile, path);
                path = SVNFormatUtil.formatPath(file);
            } else {
                path = path.replace('/', File.separatorChar);
            }
            print("Path: " + path, myOut);
        }
        if (info.getKind() != SVNNodeKind.DIR) {
            if (info.isRemote()) {
                print("Name: " + SVNPathUtil.tail(info.getPath()), myOut);
            } else {
                print("Name: " + info.getFile().getName(), myOut);
            }
        }
        print("URL: " + info.getURL(), myOut);
        if (info.getRepositoryRootURL() != null) {
            print("Repository Root: " + info.getRepositoryRootURL(), myOut);
        }
        if (info.isRemote() && info.getRepositoryUUID() != null) {
            print("Repository UUID: " + info.getRepositoryUUID(), myOut);
        }
        if (info.getRevision() != null && info.getRevision().isValid()) {
            print("Revision: " + info.getRevision(), myOut);
        }
        if (info.getKind() == SVNNodeKind.DIR) {
            print("Node Kind: directory", myOut);
        } else if (info.getKind() == SVNNodeKind.FILE) {
            print("Node Kind: file", myOut);
        } else if (info.getKind() == SVNNodeKind.NONE) {
            print("Node Kind: none", myOut);
        } else {
            print("Node Kind: unknown", myOut);
        }
        if (info.getSchedule() == null && !info.isRemote()) {
            print("Schedule: normal", myOut);
        } else if (!info.isRemote()) {
            print("Schedule: " + info.getSchedule(), myOut);
        }
        if (info.getAuthor() != null) {
            print("Last Changed Author: " + info.getAuthor(), myOut);
        }
        if (info.getCommittedRevision() != null && info.getCommittedRevision().getNumber() >= 0) {
            print("Last Changed Rev: " + info.getCommittedRevision(), myOut);
        }
        if (info.getCommittedDate() != null) {
            print("Last Changed Date: " + formatDate(info.getCommittedDate()), myOut);
        }
        if (!info.isRemote()) {
            if (info.getTextTime() != null) {
                print("Text Last Updated: " + formatDate(info.getTextTime()), myOut);
            }
            if (info.getPropTime() != null) {
                print("Properties Last Updated: " + formatDate(info.getPropTime()), myOut);
            }
            if (info.getChecksum() != null) {
                print("Checksum: " + info.getChecksum(), myOut);
            }
            if (info.getCopyFromURL() != null) {
                print("Copied From URL: " + info.getCopyFromURL(), myOut);
            }
            if (info.getCopyFromRevision() != null && info.getCopyFromRevision().getNumber() >= 0) {
                print("Copied From Rev: " + info.getCopyFromRevision(), myOut);
            }
            if (info.getConflictOldFile() != null) {
                print("Conflict Previous Base File: " + info.getConflictOldFile().getName(), myOut);
            }
            if (info.getConflictWrkFile() != null) {
                print("Conflict Previous Working File: " + info.getConflictWrkFile().getName(), myOut);
            }
            if (info.getConflictNewFile() != null) {
                print("Conflict Current Base File: " + info.getConflictNewFile().getName(), myOut);
            }
            if (info.getPropConflictFile() != null) {
                print("Conflict Properties File: " + info.getPropConflictFile().getName(), myOut);
            }
        }
        if (info.getLock() != null) {
            SVNLock lock = info.getLock();
            print("Lock Token: " + lock.getID(), myOut);
            print("Lock Owner: " + lock.getOwner(), myOut);
            print("Lock Created: " + formatDate(lock.getCreationDate()), myOut);
            if (lock.getComment() != null) {
                myOut.print("Lock Comment ");
                int lineCount = getLinesCount(lock.getComment());
                if (lineCount == 1) {
                    myOut.print("(1 line)");
                } else {
                    myOut.print("(" + lineCount + " lines)");
                }
                myOut.print(":\n" + lock.getComment() + "\n");
            }
        }
        println(myOut);
    }

    private static String formatDate(Date date) {
        return DATE_FORMAT.format(date);
    }
}
