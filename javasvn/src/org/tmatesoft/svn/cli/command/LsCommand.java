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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;

import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.io.SVNDirEntry;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.core.io.ISVNDirEntryHandler;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.util.SVNUtil;

/**
 * @author TMate Software Ltd.
 */
public class LsCommand extends SVNCommand implements ISVNDirEntryHandler {

    private boolean myIsVerbose;
    private boolean myIsIncremental;
    private boolean myIsXML;

    private PrintStream myPrintStream;

    public void run(PrintStream out, PrintStream err) throws SVNException {
        myIsVerbose = getCommandLine().hasArgument(SVNArgument.VERBOSE);
        myIsIncremental = getCommandLine().hasArgument(SVNArgument.INCREMENTAL);
        myIsXML = getCommandLine().hasArgument(SVNArgument.XML);
        boolean recursive = getCommandLine().hasArgument(SVNArgument.RECURSIVE);
        myPrintStream = out;

        SVNRevision revision = parseRevision(getCommandLine());
        SVNLogClient logClient = new SVNLogClient(getCredentialsProvider(), getOptions(), null);
        if (getCommandLine().getURLCount() == 0 && getCommandLine().getPathCount() == 0) {
            getCommandLine().setPathAt(0, ".");
        }
        for(int i = 0; i < getCommandLine().getURLCount(); i++) {
            String url = getCommandLine().getURL(i);
            logClient.doList(url, getCommandLine().getPegRevision(i), revision == null || !revision.isValid() ? SVNRevision.HEAD : revision, recursive, this);
        }
        for(int i = 0; i < getCommandLine().getPathCount(); i++) {
            File path = new File(getCommandLine().getPathAt(i)).getAbsoluteFile();
            logClient.doList(path, getCommandLine().getPathPegRevision(i), revision == null || !revision.isValid() ? SVNRevision.BASE : revision, recursive, this);
        }
    }

    public void handleDirEntry(SVNDirEntry dirEntry) {
        myPrintStream.print(dirEntry.getPath());
        if (dirEntry.getKind() == SVNNodeKind.DIR) {
            myPrintStream.print('/');
        }
        myPrintStream.println();
    }

}
