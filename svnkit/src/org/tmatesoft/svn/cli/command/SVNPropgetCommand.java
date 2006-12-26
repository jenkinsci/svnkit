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
public class SVNPropgetCommand extends SVNCommand implements ISVNPropertyHandler {

    private boolean myIsStrict;
    private boolean myIsRecursive;
    private PrintStream myOut;

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

    public final void run(final PrintStream out, PrintStream err) throws SVNException {
        String propertyName = getCommandLine().getPathAt(0);
        myIsRecursive = getCommandLine().hasArgument(SVNArgument.RECURSIVE);
        boolean revProp = getCommandLine().hasArgument(SVNArgument.REV_PROP);
        myIsStrict = getCommandLine().hasArgument(SVNArgument.STRICT);
        myOut = out;
        myIsRecursive = myIsRecursive & !revProp;
        SVNRevision revision = SVNRevision.UNDEFINED;
        if (getCommandLine().hasArgument(SVNArgument.REVISION)) {
            revision = SVNRevision.parse((String) getCommandLine().getArgumentValue(SVNArgument.REVISION));
        }
        SVNWCClient wcClient = getClientManager().getWCClient();
        if (getCommandLine().hasURLs()) {
            String url = getCommandLine().getURL(0);
            if (revProp) {
                wcClient.doGetRevisionProperty(SVNURL.parseURIEncoded(url), propertyName, revision, this);
            } else {
                SVNRevision pegRevision = getCommandLine().getPegRevision(0);
                wcClient.doGetProperty(SVNURL.parseURIEncoded(url), propertyName, pegRevision, revision, myIsRecursive, this);
            }
        } else if (getCommandLine().getPathCount() > 1) {
            String path = getCommandLine().getPathAt(1);
            SVNRevision pegRevision = getCommandLine().getPathPegRevision(1);
            if (revProp) {
                wcClient.doGetRevisionProperty(new File(path), propertyName, revision, this);
            } else {
                wcClient.doGetProperty(new File(path), propertyName, pegRevision, revision, myIsRecursive, this);
            }
        }
    }

    public void handleProperty(File path, SVNPropertyData property) throws SVNException {
        if (!myIsStrict && myIsRecursive) {
            myOut.print(SVNFormatUtil.formatPath(path) + " - ");
        }
        myOut.print(property.getValue());
        if (!myIsStrict) {
            myOut.println();
        }
    }

    public void handleProperty(SVNURL url, SVNPropertyData property) throws SVNException {
        if (!myIsStrict && myIsRecursive) {
            myOut.print(url + " - ");
        }
        myOut.print(property.getValue());
        if (!myIsStrict) {
            myOut.println();
        }
    }
    
    public void handleProperty(long revision, SVNPropertyData property) throws SVNException {
        myOut.print(property.getValue());
        if (!myIsStrict) {
            myOut.println();
        }
    }
    
}
