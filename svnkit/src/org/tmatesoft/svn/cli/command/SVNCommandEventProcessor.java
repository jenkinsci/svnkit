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
import java.io.PrintStream;

import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNFormatUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNStatusType;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNCommandEventProcessor implements ISVNEventHandler {

    private boolean myIsExternal;
    private boolean myIsChanged;
    private boolean myIsExternalChanged;
    private boolean myIsCheckout;
    private boolean myIsExport;
    private boolean myIsDelta;

    private final PrintStream myPrintStream;
    private PrintStream myErrStream;

    public SVNCommandEventProcessor(PrintStream out, PrintStream err, boolean checkout) {
        this(out, err, checkout, false);
    }

    public SVNCommandEventProcessor(PrintStream out, PrintStream err, boolean checkout, boolean export) {
        myPrintStream = out;
        myErrStream = err;
        myIsCheckout = checkout;
        myIsExport = export;
    }

    public void handleEvent(SVNEvent event, double progress) {
        String commitPath = null;
        if (event.getAction() == SVNEventAction.COMMIT_ADDED || event.getAction() == SVNEventAction.COMMIT_MODIFIED ||
                event.getAction() == SVNEventAction.COMMIT_DELETED || event.getAction() == SVNEventAction.COMMIT_REPLACED) {
            File root = new File(".");
            File file = event.getFile();
            try {
                if (root.getCanonicalFile().equals(file.getCanonicalFile()) || SVNPathUtil.isChildOf(root, file)) {
                    commitPath = SVNFormatUtil.formatPath(event.getFile());
                } else {
                    commitPath = event.getPath();
                    if ("".equals(commitPath)) {
                        commitPath = ".";
                    }
                }
            } catch (IOException e) {
                //
            }
        }
        if (event.getAction() == SVNEventAction.COMMIT_MODIFIED) {
            SVNCommand.println(myPrintStream, "Sending        " + commitPath);
        } else if (event.getAction() == SVNEventAction.COMMIT_DELETED) {
            SVNCommand.println(myPrintStream, "Deleting       " + commitPath);
        } else if (event.getAction() == SVNEventAction.COMMIT_REPLACED) {
            SVNCommand.println(myPrintStream, "Replacing      " + commitPath);
        } else if (event.getAction() == SVNEventAction.COMMIT_DELTA_SENT) {
            if (!myIsDelta) {
                SVNCommand.print(myPrintStream, "Transmitting file data ");
                myIsDelta = true;
            }
            SVNCommand.print(myPrintStream, ".");
        } else if (event.getAction() == SVNEventAction.COMMIT_ADDED) {
            String mimeType = event.getMimeType();
            if (SVNProperty.isBinaryMimeType(mimeType)) {
                SVNCommand.println(myPrintStream, "Adding  (bin)  " + commitPath);
            } else {
                SVNCommand.println(myPrintStream, "Adding         " + commitPath);
            }
        } else if (event.getAction() == SVNEventAction.REVERT) {
            SVNCommand.println(myPrintStream, "Reverted '" + SVNFormatUtil.formatPath(event.getFile()) + "'");
        } else if (event.getAction() == SVNEventAction.FAILED_REVERT) {
            SVNCommand.println(myPrintStream, "Failed to revert '" + SVNFormatUtil.formatPath(event.getFile()) + "' -- try updating instead.");
        } else if (event.getAction() == SVNEventAction.LOCKED) {
            String path = event.getPath();
            if (event.getFile() != null) {
                path = SVNFormatUtil.formatPath(event.getFile());
            }
            SVNLock lock = event.getLock();
            SVNCommand.println(myPrintStream, "'" + path + "' locked by user '" + lock.getOwner() + "'.");
        } else if (event.getAction() == SVNEventAction.UNLOCKED) {
            String path = event.getPath();
            if (event.getFile() != null) {
                path = SVNFormatUtil.formatPath(event.getFile());
            }
            SVNCommand.println(myPrintStream, "'" + path + "' unlocked.");
        } else if (event.getAction() == SVNEventAction.UNLOCK_FAILED) {
            SVNCommand.println(myErrStream, "error: " + event.getErrorMessage());
        } else if (event.getAction() == SVNEventAction.LOCK_FAILED) {
            SVNCommand.println(myErrStream, "error: " + event.getErrorMessage());
        } else if (event.getAction() == SVNEventAction.UPDATE_ADD) {
            if (myIsExternal) {
                myIsExternalChanged = true;
            } else {
                myIsChanged = true;
            }
            if (event.getContentsStatus() == SVNStatusType.CONFLICTED) {
                SVNCommand.println(myPrintStream, "C    " + SVNFormatUtil.formatPath(event.getFile()));
            } else {
                SVNCommand.println(myPrintStream, "A    " + SVNFormatUtil.formatPath(event.getFile()));
            }
        } else if (event.getAction() == SVNEventAction.UPDATE_DELETE) {
            if (myIsExternal) {
                myIsExternalChanged = true;
            } else {
                myIsChanged = true;
            }
            SVNCommand.println(myPrintStream, "D    " + SVNFormatUtil.formatPath(event.getFile()));
        } else if (event.getAction() == SVNEventAction.UPDATE_UPDATE) {
            StringBuffer sb = new StringBuffer();
            if (event.getNodeKind() != SVNNodeKind.DIR) {
                if (event.getContentsStatus() == SVNStatusType.CHANGED) {
                    sb.append("U");
                } else if (event.getContentsStatus() == SVNStatusType.CONFLICTED) {
                    sb.append("C");
                } else if (event.getContentsStatus() == SVNStatusType.MERGED) {
                    sb.append("G");
                } else {
                    sb.append(" ");
                }
            } else {
                sb.append(' ');
            }
            if (event.getPropertiesStatus() == SVNStatusType.CHANGED) {
                sb.append("U");
            } else if (event.getPropertiesStatus() == SVNStatusType.CONFLICTED) {
                sb.append("C");
            } else if (event.getPropertiesStatus() == SVNStatusType.MERGED) {
                sb.append("G");
            } else {
                sb.append(" ");
            }
            if (sb.toString().trim().length() != 0) {
                if (myIsExternal) {
                    myIsExternalChanged = true;
                } else {
                    myIsChanged = true;
                }
            }
            if (event.getLockStatus() == SVNStatusType.LOCK_UNLOCKED) {
                sb.append("B");
            } else {
                sb.append(" ");
            }
            if (sb.toString().trim().length() > 0) {
                SVNCommand.println(myPrintStream, sb.toString() + "  " + SVNFormatUtil.formatPath(event.getFile()));
            }
        } else if (event.getAction() == SVNEventAction.UPDATE_COMPLETED) {
            if (!myIsExternal) {
                if (myIsChanged) {
                    if (myIsCheckout) {
                        SVNCommand.println(myPrintStream, "Checked out revision " + event.getRevision() + ".");
                    } else if (myIsExport) {
                        SVNCommand.println(myPrintStream, "Export complete.");
                    } else {
                        SVNCommand.println(myPrintStream, "Updated to revision " + event.getRevision() + ".");
                    }
                } else {
                    if (myIsCheckout) {
                        SVNCommand.println(myPrintStream, "Checked out revision " + event.getRevision() + ".");
                    } else if (myIsExport) {
                        SVNCommand.println(myPrintStream, "Export complete.");
                    } else {
                        SVNCommand.println(myPrintStream, "At revision " + event.getRevision() + ".");
                    }
                }
            } else {
                if (myIsExternalChanged) {
                    if (myIsCheckout) {
                        SVNCommand.println(myPrintStream, "Checked out external at revision " + event.getRevision() + ".");
                    } else if (myIsExport) {
                        SVNCommand.println(myPrintStream, "Export complete.");
                    } else {
                        SVNCommand.println(myPrintStream, "Updated external to revision " + event.getRevision() + ".");
                    }
                } else {
                    SVNCommand.println(myPrintStream, "External at revision " + event.getRevision() + ".");
                }
                SVNCommand.println(myPrintStream);
                myIsExternalChanged = false;
                myIsExternal = false;
            }
        } else if (event.getAction() == SVNEventAction.UPDATE_EXTERNAL) {
            SVNCommand.println(myPrintStream);
            String path = event.getPath().replace('/', File.separatorChar);
            if (myIsCheckout) {
                SVNCommand.println(myPrintStream, "Fetching external item into '" + path + "'");
            } else {
                SVNCommand.println(myPrintStream, "Updating external item at '" + path + "'");
            }
            myIsExternal = true;
        } else if (event.getAction() == SVNEventAction.STATUS_EXTERNAL) {
            SVNCommand.println(myPrintStream);
            String path = event.getPath().replace('/', File.separatorChar);
            SVNCommand.println(myPrintStream, "Performing status on external item at '" + path + "'");
            myIsExternal = true;
        } else if (event.getAction() == SVNEventAction.RESTORE) {
            SVNCommand.println(myPrintStream, "Restored '" + SVNFormatUtil.formatPath(event.getFile()) + "'");
        } else if (event.getAction() == SVNEventAction.ADD) {
            if (SVNProperty.isBinaryMimeType(event.getMimeType())) {
                SVNCommand.println(myPrintStream, "A  (bin)  " + SVNFormatUtil.formatPath(event.getFile()));
            } else {
                SVNCommand.println(myPrintStream, "A         " + SVNFormatUtil.formatPath(event.getFile()));
            }
        } else if (event.getAction() == SVNEventAction.DELETE) {
            SVNCommand.println(myPrintStream,     "D         " + SVNFormatUtil.formatPath(event.getFile()));
        } else if (event.getAction() == SVNEventAction.SKIP) {
            SVNCommand.println(myPrintStream, "Skipped '" + SVNFormatUtil.formatPath(event.getFile()) + "'");
        } else if (event.getAction() == SVNEventAction.RESOLVED) {
            SVNCommand.println(myPrintStream, "Resolved conflicted state of '" + SVNFormatUtil.formatPath(event.getFile()) + "'");
        } else if (event.getAction() == SVNEventAction.STATUS_COMPLETED) {
            SVNCommand.println(myPrintStream, "Status against revision: " + SVNFormatUtil.formatString(Long.toString(event.getRevision()), 6, false));
        }
    }

    public void checkCancelled() throws SVNCancelException {
    }
}