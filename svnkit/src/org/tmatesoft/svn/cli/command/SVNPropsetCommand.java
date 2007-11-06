/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
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
import java.util.ArrayList;
import java.util.Collection;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNFormatUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.ISVNPropertyHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class SVNPropsetCommand extends SVNCommand implements ISVNEventHandler {
    private boolean myIsQuiet;
    private boolean myIsRecursive;
    private PrintStream myErrStream;
    
    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

    public final void run(final PrintStream out, PrintStream err) throws SVNException {
        myErrStream = err;

        final String propertyName = getCommandLine().getPathAt(0);
        String propertyValue = getCommandLine().getPathAt(1);
        
        String changelistName = (String) getCommandLine().getArgumentValue(SVNArgument.CHANGELIST); 
        if (changelistName != null) {
/*            changelist = SVNChangeList.create(changelistName, new File(".").getAbsoluteFile());
            changelist.setOptions(getClientManager().getOptions());
            changelist.setRepositoryPool(getClientManager().getRepositoryPool());
            if (changelist.getPaths() == null || changelist.getPathsCount() == 0) {
                SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                                    "no such changelist ''{0}''", changelistName); 
                SVNErrorManager.error(error);
            }
*/            
        }
        
        SVNDepth depth = SVNDepth.UNKNOWN;
        if (getCommandLine().hasArgument(SVNArgument.RECURSIVE)) {
            depth = SVNDepth.fromRecurse(true);
        }
        String depthStr = (String) getCommandLine().getArgumentValue(SVNArgument.DEPTH);
        if (depthStr != null) {
            depth = SVNDepth.fromString(depthStr);
        }
        if (depth == SVNDepth.UNKNOWN) {
            depth = SVNDepth.EMPTY;
        }
        
        myIsRecursive = SVNDepth.recurseFromDepth(depth);
        boolean force = getCommandLine().hasArgument(SVNArgument.FORCE);
        boolean revProps = getCommandLine().hasArgument(SVNArgument.REV_PROP);
        myIsQuiet = getCommandLine().hasArgument(SVNArgument.QUIET);

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
                SVNFileUtil.closeFile(os);
                SVNFileUtil.closeFile(is);
            }
            propertyValue = os.toString();
            pathIndex = 1;
        }

        Collection targets = new ArrayList(getCommandLine().getPathCount());
        for (int i = pathIndex; i < getCommandLine().getPathCount(); i++) {
            targets.add(new File(getCommandLine().getPathAt(i)).getAbsoluteFile());
        }
        File[] paths = (File[]) targets.toArray(new File[targets.size()]);
//        SVNPathList pathList = SVNPathList.create(paths, SVNRevision.UNDEFINED);
//        SVNCompositePathList combinedPathList = SVNCompositePathList.create(pathList, changelist, false);

        SVNRevision revision = SVNRevision.UNDEFINED;
        if (getCommandLine().hasArgument(SVNArgument.REVISION)) {
            revision = SVNRevision.parse((String) getCommandLine().getArgumentValue(SVNArgument.REVISION));
        }

        SVNWCClient wcClient = getClientManager().getWCClient();

        if (revProps) {
            if (getCommandLine().hasURLs()) {
                wcClient.doSetRevisionProperty(SVNURL.parseURIEncoded(getCommandLine().getURL(0)),
                        revision, propertyName, propertyValue, force, new PropertyHandler(out, propertyName));
            } else {
                File[] combinedPaths = null;//combinedPathList != null ? combinedPathList.getPaths() : null; 
                File tgt = combinedPaths != null ? combinedPaths[0] : new File(".").getAbsoluteFile();
                wcClient.doSetRevisionProperty(tgt, revision, propertyName, propertyValue, 
                                               force, new PropertyHandler(out, propertyName));
            }
        } else if (revision != null && revision != SVNRevision.UNDEFINED) {
            SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Cannot specify revision for deleting versioned property ''{0}''", propertyName);
            SVNErrorManager.error(error);
        } else {
            PropertyHandler handler = new PropertyHandler(out, propertyName);
//            wcClient.doSetProperty(combinedPathList, propertyName, propertyValue, force, myIsRecursive, handler);             
            handler.handlePendingFile();
/*            if ((combinedPathList == null || combinedPathList.getPathsCount() == 0) && getCommandLine().hasURLs()) {
                err.println("Setting property on non-local target '" + getCommandLine().getURL(0) + "' is not supported");
                System.exit(1);
            }
*/            
        }
    }

    public void checkCancelled() throws SVNCancelException {
    }

    public void handleEvent(SVNEvent event, double progress) throws SVNException {
        if (event.getErrorMessage() != null) {
            handleWarning(event.getErrorMessage(), new SVNErrorCode[] {SVNErrorCode.UNVERSIONED_RESOURCE, SVNErrorCode.ENTRY_NOT_FOUND}, myErrStream);
        }
    }

    private class PropertyHandler implements ISVNPropertyHandler {
        private PrintStream myOutput;
        private File myCurrentFile;
        private String myPropertyName;
        
        public PropertyHandler(PrintStream out, String propName) {
            myOutput = out;
            myPropertyName = propName;
        }
        
        public void handleProperty(File path, SVNPropertyData property) throws SVNException {
            if (!myIsQuiet) {
                if (myIsRecursive) {
                    if (myCurrentFile != null) {
                        String rootPath = myCurrentFile.getAbsolutePath();
                        if (path.getAbsolutePath().indexOf(rootPath) == -1) {
                            myOutput.println("property '" + property.getName() + "' set (recursively) on '" + SVNFormatUtil.formatPath(myCurrentFile) + "'");
                            myCurrentFile = path;
                        }
                    } else {
                        myCurrentFile = path;
                    }
                } else {
                    myOutput.println("property '" + property.getName() + "' set on '" + SVNFormatUtil.formatPath(path) + "'");                    
                }
            }            
        }

        public void handleProperty(long revision, SVNPropertyData property) throws SVNException {
            if (!myIsQuiet) {
                myOutput.println("property '" + property.getName() +"' set on repository revision " + revision);
            }
        }

        public void handlePendingFile() {
            if (!myIsQuiet) {
                if (myIsRecursive && myCurrentFile != null) {
                    myOutput.println("property '" + myPropertyName + "' set on '" + SVNFormatUtil.formatPath(myCurrentFile) + "'");                    
                    myCurrentFile = null;
                }
            }
        }

        public void handleProperty(SVNURL url, SVNPropertyData property) throws SVNException {
        }
        
    }

}
