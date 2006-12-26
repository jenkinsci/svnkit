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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
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
public class SVNPropsetCommand extends SVNCommand {

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

    public final void run(final PrintStream out, PrintStream err) throws SVNException {
        final String propertyName = getCommandLine().getPathAt(0);
        String propertyValue = getCommandLine().getPathAt(1);
        final boolean recursive = getCommandLine().hasArgument(SVNArgument.RECURSIVE);
        boolean force = getCommandLine().hasArgument(SVNArgument.FORCE);
        boolean revProps = getCommandLine().hasArgument(SVNArgument.REV_PROP);

        int pathIndex = 2;
        if (getCommandLine().hasArgument(SVNArgument.FILE)) {
            File file = new File((String) getCommandLine().getArgumentValue(SVNArgument.FILE));
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            FileInputStream is = null;
            try {
                is = new FileInputStream(file);
                while(true) {
                    int r = is.read();
                    if (r < 0) {
                        break;
                    }
                    os.write(r);
                }
            } catch (IOException e) {
                SVNErrorMessage msg = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, e.getLocalizedMessage());
                throw new SVNException(msg, e);
            } finally {
                try {
                    os.close();
                } catch (IOException e1) {
                }
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException e) {
                    }
                }
            }
            propertyValue = os.toString();
            pathIndex = 1;
        }

        SVNWCClient wcClient = getClientManager().getWCClient();

        if (revProps) {
            SVNRevision revision = SVNRevision.UNDEFINED;
            if (getCommandLine().hasArgument(SVNArgument.REVISION)) {
                revision = SVNRevision.parse((String) getCommandLine().getArgumentValue(SVNArgument.REVISION));
            }
            if (getCommandLine().hasURLs()) {
                wcClient.doSetRevisionProperty(SVNURL.parseURIEncoded(getCommandLine().getURL(0)),
                        revision, propertyName, propertyValue, force, new ISVNPropertyHandler() {
                            public void handleProperty(File path, SVNPropertyData property) throws SVNException {
                            }
                            public void handleProperty(SVNURL url, SVNPropertyData property) throws SVNException {
                                out.println("property '" + propertyName +"' set on repository revision " + url);
                            }
                            public void handleProperty(long revision, SVNPropertyData property) throws SVNException {
                            }
                });

            } else {
                File tgt = new File(".");
                if (getCommandLine().getPathCount() > 2) {
                    tgt = new File(getCommandLine().getPathAt(2));
                }
                wcClient.doSetRevisionProperty(tgt, revision, propertyName, propertyValue, force, new ISVNPropertyHandler() {
                            public void handleProperty(File path, SVNPropertyData property) throws SVNException {
                            }
                            public void handleProperty(SVNURL url, SVNPropertyData property) throws SVNException {
                                out.println("property '" + propertyName +"' set on repository revision " + url);
                            }
                            public void handleProperty(long revision, SVNPropertyData property) throws SVNException {
                            }
                });
            }

        } else {
            for (int i = pathIndex; i < getCommandLine().getPathCount(); i++) {
                final String absolutePath = getCommandLine().getPathAt(i);
                if (!recursive) {
                    wcClient.doSetProperty(new File(absolutePath), propertyName, propertyValue, force, recursive, new ISVNPropertyHandler() {
                        public void handleProperty(File path, SVNPropertyData property) throws SVNException {
                            out.println("property '" + propertyName + "' set on '" + SVNFormatUtil.formatPath(path) + "'");
                        }
                        public void handleProperty(SVNURL url, SVNPropertyData property) throws SVNException {
                        }
                        public void handleProperty(long revision, SVNPropertyData property) throws SVNException {
                        }

                    });
                } else {
                    final boolean wasSet[] = new boolean[] {false};
                    wcClient.doSetProperty(new File(absolutePath), propertyName, propertyValue, force, recursive, new ISVNPropertyHandler() {
                        public void handleProperty(File path, SVNPropertyData property) throws SVNException {
                           wasSet[0] = true;
                        }
                        public void handleProperty(SVNURL url, SVNPropertyData property) throws SVNException {
                        }
                        public void handleProperty(long revision, SVNPropertyData property) throws SVNException {
                        }
                    });
                    if (wasSet[0]) {
                        out.println("property '" + propertyName + "' set (recursively) on '" + absolutePath + "'");
                    }
                }
            }
            if (getCommandLine().getPathCount() == 2 && getCommandLine().hasURLs()) {
                err.println("Propset is not supported for target '" + getCommandLine().getURL(0) + "'");
                System.exit(1);
            }
        }
    }
}
