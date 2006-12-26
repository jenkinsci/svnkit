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
import java.io.InputStream;
import java.io.PrintStream;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNFormatUtil;
import org.tmatesoft.svn.core.wc.ISVNPropertyHandler;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNProplistCommand extends SVNCommand implements ISVNPropertyHandler {

    private boolean myIsVerbose;
    private boolean myIsRecursive;
    private PrintStream myOut;
    private boolean myIsRevProp;

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

    public final void run(final PrintStream out, PrintStream err) throws SVNException {
        myIsRecursive = getCommandLine().hasArgument(SVNArgument.RECURSIVE);
        myIsRevProp = getCommandLine().hasArgument(SVNArgument.REV_PROP);
        myIsVerbose = getCommandLine().hasArgument(SVNArgument.VERBOSE);
        myOut = out;
        myIsRecursive = myIsRecursive & !myIsRevProp;
        SVNRevision revision = SVNRevision.UNDEFINED;
        if (getCommandLine().hasArgument(SVNArgument.REVISION)) {
            revision = SVNRevision.parse((String) getCommandLine().getArgumentValue(SVNArgument.REVISION));
        }
        SVNWCClient wcClient = getClientManager().getWCClient();
        if (getCommandLine().hasURLs()) {
            String url = getCommandLine().getURL(0);
            if (myIsRevProp) {
                wcClient.doGetRevisionProperty(SVNURL.parseURIEncoded(url), null, revision, this);
            } else {
                SVNRevision pegRevision = getCommandLine().getPegRevision(0);
                wcClient.doGetProperty(SVNURL.parseURIEncoded(url), null, pegRevision, revision, myIsRecursive, this);
            }
        } else if (getCommandLine().getPathCount() > 0) {
            String path = getCommandLine().getPathAt(0);
            SVNRevision pegRevision = getCommandLine().getPathPegRevision(0);
            if (myIsRevProp) {
                wcClient.doGetRevisionProperty(new File(path), null, revision, this);
            } else {
                wcClient.doGetProperty(new File(path), null, pegRevision, revision, myIsRecursive, this);
            }
        }
    }

    private File myCurrentFile;
    private SVNURL myCurrentURL;

    public void handleProperty(File path, SVNPropertyData property) throws SVNException {
        if (!path.equals(myCurrentFile)) {
            myOut.println("Properties on '" + SVNFormatUtil.formatPath(path) + "':");
            myCurrentFile = path;
        }
        myOut.print("  ");
        myOut.print(property.getName());
        if (myIsVerbose) {
            myOut.print(" : " + property.getValue());
        }
        myOut.println();
    }

    public void handleProperty(SVNURL url, SVNPropertyData property) throws SVNException {
        if (!myIsRevProp) {
            if (!url.equals(myCurrentURL)) {
                myOut.println("Properties on '" + url + "':");
                myCurrentURL = url;
            }
        } else if (myCurrentURL == null){
            myOut.println("Unversioned properties on revision " + url + ":");
            myCurrentURL = url;
        }
        myOut.print("  ");
        myOut.print(property.getName());
        if (myIsVerbose) {
            myOut.print(" : " + property.getValue());
        }
        myOut.println();
    }
    
    public void handleProperty(long revision, SVNPropertyData property) throws SVNException {        
    }
}
