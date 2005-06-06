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

import java.io.File;
import java.io.PrintStream;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.wc.ISVNPropertyHandler;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

/**
 * @author TMate Software Ltd.
 */
public class PropdelCommand extends SVNCommand {

    public final void run(final PrintStream out, PrintStream err) throws SVNException {
        final String propertyName = getCommandLine().getPathAt(0);
        final boolean recursive = getCommandLine().hasArgument(SVNArgument.RECURSIVE);
        boolean revProp = getCommandLine().hasArgument(SVNArgument.REV_PROP);
        int pathIndex = 1;

        SVNWCClient wcClient = new SVNWCClient(getCredentialsProvider(), getOptions(), null);
        if (revProp) {
            SVNRevision revision = SVNRevision.UNDEFINED;
            if (getCommandLine().hasArgument(SVNArgument.REVISION)) {
                revision = SVNRevision.parse((String) getCommandLine().getArgumentValue(SVNArgument.REVISION));
            }
            if (getCommandLine().hasURLs()) {
                wcClient.doSetRevisionProperty(getCommandLine().getURL(0), getCommandLine().getPegRevision(0), 
                        revision, propertyName, null, new ISVNPropertyHandler() {
                            public void handleProperty(File path, SVNPropertyData property) throws SVNException {
                            }
                            public void handleProperty(String url, SVNPropertyData property) throws SVNException {
                                out.println("Property '" + propertyName +"' deleted on repository revision " + url);
                            }
                });
                        
            } else {
                File tgt = new File(".");
                if (getCommandLine().getPathCount() > 1) {
                    tgt = new File(getCommandLine().getPathAt(1));
                }
                wcClient.doSetRevisionProperty(tgt, revision, propertyName, null, new ISVNPropertyHandler() {
                            public void handleProperty(File path, SVNPropertyData property) throws SVNException {
                            }
                            public void handleProperty(String url, SVNPropertyData property) throws SVNException {
                                out.println("Property '" + propertyName +"' deleted on repository revision " + url);
                            }
                });
            }
        } else {
            for (int i = pathIndex; i < getCommandLine().getPathCount(); i++) {
                String absolutePath = getCommandLine().getPathAt(i);
                if (!recursive) {
                    wcClient.doSetProperty(new File(absolutePath), propertyName, null, recursive, new ISVNPropertyHandler() {
                        public void handleProperty(File path, SVNPropertyData property) throws SVNException {
                            out.println("Property '" + propertyName + "' deleted on '" + getPath(path) + "'");
                        }
                        public void handleProperty(String url, SVNPropertyData property) throws SVNException {
                        }
                        
                    });
                } else {
                    final boolean wasSet[] = new boolean[] {false};
                    wcClient.doSetProperty(new File(absolutePath), propertyName, null, recursive, new ISVNPropertyHandler() {
                        public void handleProperty(File path, SVNPropertyData property) throws SVNException {
                           wasSet[0] = true;
                        }
                        public void handleProperty(String url, SVNPropertyData property) throws SVNException {
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
