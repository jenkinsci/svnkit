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
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.ISVNConflictHandler;
import org.tmatesoft.svn.core.wc.SVNConflictAction;
import org.tmatesoft.svn.core.wc.SVNConflictChoice;
import org.tmatesoft.svn.core.wc.SVNConflictDescription;
import org.tmatesoft.svn.core.wc.SVNConflictReason;
import org.tmatesoft.svn.core.wc.SVNConflictResult;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNMergeFileSet;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNCommandLineConflictHandler implements ISVNConflictHandler {
    private SVNConflictAcceptPolicy myAccept;
    private SVNCommandEnvironment mySVNEnvironment;
    private boolean myIsExternalFailed;
    
    public SVNCommandLineConflictHandler(SVNConflictAcceptPolicy accept, SVNCommandEnvironment environment) {
        myAccept = accept;
        mySVNEnvironment = environment;
    }
    
    public SVNConflictResult handleConflict(SVNConflictDescription conflictDescription) throws SVNException {
        SVNMergeFileSet files = conflictDescription.getMergeFiles();
        if (myAccept == SVNConflictAcceptPolicy.POSTPONE) {
            return new SVNConflictResult(SVNConflictChoice.POSTPONE, null);
        } else if (myAccept == SVNConflictAcceptPolicy.BASE) {
            return new SVNConflictResult(SVNConflictChoice.BASE, null);
        } else if (myAccept == SVNConflictAcceptPolicy.MINE) {
            return new SVNConflictResult(SVNConflictChoice.MINE, null);
        } else if (myAccept == SVNConflictAcceptPolicy.THEIRS) {
            return new SVNConflictResult(SVNConflictChoice.THEIRS, null);
        } else if (myAccept == SVNConflictAcceptPolicy.EDIT) {
            if (files.getResultFile() != null) {
                if (myIsExternalFailed) {
                    return new SVNConflictResult(SVNConflictChoice.POSTPONE, null);
                }
                
                try {
                    SVNCommandUtil.editFileExternally(mySVNEnvironment, mySVNEnvironment.getEditorCommand(), 
                            files.getResultFile().getAbsolutePath());
                } catch (SVNException svne) {
                    if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.CL_NO_EXTERNAL_EDITOR) {
                        mySVNEnvironment.getErr().println(svne.getErrorMessage().getMessage() != null ? 
                                svne.getErrorMessage().getMessage() : "No editor found, leaving all conflicts.");
                        myIsExternalFailed = true;
                    } else if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.EXTERNAL_PROGRAM) {
                        mySVNEnvironment.getErr().println(svne.getErrorMessage().getMessage() != null ? 
                                svne.getErrorMessage().getMessage() : "Error running editor, leaving all conflicts.");
                        myIsExternalFailed = true;
                    } else {
                        throw svne;
                    }
                }
                return new SVNConflictResult(SVNConflictChoice.MERGED, null);
            }
        } else if (myAccept == SVNConflictAcceptPolicy.LAUNCH) {
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
                        mySVNEnvironment.getErr().println(svne.getErrorMessage().getMessage() != null ? 
                                svne.getErrorMessage().getMessage() : "No merge tool found.");
                        myIsExternalFailed = true;
                    } else if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.EXTERNAL_PROGRAM) {
                        mySVNEnvironment.getErr().println(svne.getErrorMessage().getMessage() != null ? 
                                svne.getErrorMessage().getMessage() : "Error running merge tool.");
                        myIsExternalFailed = true;
                    } else {
                        throw svne;
                    }
                }
                return new SVNConflictResult(SVNConflictChoice.MERGED, null);
            }
        }
        
        SVNConflictChoice choice = SVNConflictChoice.POSTPONE;
        if ((conflictDescription.getNodeKind() == SVNNodeKind.FILE && 
                conflictDescription.getConflictAction() == SVNConflictAction.EDIT && 
                conflictDescription.getConflictReason() == SVNConflictReason.EDITED) || 
                conflictDescription.isPropertyConflict()) {
            
            boolean performedEdit = false;
            boolean diffAllowed = false;
            File path = files.getTargetFile();
            if (conflictDescription.isPropertyConflict()) {
                String message = "Property conflict for ''{0}'' discovered on ''{1}''.";
                message = MessageFormat.format(message, new Object[] { conflictDescription.getPropertyName(), 
                        path });
                mySVNEnvironment.getErr().println(message);
                
                if ((files.getLocalFile() == null && files.getRepositoryFile() != null) || 
                        (files.getLocalFile() != null && files.getRepositoryFile() == null)) {
                    if (files.getLocalFile() != null) {
                        String myVal = SVNFileUtil.readFile(files.getLocalFile());
                        message = MessageFormat.format("They want to delete the property, you want to change the value to ''{0}''.", 
                                new Object[] { myVal });
                        mySVNEnvironment.getErr().println(message);
                    }
                } else {
                    String reposVal = SVNFileUtil.readFile(files.getRepositoryFile());
                    message = MessageFormat.format("They want to change the property value to ''{0}'', you want to delete the property.", 
                            new Object[] { reposVal });
                    mySVNEnvironment.getErr().println(message);
                }
            } else {
                String message = "Conflict discovered in ''{0}''.";
                message = MessageFormat.format(message, new Object[] { path });
                mySVNEnvironment.getErr().println(message);
            }
            
            if ((files.getResultFile() != null && files.getBaseFile() != null) || (files.getBaseFile() != null &&
                    files.getLocalFile() != null && files.getRepositoryFile() != null)) {
                diffAllowed = true;
            }
            
            while (true) {
                String message = "Select: (p)ostpone";
                if (diffAllowed) {
                    message += ", (d)iff, (e)dit"; 
                } else {
                    message += ", (m)ine, (t)heirs";
                }
                if (performedEdit) {
                    message += ", (r)esolved";
                }
                message += ", (h)elp for more options : ";
                String answer = SVNCommandUtil.prompt(message);
                if ("h".equalsIgnoreCase(answer) || "?".equals(answer)) {
                    mySVNEnvironment.getErr().println("  (p)ostpone - mark the conflict to be resolved later");
                    mySVNEnvironment.getErr().println("  (d)iff     - show all changes made to merged file");
                    mySVNEnvironment.getErr().println("  (e)dit     - change merged file in an editor");
                    mySVNEnvironment.getErr().println("  (r)esolved - accept merged version of file");
                    mySVNEnvironment.getErr().println("  (t)heirs   - accept their version of file");
                    mySVNEnvironment.getErr().println("  (l)aunch   - use third-party tool to resolve conflict");
                    mySVNEnvironment.getErr().println("  (h)elp     - show this list");
                    mySVNEnvironment.getErr().println();
                }
                if ("p".equalsIgnoreCase(answer)) {
                    choice = SVNConflictChoice.POSTPONE;
                    break;
                }
                if ("m".equalsIgnoreCase(answer)) {
                    choice = SVNConflictChoice.MINE;
                    break;
                }
                if ("t".equalsIgnoreCase(answer)) {
                    choice = SVNConflictChoice.THEIRS;
                    break;
                }
                if ("d".equalsIgnoreCase(answer)) {
                    if (!diffAllowed) {
                        mySVNEnvironment.getErr().println("Invalid option; there's no merged version to diff.");
                        mySVNEnvironment.getErr().println();
                        continue;
                    }
                    
                    File path1 = null;
                    File path2 = null;
                    if (files.getResultFile() != null && files.getBaseFile() != null) {
                        path1 = files.getBaseFile();
                        path2 = files.getResultFile();
                    } else {
                        path1 = files.getRepositoryFile();
                        path2 = files.getLocalFile();
                    }
                    
                    DefaultSVNCommandLineDiffGenerator diffGenerator = new DefaultSVNCommandLineDiffGenerator(path1, path2);
                    diffGenerator.setDiffOptions(new SVNDiffOptions(false, false, true));
                    diffGenerator.displayFileDiff("", path1, path2, null, null, null, null, System.out);
                    performedEdit = true;
                }
                if ("e".equalsIgnoreCase(answer)) {
                    if (files.getResultFile() != null) {
                        try {
                            SVNCommandUtil.editFileExternally(mySVNEnvironment, mySVNEnvironment.getEditorCommand(), 
                                    files.getResultPath());
                            performedEdit = true;
                        } catch (SVNException svne) {
                            if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.CL_NO_EXTERNAL_EDITOR) {
                                mySVNEnvironment.getErr().println(svne.getErrorMessage().getMessage() != null ? 
                                        svne.getErrorMessage().getMessage() : "No editor found.");
                            } else if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.EXTERNAL_PROGRAM) {
                                mySVNEnvironment.getErr().println(svne.getErrorMessage().getMessage() != null ? 
                                        svne.getErrorMessage().getMessage() : "Error running editor.");
                            } else {
                                throw svne;
                            }
                        }
                    } else {
                        mySVNEnvironment.getErr().println("Invalid option; there's no merged version to edit.");
                        mySVNEnvironment.getErr().println();
                    }
                }
                if ("l".equalsIgnoreCase(answer)) {
                    if (files.getBaseFile() != null && files.getLocalFile() != null && 
                            files.getRepositoryFile() != null && files.getResultFile() != null) {
                        try {
                            SVNCommandUtil.mergeFileExternally(mySVNEnvironment, files.getBasePath(), 
                                    files.getRepositoryPath(), files.getLocalPath(), files.getResultPath());
                            performedEdit = true;
                        } catch (SVNException svne) {
                            if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.CL_NO_EXTERNAL_MERGE_TOOL) {
                                mySVNEnvironment.getErr().println(svne.getErrorMessage().getMessage() != null ? 
                                        svne.getErrorMessage().getMessage() : "No merge tool found.");
                                myIsExternalFailed = true;
                            } else if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.EXTERNAL_PROGRAM) {
                                mySVNEnvironment.getErr().println(svne.getErrorMessage().getMessage() != null ? 
                                        svne.getErrorMessage().getMessage() : "Error running merge tool.");
                                myIsExternalFailed = true;
                            } else {
                                throw svne;
                            }
                        }
                    } else {
                        mySVNEnvironment.getErr().println("Invalid option.");
                        mySVNEnvironment.getErr().println();
                    }
                }
                if ("r".equalsIgnoreCase(answer)) {
                    if (performedEdit) {
                        choice = SVNConflictChoice.MERGED;
                        break;
                    } 
                    mySVNEnvironment.getErr().println("Invalid option.");
                    mySVNEnvironment.getErr().println();
                }
            }
        } else if (conflictDescription.getConflictAction() == SVNConflictAction.ADD && 
                conflictDescription.getConflictReason() == SVNConflictReason.OBSTRUCTED) {
            String message = "Conflict discovered when trying to add ''{0}''.";
            message = MessageFormat.format(message, new Object[] { files.getTargetFile() });
            mySVNEnvironment.getErr().println(message);
            mySVNEnvironment.getErr().println("An object of the same name already exists.");
            
            String prompt = "Select: (p)ostpone, (m)ine, (t)heirs, (h)elp :";
            while (true) {
                String answer = SVNCommandUtil.prompt(prompt);
                if ("h".equalsIgnoreCase(answer) || "?".equals(answer)) {
                    mySVNEnvironment.getErr().println("  (p)ostpone - resolve the conflict later");
                    mySVNEnvironment.getErr().println("  (m)ine     - accept pre-existing item");
                    mySVNEnvironment.getErr().println("  (t)heirs   - accept incoming item");
                    mySVNEnvironment.getErr().println("  (h)elp     - show this list");
                    mySVNEnvironment.getErr().println();
                }
                if ("p".equalsIgnoreCase(answer)) {
                    choice = SVNConflictChoice.POSTPONE;
                    break;
                }
                if ("m".equalsIgnoreCase(answer)) {
                    choice = SVNConflictChoice.MINE;
                    break;
                }
                if ("t".equalsIgnoreCase(answer)) {
                    choice = SVNConflictChoice.THEIRS;
                    break;
                }
            }
        } else {
            choice = SVNConflictChoice.POSTPONE;
        }
        
        return new SVNConflictResult(choice, null);
    }

}
