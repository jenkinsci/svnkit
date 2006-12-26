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
public class SVNPropdelCommand extends SVNCommand {

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

    public final void run(final PrintStream out, PrintStream err) throws SVNException {
        final String propertyName = getCommandLine().getPathAt(0);
        final boolean recursive = getCommandLine().hasArgument(SVNArgument.RECURSIVE);
        boolean revProp = getCommandLine().hasArgument(SVNArgument.REV_PROP);
        boolean force = getCommandLine().hasArgument(SVNArgument.FORCE);
        int pathIndex = 1;

        SVNWCClient wcClient = getClientManager().getWCClient();
        if (revProp) {
            SVNRevision revision = SVNRevision.UNDEFINED;
            if (getCommandLine().hasArgument(SVNArgument.REVISION)) {
                revision = SVNRevision.parse((String) getCommandLine().getArgumentValue(SVNArgument.REVISION));
            }
            if (getCommandLine().hasURLs()) {
                wcClient.doSetRevisionProperty(SVNURL.parseURIEncoded(getCommandLine().getURL(0)),
                        revision, propertyName, null, force, new ISVNPropertyHandler() {
                    public void handleProperty(File path, SVNPropertyData property) throws SVNException {
                    }
                    public void handleProperty(long revision, SVNPropertyData property) throws SVNException {
                        out.println("Property '" + propertyName +"' deleted on repository revision " + revision);
                    }
                    public void handleProperty(SVNURL url, SVNPropertyData property) throws SVNException {
                    }
                });

            } else {
                File tgt = new File(".");
                if (getCommandLine().getPathCount() > 1) {
                    tgt = new File(getCommandLine().getPathAt(1));
                }
                wcClient.doSetRevisionProperty(tgt, revision, propertyName, null, force, new ISVNPropertyHandler() {
                    public void handleProperty(File path, SVNPropertyData property) throws SVNException {
                    }
                    public void handleProperty(long revision, SVNPropertyData property) throws SVNException {
                        out.println("Property '" + propertyName +"' deleted on repository revision " + revision);
                    }
                    public void handleProperty(SVNURL url, SVNPropertyData property) throws SVNException {
                    }
                });
            }
        } else {
            for (int i = pathIndex; i < getCommandLine().getPathCount(); i++) {
                String absolutePath = getCommandLine().getPathAt(i);
                if (!recursive) {
                    wcClient.doSetProperty(new File(absolutePath), propertyName, null, force, recursive, new ISVNPropertyHandler() {
                        public void handleProperty(File path, SVNPropertyData property) throws SVNException {
                            out.println("Property '" + propertyName + "' deleted on '" + SVNFormatUtil.formatPath(path) + "'");
                        }
                        public void handleProperty(SVNURL url, SVNPropertyData property) throws SVNException {
                        }
                        public void handleProperty(long revision, SVNPropertyData property) throws SVNException {
                        }

                    });
                } else {
                    final boolean wasSet[] = new boolean[] {false};
                    wcClient.doSetProperty(new File(absolutePath), propertyName, null, force, recursive, new ISVNPropertyHandler() {
                        public void handleProperty(File path, SVNPropertyData property) throws SVNException {
                           wasSet[0] = true;
                        }
                        public void handleProperty(SVNURL url, SVNPropertyData property) throws SVNException {
                        }
                        public void handleProperty(long revision, SVNPropertyData property) throws SVNException {
                        }
                    });
                    if (wasSet[0]) {
                        out.println("Property '" + propertyName + "' deleted (recursively) on '" + absolutePath + "'");
                    }
                }

            }
        }
    }
}
