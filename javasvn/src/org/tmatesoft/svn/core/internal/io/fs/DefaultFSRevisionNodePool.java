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
package org.tmatesoft.svn.core.internal.io.fs;

import java.util.TreeMap;
import java.util.Map;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class DefaultFSRevisionNodePool extends FSRevisionNodePool {
    private Map myRootsCache = new TreeMap();
    private Map myRevisionsCache = new TreeMap();
    private int myRootsCacheSize = 100;
    private int myRevisionNodesCacheSize = 100;
    private int myRevisionsCacheSize = 100;
    
    public DefaultFSRevisionNodePool(){
    }

    public DefaultFSRevisionNodePool(int rootsCacheSize, int revisionsCacheSize, int revisionNodesCacheSize){
        myRootsCacheSize = rootsCacheSize;
        myRevisionNodesCacheSize = revisionsCacheSize;
        myRevisionsCacheSize = revisionNodesCacheSize;
    }

    public void setRootsCacheSize(int numberOfRoots) {
        myRootsCacheSize = numberOfRoots;
        resize(myRootsCache, myRootsCacheSize);
    }
    
    public void setRevisionsCacheSize(int numberOfRevs){
        myRevisionsCacheSize = numberOfRevs;
        resize(myRevisionsCache, myRevisionsCacheSize);
    }

    public int getRevisionsCacheSize(){
        return myRevisionsCacheSize;
    }

    public void setRevisionNodesCacheSize(int numberOfNodes) {
        myRevisionNodesCacheSize = numberOfNodes;
        Object[] revisions = myRevisionsCache.keySet().toArray();
        for(int i = 0; i < revisions.length; i++){
            Map pathsToRevNodes = (Map)myRevisionsCache.get(revisions[i]);
            resize(pathsToRevNodes, myRevisionNodesCacheSize);
        }
    }
    
    private void resize(Map cache, int newSize){
        if(cache != null && cache.size() > newSize){
            Object[] keys = cache.keySet().toArray();
            for(int i = 0; i < cache.size() - newSize; i++){
                cache.remove(keys[newSize + i]);
            }
        }
    }
    
    public int getRootsCacheSize() {
        return myRootsCacheSize;
    }

    public int getRevisionNodesCacheSize() {
        return myRevisionNodesCacheSize;
    }

    protected FSRevisionNode fetchRootRevisionNode(long revision){
        return (FSRevisionNode)myRootsCache.get(new Long(revision));
    }

    protected void cacheRootRevisionNode(long revision, FSRevisionNode root){
        resize(myRootsCache, myRootsCacheSize - 1);
        myRootsCache.put(new Long(revision), root);
    }
    
    protected void cacheRevisionNode(long revision, String path, FSRevisionNode revNode){
        Map pathsToRevNodes = (Map)myRevisionsCache.get(new Long(revision));
        pathsToRevNodes = pathsToRevNodes == null ? new TreeMap() : pathsToRevNodes;
        resize(pathsToRevNodes, myRevisionNodesCacheSize - 1);
        pathsToRevNodes.put(path, revNode);
        myRevisionsCache.put(new Long(revision), pathsToRevNodes);
    }
    
    protected FSRevisionNode fetchRevisionNode(long revision, String path){
        Map pathsToRevNodes = (Map)myRevisionsCache.get(new Long(revision));
        if(pathsToRevNodes != null){
            FSRevisionNode revNode = (FSRevisionNode)pathsToRevNodes.get(path);
            return revNode;
        }
        return null;
    }

    public void clearRootsCache(){
        myRootsCache.clear();
    }
    
    public void clearRevisionsCache(){
        myRevisionsCache.clear();
    }
}
