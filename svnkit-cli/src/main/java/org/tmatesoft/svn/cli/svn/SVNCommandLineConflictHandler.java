/*
 * ====================================================================
 * Copyright (c) 2004-2012 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.cli.svn;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.MessageFormat;

import org.tmatesoft.svn.cli.SVNCommandUtil;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.wc.FSMergerBySequence;
import org.tmatesoft.svn.core.internal.wc.SVNDiffConflictChoiceStyle;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.ISVNConflictHandler;
import org.tmatesoft.svn.core.wc.SVNConflictAction;
import org.tmatesoft.svn.core.wc.SVNConflictChoice;
import org.tmatesoft.svn.core.wc.SVNConflictDescription;
import org.tmatesoft.svn.core.wc.SVNConflictReason;
import org.tmatesoft.svn.core.wc.SVNConflictResult;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNMergeFileSet;
import org.tmatesoft.svn.util.SVNLogType;

import de.regnis.q.sequence.line.QSequenceLineRAData;
import de.regnis.q.sequence.line.QSequenceLineRAFileData;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2.0
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
        if (conflictDescription.isTreeConflict()) {
            return null;
        }
        
        SVNMergeFileSet files = conflictDescription.getMergeFiles();
        if (myAccept == SVNConflictAcceptPolicy.POSTPONE) {
            return new SVNConflictResult(SVNConflictChoice.POSTPONE, null);
        } else if (myAccept == SVNConflictAcceptPolicy.BASE) {
            return new SVNConflictResult(SVNConflictChoice.BASE, null);
        } else if (myAccept == SVNConflictAcceptPolicy.WORKING) {
            return new SVNConflictResult(SVNConflictChoice.MERGED, null);
        } else if (myAccept == SVNConflictAcceptPolicy.MINE_CONFLICT) {
            return new SVNConflictResult(SVNConflictChoice.MINE_CONFLICT, null);
        } else if (myAccept == SVNConflictAcceptPolicy.THEIRS_CONFLICT) {
            return new SVNConflictResult(SVNConflictChoice.THEIRS_CONFLICT, null);
        } else if (myAccept == SVNConflictAcceptPolicy.MINE_FULL) {
            return new SVNConflictResult(SVNConflictChoice.MINE_FULL, null);
        } else if (myAccept == SVNConflictAcceptPolicy.THEIRS_FULL) {
            return new SVNConflictResult(SVNConflictChoice.THEIRS_FULL, null);
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
                        String message = svne.getErrorMessage().getMessageTemplate() != null ? svne.getErrorMessage().getMessage() : 
                            "Error running editor, leaving all conflicts.";
                        if (message.startsWith("svn: ")) {
                            //hack: use the original message template without any prefixes (like 'svn:', 'svn:warning') 
                            //added to make update test 42 pass
                            message = message.substring("svn: ".length());
                        }
                        mySVNEnvironment.getErr().println(message);
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

                boolean[] remainsInConflict = { false };
                try {
                    SVNCommandUtil.mergeFileExternally(mySVNEnvironment, files.getBaseFile().getAbsolutePath(), 
                            files.getRepositoryFile().getAbsolutePath(), files.getLocalFile().getAbsolutePath(), 
                            files.getResultFile().getAbsolutePath(), files.getWCPath(), remainsInConflict);
                } catch (SVNException svne) {
                    if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.CL_NO_EXTERNAL_MERGE_TOOL) {
                        mySVNEnvironment.getErr().println(svne.getErrorMessage().getMessage() != null ? 
                                svne.getErrorMessage().getMessage() : "No merge tool found.");
                        myIsExternalFailed = true;
                    } else if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.EXTERNAL_PROGRAM) {
                        mySVNEnvironment.getErr().println(svne.getErrorMessage().getMessage() != null ? 
                                svne.getErrorMessage().getMessage() : "Error running merge tool.");
                        myIsExternalFailed = true;
                    }
                    throw svne;
                }

                if (remainsInConflict[0]) {
                    return new SVNConflictResult(SVNConflictChoice.POSTPONE, null);
                }
                 
                return new SVNConflictResult(SVNConflictChoice.MERGED, null);
            }
        }
        
        boolean saveMerged = false;
        SVNConflictChoice choice = SVNConflictChoice.POSTPONE;
        if ((conflictDescription.getNodeKind() == SVNNodeKind.FILE && 
                conflictDescription.getConflictAction() == SVNConflictAction.EDIT && 
                conflictDescription.getConflictReason() == SVNConflictReason.EDITED) || 
                conflictDescription.isPropertyConflict()) {
            
            boolean performedEdit = false;
            boolean diffAllowed = false;
            boolean knowsSmth = false;
            String path = mySVNEnvironment.getRelativePath(files.getWCFile());
            path = SVNCommandUtil.getLocalPath(path);

            if (conflictDescription.isPropertyConflict()) {
                String message = "Conflict for property ''{0}'' discovered on ''{1}''.";
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
                    } else {
                        String reposVal = SVNFileUtil.readFile(files.getRepositoryFile());
                        message = MessageFormat.format("They want to change the property value to ''{0}'', you want to delete the property.", 
                                new Object[] { reposVal });
                        mySVNEnvironment.getErr().println(message);
                    }
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
                String message = "Select: (p) postpone";
                if (diffAllowed) {
                    message += ", (df) diff-full, (e) edit";
                    
                    if (knowsSmth) {
                        message += ", (r) resolved";
                    }
                    
                    if (!files.isBinary() && !conflictDescription.isPropertyConflict()) {
                        message += ",\n        (mc) mine-conflict, (tc) theirs-conflict";
                    }
                } else {
                    if (knowsSmth) {
                        message += ", (r) resolved";
                    }
                    message += ",\n        (mf) mine-full, (tf) theirs-full";
                }
                
                message += ",\n        (s) show all options: ";
                
                String answer = SVNCommandUtil.prompt(message, mySVNEnvironment);
                
                if ("s".equals(answer)) {
                    mySVNEnvironment.getErr().println();
                    mySVNEnvironment.getErr().println("  (e)  edit             - change merged file in an editor");
                    mySVNEnvironment.getErr().println("  (df) diff-full        - show all changes made to merged file");
                    mySVNEnvironment.getErr().println("  (r)  resolved         - accept merged version of file");
                    mySVNEnvironment.getErr().println();
                    mySVNEnvironment.getErr().println("  (dc) display-conflict - show all conflicts (ignoring merged version)");
                    mySVNEnvironment.getErr().println("  (mc) mine-conflict    - accept my version for all conflicts (same)");
                    mySVNEnvironment.getErr().println("  (tc) theirs-conflict  - accept their version for all conflicts (same)");
                    mySVNEnvironment.getErr().println();
                    mySVNEnvironment.getErr().println("  (mf) mine-full        - accept my version of entire file (even non-conflicts)");
                    mySVNEnvironment.getErr().println("  (tf) theirs-full      - accept their version of entire file (same)");
                    mySVNEnvironment.getErr().println();
                    mySVNEnvironment.getErr().println("  (p)  postpone         - mark the conflict to be resolved later");
                    mySVNEnvironment.getErr().println("  (l)  launch           - launch external tool to resolve conflict");
                    mySVNEnvironment.getErr().println("  (s)  show all         - show this list");
                    mySVNEnvironment.getErr().println();
                } else  if ("p".equals(answer)) {
                    choice = SVNConflictChoice.POSTPONE;
                    break;
                } else if ("mc".equals(answer)) {
                    if (files.isBinary()) {
                        mySVNEnvironment.getErr().println("Invalid option; cannot choose based on conflicts in a binary file.");
                        mySVNEnvironment.getErr().println();
                        continue;
                    } else if (conflictDescription.isPropertyConflict()) {
                        mySVNEnvironment.getErr().println("Invalid option; cannot choose based on conflicts for properties.");
                        mySVNEnvironment.getErr().println();
                        continue;
                    }
                    choice = SVNConflictChoice.MINE_CONFLICT;
                    if (performedEdit) {
                        saveMerged = true;
                    }
                    break;
                } else if ("tc".equals(answer)) {
                    if (files.isBinary()) {
                        mySVNEnvironment.getErr().println("Invalid option; cannot choose based on conflicts in a binary file.");
                        mySVNEnvironment.getErr().println();
                        continue;
                    } else if (conflictDescription.isPropertyConflict()) {
                        mySVNEnvironment.getErr().println("Invalid option; cannot choose based on conflicts for properties.");
                        mySVNEnvironment.getErr().println();
                        continue;
                    }
                    choice = SVNConflictChoice.THEIRS_CONFLICT;
                    if (performedEdit) {
                        saveMerged = true;
                    }
                    break;
                } else if ("mf".equals(answer)) {
                    choice = SVNConflictChoice.MINE_FULL;
                    if (performedEdit) {
                        saveMerged = true;
                    }
                    break;
                } else if ("tf".equals(answer)) {
                    choice = SVNConflictChoice.THEIRS_FULL;
                    if (performedEdit) {
                        saveMerged = true;
                    }
                    break;
                } else if ("dc".equals(answer)) {
                    if (files.isBinary()) {
                        mySVNEnvironment.getErr().println("Invalid option; cannot display conflicts for a binary file.");
                        mySVNEnvironment.getErr().println();
                        continue;
                    } else if (conflictDescription.isPropertyConflict()) {
                        mySVNEnvironment.getErr().println("Invalid option; cannot display conflicts for properties.");
                        mySVNEnvironment.getErr().println();
                        continue;
                    } else if (files.getLocalFile() == null || files.getBaseFile() == null || files.getRepositoryFile() == null) {
                        mySVNEnvironment.getErr().println("Invalid option; original files not available.");
                        mySVNEnvironment.getErr().println();
                        continue;
                    }
                    
                    //TODO: re-implement in future  
                    showConflictedChunks(files);
                    knowsSmth = true;
                    continue;
                } else if ("df".equals(answer)) {
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
                    knowsSmth = true;
                } else if ("e".equals(answer)) {
                    if (files.getResultFile() != null) {
                        try {
                            String resultPath = files.getResultFile().getAbsolutePath();
                            SVNCommandUtil.editFileExternally(mySVNEnvironment, mySVNEnvironment.getEditorCommand(), 
                                    resultPath);
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
                    if (performedEdit) {
                        knowsSmth = true;
                    }
                } else if ("l".equals(answer)) {
                    if (files.getBaseFile() != null && files.getLocalFile() != null && files.getRepositoryFile() != null && 
                            files.getResultFile() != null) {
                        try {
                            SVNCommandUtil.mergeFileExternally(mySVNEnvironment, files.getBasePath(), files.getRepositoryPath(), 
                                    files.getLocalPath(), files.getResultPath(), files.getWCPath(), null);
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
                } else if ("r".equals(answer)) {
                    if (knowsSmth) {
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
            message = MessageFormat.format(message, new Object[] { files.getWCFile() });
            mySVNEnvironment.getErr().println(message);
            mySVNEnvironment.getErr().println("An object of the same name already exists.");
            
            String prompt = "Select: (p) postpone, (mf) mine-full, (tf) theirs-full, (h) help:";
            while (true) {
                String answer = SVNCommandUtil.prompt(prompt, mySVNEnvironment);
                 
                if ("h".equals(answer) || "?".equals(answer)) {
                    mySVNEnvironment.getErr().println("  (p)  postpone    - resolve the conflict later");
                    mySVNEnvironment.getErr().println("  (mf) mine-full   - accept pre-existing item (ignore upstream addition)");
                    mySVNEnvironment.getErr().println("  (tf) theirs-full - accept incoming item (overwrite pre-existing item)");
                    mySVNEnvironment.getErr().println("  (h)  help        - show this help");
                    mySVNEnvironment.getErr().println();
                }

                if ("p".equals(answer)) {
                    choice = SVNConflictChoice.POSTPONE;
                    break;
                }
                if ("mf".equals(answer)) {
                    choice = SVNConflictChoice.MINE_FULL;
                    break;
                }
                if ("tf".equals(answer)) {
                    choice = SVNConflictChoice.THEIRS_FULL;
                    break;
                }
            }
        } else {
            choice = SVNConflictChoice.POSTPONE;
        }
        
        return new SVNConflictResult(choice, null, saveMerged);
    }
    
    private void showConflictedChunks(SVNMergeFileSet files) throws SVNException {
        byte[] conflictStartMarker = "<<<<<<< MINE (select with 'mc')".getBytes();
        byte[] conflictSeparator = "=======".getBytes();
        byte[] conflictEndMarker = ">>>>>>> THEIRS (select with 'tc')".getBytes();
        byte[] conflictOriginalMarker = "||||||| ORIGINAL".getBytes();
        
        SVNDiffOptions options = new SVNDiffOptions(false, false, true);
        FSMergerBySequence merger = new FSMergerBySequence(conflictStartMarker, conflictSeparator, conflictEndMarker, conflictOriginalMarker);
        RandomAccessFile localIS = null;
        RandomAccessFile latestIS = null;
        RandomAccessFile baseIS = null;
        try {
            localIS = new RandomAccessFile(files.getWCFile(), "r");
            latestIS = new RandomAccessFile(files.getRepositoryFile(), "r");
            baseIS = new RandomAccessFile(files.getBaseFile(), "r");

            QSequenceLineRAData baseData = new QSequenceLineRAFileData(baseIS);
            QSequenceLineRAData localData = new QSequenceLineRAFileData(localIS);
            QSequenceLineRAData latestData = new QSequenceLineRAFileData(latestIS);
            merger.merge(baseData, localData, latestData, options, mySVNEnvironment.getOut(), SVNDiffConflictChoiceStyle.CHOOSE_ONLY_CONFLICTS);
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
            SVNErrorManager.error(err, e, SVNLogType.WC);
        } finally {
            SVNFileUtil.closeFile(localIS);
            SVNFileUtil.closeFile(baseIS);
            SVNFileUtil.closeFile(latestIS);
        }
    }

}
