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
package org.tmatesoft.svn.core.internal.io.fs;

import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.delta.SVNDeltaCombiner;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.ISVNCommitPathHandler;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator;


/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class FSReplayPathHandler implements ISVNCommitPathHandler {

    private FSRoot myRoot;
    private FSRoot myCompareRoot;
    private Map myChangedPaths;
    private String myBasePath;
    private long myLowRevision;
    private LinkedList myCopies;
    private FSFS myOwner;
    private SVNDeltaGenerator myDeltaGenerator;
    private SVNDeltaCombiner myDeltaCombiner;

    public FSReplayPathHandler(FSFS owner, FSRoot root, FSRoot compareRoot, Map changedPaths, String basePath, long lowRevision) {
        myRoot = root;
        myCompareRoot = compareRoot;
        myChangedPaths = changedPaths;
        myBasePath = basePath;
        myLowRevision = lowRevision;
        myCopies = new LinkedList();
        myOwner = owner;
        myDeltaGenerator = new SVNDeltaGenerator();
        myDeltaCombiner = new SVNDeltaCombiner();
    }
    
    public boolean handleCommitPath(String path, ISVNEditor editor) throws SVNException {
        String absPath = !path.startsWith("/") ? "/" + path : path;
        while(myCopies.size() > 0) {
            CopyInfo info = (CopyInfo) myCopies.getLast();
            if (SVNPathUtil.isAncestor(info.myPath, path)) {
                break;
            }
            myCopies.removeLast();
        }
        
        boolean isAdd = false;
        boolean isDelete = false;
        
        FSPathChange change = (FSPathChange) myChangedPaths.get(path);
        if (change.getChangeKind() == FSPathChangeKind.FS_PATH_CHANGE_ADD) {
            isAdd = true;
        } else if (change.getChangeKind() == FSPathChangeKind.FS_PATH_CHANGE_DELETE) {
            isDelete = true;
        } else if (change.getChangeKind() == FSPathChangeKind.FS_PATH_CHANGE_REPLACE) {
            isAdd = true;
            isDelete = true;
        }
        
        if (isDelete) {
            editor.deleteEntry(path, -1);
            return false;
        }
        
        SVNNodeKind kind = null;
        if (!isDelete || isAdd) {
            kind = myRoot.checkNodeKind(absPath);
            if (kind != SVNNodeKind.DIR && kind != SVNNodeKind.FILE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "Filesystem path ''{0}'' is neither a file nor a directory", path);
                SVNErrorManager.error(err);
            }
        }
        
        String copyFromPath = null;
        String realCopyFromPath = null;
        FSRoot srcRoot = myCompareRoot;
        String srcPath = srcRoot != null ? absPath : null;
        boolean closeFile = false;
        if (isAdd) {
            FSRoot copyFromRoot = null;
            FSRevisionNode copyfromNode = myRoot.getRevisionNode(absPath);
            copyFromPath = copyfromNode.getCopyFromPath();
            long copyFromRevision = copyfromNode.getCopyFromRevision();
            if (copyFromPath != null && FSRepository.isValidRevision(copyFromRevision)) {
                copyFromRoot = myOwner.createRevisionRoot(copyFromRevision);
            }
            
            realCopyFromPath = copyFromPath;

            if (copyFromPath != null) {
                String relCopyFromPath = copyFromPath.substring(1);
                boolean isWithinBasePath = "".equals(myBasePath) || (relCopyFromPath.startsWith(myBasePath) && (relCopyFromPath.charAt(myBasePath.length()) == '/' || relCopyFromPath.length() == myBasePath.length()));
                if (!isWithinBasePath || myLowRevision > copyFromRevision) {
                    copyFromPath = null;
                    copyFromRevision = -1;
                }
            }
            
            if (kind == SVNNodeKind.DIR) {
                if (realCopyFromPath != null && copyFromPath == null) {
                    addSubdirectory(copyFromRoot, myRoot, editor, realCopyFromPath, path);
                } else {
                    editor.addDir(path, copyFromPath, copyFromRevision);
                }
            } else {
                editor.addFile(path, copyFromPath, copyFromRevision);
                closeFile = true;
            }
            
            if (copyFromPath != null) {
                if (kind == SVNNodeKind.DIR) {
                    CopyInfo info = new CopyInfo(path, copyFromPath, copyFromRevision);
                    myCopies.addLast(info);
                }
                srcRoot = copyFromRoot;
                srcPath = copyFromPath;
            } else {
                if (kind == SVNNodeKind.DIR && myCopies.size() > 0) {
                    CopyInfo info = new CopyInfo(path, null, -1);
                    myCopies.addLast(info);
                }
                srcRoot = null;
                srcPath = null;
            }
        } else if (!isDelete) {
            if (kind == SVNNodeKind.DIR) {
                if ("".equals(path)) {
                    editor.openRoot(-1);
                } else {
                    editor.openDir(path, -1);
                }
            } else {
                editor.openFile(path, -1);
                closeFile = true;
            }
            
            if (myCopies.size() > 0) {
                CopyInfo info = (CopyInfo) myCopies.getLast();
                if (info.myCopyFromPath != null) {
                    srcRoot = myOwner.createRevisionRoot(info.myCopyFromRevision);
                    srcPath = SVNPathUtil.append(info.myCopyFromPath, SVNPathUtil.pathIsChild(info.myPath, path));
                } else {
                    srcRoot = null;
                    srcPath = null;
                }
            } 
        }
        
        if (!isDelete || isAdd) {
            if (change.arePropertiesModified()) {
                if (myCompareRoot != null) {
                    Map oldProps = null;
                    if (srcRoot != null) {
                        FSRevisionNode srcNode = srcRoot.getRevisionNode(srcPath);
                        oldProps = srcNode.getProperties(myOwner);
                    }
                    
                    FSRevisionNode node = myRoot.getRevisionNode(absPath);
                    Map newProps = node.getProperties(myOwner);
                    Map propDiff = FSRepositoryUtil.getPropsDiffs(oldProps, newProps);
                    for (Iterator propNames = propDiff.keySet().iterator(); propNames.hasNext();) {
                        String propName = (String) propNames.next();
                        String propValue = (String) propDiff.get(propName);
                        if (kind == SVNNodeKind.DIR) {
                            editor.changeDirProperty(propName, propValue);
                        } else if (kind == SVNNodeKind.FILE) {
                            editor.changeFileProperty(path, propName, propValue);
                        }
                    }
                } else {
                    if (kind == SVNNodeKind.DIR) {
                        editor.changeDirProperty("", null);
                    } else if (kind == SVNNodeKind.FILE) {
                        editor.changeFileProperty(path, "", null);
                    }
                }
            }
            
            if (kind == SVNNodeKind.FILE && (change.isTextModified() || (realCopyFromPath != null && copyFromPath == null))) {
                editor.applyTextDelta(path, null);
                if (myCompareRoot != null) {
                    InputStream sourceStream = null;
                    InputStream targetStream = null;
                    try {
                        if (srcRoot != null && srcPath != null) {
                            sourceStream = srcRoot.getFileStreamForPath(myDeltaCombiner, srcPath);
                        } else {
                            sourceStream = SVNFileUtil.DUMMY_IN;
                        }
                        targetStream = myRoot.getFileStreamForPath(myDeltaCombiner, absPath);
                        myDeltaGenerator.sendDelta(path, sourceStream, 0, targetStream, editor, false);
                    } finally {
                        SVNFileUtil.closeFile(sourceStream);
                        SVNFileUtil.closeFile(targetStream);
                    }
                }
            }
        }
        
        if (closeFile) {
            editor.closeFile(path, null);
            return false;
        }
        return true;
    }

    private void addSubdirectory(FSRoot srcRoot, FSRoot tgtRoot, ISVNEditor editor, String srcPath, String path) throws SVNException {
        editor.addDir(path, null, -1);
        FSRevisionNode node = srcRoot.getRevisionNode(srcPath);
        
        Map props = node.getProperties(myOwner);
        for (Iterator propNames = props.keySet().iterator(); propNames.hasNext();) {
            String propName = (String) propNames.next();
            String propValue = (String) props.get(propName);
            editor.changeDirProperty(propName, propValue);
        }
        
        Map entries = node.getDirEntries(myOwner);
        for (Iterator entryNames = entries.keySet().iterator(); entryNames.hasNext();) {
            String entryName = (String) entryNames.next();
            FSEntry entry = (FSEntry) entries.get(entryName);
            String newPath = SVNPathUtil.append(path, entry.getName());
            
            if (entry.getType() == SVNNodeKind.DIR) {
                addSubdirectory(srcRoot, tgtRoot, editor, SVNPathUtil.append(srcPath, entry.getName()), newPath);
                editor.closeDir();
            } else if (entry.getType() == SVNNodeKind.FILE) {
                editor.addFile(SVNPathUtil.append(path, entry.getName()), null, -1);
                String newSrcPath = SVNPathUtil.append(srcPath, entry.getName());
                FSRevisionNode srcNode = srcRoot.getRevisionNode(newSrcPath);
                
                props = srcNode.getProperties(myOwner);
                for (Iterator propNames = props.keySet().iterator(); propNames.hasNext();) {
                    String propName = (String) propNames.next();
                    String propValue = (String) props.get(propName);
                    editor.changeFileProperty(newPath, propName, propValue);
                }                
                
                editor.applyTextDelta(newPath, null);
                
                InputStream targetStream = null;
                try {
                    targetStream = srcRoot.getFileStreamForPath(myDeltaCombiner, newSrcPath);
                    myDeltaGenerator.sendDelta(newPath, SVNFileUtil.DUMMY_IN, 0, targetStream, editor, false);
                } finally {
                    SVNFileUtil.closeFile(targetStream);
                }
                editor.closeFile(newPath, null);
            }
            
        }
    }
    
    private class CopyInfo {
        String myCopyFromPath;
        long myCopyFromRevision;
        String myPath;
        
        public CopyInfo(String path, String copyFromPath, long copyFromRevision) {
            myPath = path;
            myCopyFromPath = copyFromPath;
            myCopyFromRevision = copyFromRevision;
        }
    }
}
