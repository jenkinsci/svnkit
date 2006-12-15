/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.fs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.wc.ISVNCommitPathHandler;
import org.tmatesoft.svn.core.internal.wc.SVNCommitUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.ISVNEditor;


/**
 * @version 1.1
 * @author  TMate Software Ltd.
 */
public class FSRepositoryUtil {
    
    public static void replay(FSFS fsfs, FSRoot root, String basePath, long lowRevision, boolean sendDeltas, ISVNEditor editor) throws SVNException {
        Map fsChanges = root.getChangedPaths();
        basePath = basePath.startsWith("/") ? basePath.substring(1) : basePath;
        Collection interestingPaths = new LinkedList();
        Map changedPaths = new HashMap();
        for (Iterator paths = fsChanges.keySet().iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            FSPathChange change = (FSPathChange) fsChanges.get(path);  

            path = path.startsWith("/") ? path.substring(1) : path;
            if ("".equals(basePath) || (path.startsWith(basePath) && (path.charAt(basePath.length()) == '/' || path.length() == basePath.length()))) {
                path = path.startsWith("/") ? path.substring(1) : path;
                interestingPaths.add(path);
                changedPaths.put(path, change);
            }
        }
        if (FSRepository.isInvalidRevision(lowRevision)) {
            lowRevision = 0;
        }
        
        FSRoot compareRoot = null;
        if (sendDeltas && root instanceof FSRevisionRoot) {
            FSRevisionRoot revRoot = (FSRevisionRoot) root;
            compareRoot = fsfs.createRevisionRoot(revRoot.getRevision() - 1);
        }
        
        if (root instanceof FSRevisionRoot) {
            FSRevisionRoot revRoot = (FSRevisionRoot) root;
            editor.targetRevision(revRoot.getRevision());
        }
        
        ISVNCommitPathHandler handler = new FSReplayPathHandler(fsfs, root, compareRoot, changedPaths, basePath, lowRevision);
        SVNCommitUtil.driveCommitEditor(handler, interestingPaths, editor, -1);
    }
    
    public static void copy(InputStream src, OutputStream dst) throws SVNException {
        try {
            byte[] buffer = new byte[102400];
            while (true) {
                int length = src.read(buffer);
                if (length > 0) {
                    dst.write(buffer, 0, length);
                }
                if (length != 102400) {
                    break;
                }
            }
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        } 
    }
    
    public static boolean arePropertiesEqual(FSRevisionNode revNode1, FSRevisionNode revNode2) {
        return areRepresentationsEqual(revNode1, revNode2, true);
    }

    public static boolean arePropertiesChanged(FSRoot root1, String path1, FSRoot root2, String path2) throws SVNException {
        FSRevisionNode node1 = root1.getRevisionNode(path1);
        FSRevisionNode node2 = root2.getRevisionNode(path2);
        return !areRepresentationsEqual(node1, node2, true);
    }

    public static boolean areFileContentsChanged(FSRoot root1, String path1, FSRoot root2, String path2) throws SVNException {
        if (root1.checkNodeKind(path1) != SVNNodeKind.FILE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_GENERAL, "''{0}'' is not a file", path1);
            SVNErrorManager.error(err);
        }
        if (root2.checkNodeKind(path2) != SVNNodeKind.FILE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_GENERAL, "''{0}'' is not a file", path2);
            SVNErrorManager.error(err);
        }
        FSRevisionNode revNode1 = root1.getRevisionNode(path1);
        FSRevisionNode revNode2 = root2.getRevisionNode(path2);
        return !areRepresentationsEqual(revNode1, revNode2, false);
    }

    public static Map getPropsDiffs(Map sourceProps, Map targetProps){
        Map result = new HashMap();
        
        if(sourceProps == null){
            sourceProps = Collections.EMPTY_MAP;
        }
        
        if(targetProps == null){
            targetProps = Collections.EMPTY_MAP;
        }
    
        for(Iterator names = sourceProps.keySet().iterator(); names.hasNext();){
            String propName = (String)names.next();
            String srcPropVal = (String)sourceProps.get(propName);
            String targetPropVal = (String)targetProps.get(propName);
    
            if(targetPropVal == null){
                result.put(propName, null);
            }else if(!targetPropVal.equals(srcPropVal)){
                result.put(propName, targetPropVal);
            }
        }
    
        for(Iterator names = targetProps.keySet().iterator(); names.hasNext();){
            String propName = (String)names.next();
            String targetPropVal = (String)targetProps.get(propName);
            if(sourceProps.get(propName) == null){
                result.put(propName, targetPropVal);
            }
        }        
    
        return result;
    }

    private static boolean areRepresentationsEqual(FSRevisionNode revNode1, FSRevisionNode revNode2, boolean forProperties) {
        if(revNode1 == revNode2){
            return true;
        }else if(revNode1 == null || revNode2 == null){
            return false;
        }
        return FSRepresentation.compareRepresentations(forProperties ? revNode1.getPropsRepresentation() : revNode1.getTextRepresentation(), forProperties ? revNode2.getPropsRepresentation() : revNode2.getTextRepresentation());
    }

}
