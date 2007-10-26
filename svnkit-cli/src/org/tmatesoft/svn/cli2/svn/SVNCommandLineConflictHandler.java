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

import java.io.File;
import java.text.MessageFormat;

import org.tmatesoft.svn.cli2.SVNCommandUtil;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNMergerAction;
import org.tmatesoft.svn.core.wc.ISVNConflictHandler;
import org.tmatesoft.svn.core.wc.SVNConflictAction;
import org.tmatesoft.svn.core.wc.SVNConflictChoice;
import org.tmatesoft.svn.core.wc.SVNConflictDescription;
import org.tmatesoft.svn.core.wc.SVNConflictReason;
import org.tmatesoft.svn.core.wc.SVNConflictResult;
import org.tmatesoft.svn.core.wc.SVNMergeFileSet;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNCommandLineConflictHandler implements ISVNConflictHandler {
    private SVNWCAccept myAccept;
    private SVNCommandEnvironment mySVNEnvironment;
    private boolean myIsExternalFailed;
    
    public SVNCommandLineConflictHandler(SVNWCAccept accept) {
        myAccept = accept;
    }
    
    public SVNConflictResult handleConflict(SVNConflictDescription conflictDescription) throws SVNException {
        SVNMergeFileSet files = conflictDescription.getMergeFiles();
        if (myAccept == SVNWCAccept.POSTPONE) {
            return new SVNConflictResult(SVNConflictChoice.POSTPONE, null);
        } else if (myAccept == SVNWCAccept.BASE) {
            return new SVNConflictResult(SVNConflictChoice.BASE, null);
        } else if (myAccept == SVNWCAccept.MINE) {
            return new SVNConflictResult(SVNConflictChoice.MINE, null);
        } else if (myAccept == SVNWCAccept.THEIRS) {
            return new SVNConflictResult(SVNConflictChoice.THEIRS, null);
        } else if (myAccept == SVNWCAccept.EDIT) {
            if (files.getResultFile() != null) {
                if (myIsExternalFailed) {
                    return new SVNConflictResult(SVNConflictChoice.POSTPONE, null);
                }
                
                try {
                    SVNCommandUtil.editFileExternally(mySVNEnvironment, mySVNEnvironment.getEditorCommand(), 
                            files.getResultFile().getAbsolutePath());
                } catch (SVNException svne) {
                    if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.CL_NO_EXTERNAL_EDITOR) {
                        mySVNEnvironment.getOut().println(svne.getErrorMessage().getMessage() != null ? 
                                svne.getErrorMessage().getMessage() : "No editor found, leaving all conflicts.");
                        myIsExternalFailed = true;
                    } else if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.EXTERNAL_PROGRAM) {
                        mySVNEnvironment.getOut().println(svne.getErrorMessage().getMessage() != null ? 
                                svne.getErrorMessage().getMessage() : "Error running editor, leaving all conflicts.");
                        myIsExternalFailed = true;
                    } else {
                        throw svne;
                    }
                }
                return new SVNConflictResult(SVNConflictChoice.MERGED, null);
            }
        } else if (myAccept == SVNWCAccept.LAUNCH) {
            if (files.getBaseFile() != null && files.getLocalFile() != null && files.getRepositoryFile() != null &&
                    files.getResultFile() != null) {
                if (myIsExternalFailed) {
                    return new SVNConflictResult(SVNConflictChoice.POSTPONE, null);
                }
                
                try {
                    SVNCommandUtil.mergeFileExternally(mySVNEnvironment, files.getBasePath(), files.getRepositoryPath(), 
                            files.getLocalPath(), files.getResultPath());
                } catch (SVNException svne) {
                    if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.CL_NO_EXTERNAL_MERGE_TOOL) {
                        mySVNEnvironment.getOut().println(svne.getErrorMessage().getMessage() != null ? 
                                svne.getErrorMessage().getMessage() : "No merge tool found.");
                        myIsExternalFailed = true;
                    } else if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.EXTERNAL_PROGRAM) {
                        mySVNEnvironment.getOut().println(svne.getErrorMessage().getMessage() != null ? 
                                svne.getErrorMessage().getMessage() : "Error running merge tool.");
                        myIsExternalFailed = true;
                    } else {
                        throw svne;
                    }
                }
                return new SVNConflictResult(SVNConflictChoice.MERGED, null);
            }
        }
        
        if ((conflictDescription.getNodeKind() == SVNNodeKind.FILE && 
                conflictDescription.getConflictAction() == SVNConflictAction.EDIT && 
                conflictDescription.getConflictReason() == SVNConflictReason.EDITED) || 
                conflictDescription.isPropertyConflict()) {
            
            boolean performedEdit = false;
            File path = files.getTargetFile();
            if (conflictDescription.isPropertyConflict()) {
                String message = "Property conflict for ''{0}'' discovered on ''{1}''.";
                message = MessageFormat.format(message, new Object[] { conflictDescription.getPropertyName(), path });
                if (files.getResultFile() != null) {
                    if (files.getLocalFile() != null) {
                        
                    }
                    
                }
                
                
            } else {
                String message = "Conflict discovered in ''{0}''.";
                message = MessageFormat.format(message, new Object[] { path });
                mySVNEnvironment.getOut().println(message);
            }
            
            
        }
        
       
        
        return null;
    }

}
