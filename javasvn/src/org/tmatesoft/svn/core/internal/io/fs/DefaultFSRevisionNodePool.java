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

import java.util.LinkedList;
import java.util.TreeMap;
import java.util.Map;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class DefaultFSRevisionNodePool extends FSRevisionNodePool {
    private RevisionCache myRootsCache;
    private RevisionCache myRevisionsCache;
    private int myRootsCacheSize;
    private int myRevisionNodesCacheSize;
    private int myRevisionsCacheSize;
    
    public DefaultFSRevisionNodePool(int rootsCacheSize, int revisionsCacheSize, int revisionNodesCacheSize){
        rootsCacheSize = rootsCacheSize < 0 ? 0 : rootsCacheSize;
        revisionsCacheSize = revisionsCacheSize < 0 ? 0 : revisionsCacheSize;
        revisionNodesCacheSize = revisionNodesCacheSize < 0 ? 0 : revisionNodesCacheSize;
        
        myRootsCacheSize = rootsCacheSize;
        myRevisionNodesCacheSize = revisionsCacheSize;
        myRevisionsCacheSize = revisionNodesCacheSize;
        
        myRootsCache = new RevisionCache(myRootsCacheSize);
        myRevisionsCache = new RevisionCache(myRevisionsCacheSize);
    }

    protected FSRevisionNode fetchRootRevisionNode(long revision){
        return (FSRevisionNode)myRootsCache.fetch(new Long(revision));
    }

    protected void cacheRootRevisionNode(long revision, FSRevisionNode root){
        myRootsCache.put(new Long(revision), root);
    }
    
    protected void cacheRevisionNode(long revision, String path, FSRevisionNode revNode){
        RevisionCache pathsToRevNodes = (RevisionCache)myRevisionsCache.fetch(new Long(revision));
        pathsToRevNodes = pathsToRevNodes == null ? new RevisionCache(myRevisionNodesCacheSize) : pathsToRevNodes;
        pathsToRevNodes.put(path, revNode);
        myRevisionsCache.put(new Long(revision), pathsToRevNodes);
    }
    
    protected FSRevisionNode fetchRevisionNode(long revision, String path){
        RevisionCache pathsToRevNodes = (RevisionCache)myRevisionsCache.fetch(new Long(revision));
        if(pathsToRevNodes != null){
            FSRevisionNode revNode = (FSRevisionNode)pathsToRevNodes.fetch(path);
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
    
    private static final class RevisionCache {
        private LinkedList myKeys;
        private Map myCache;
        private int myMAXKeysNumber;
        
        public RevisionCache(int limit){
            myMAXKeysNumber = limit;
            myKeys = new LinkedList();
            myCache = new TreeMap(); 
        }
        
        public void put(Object key, Object value){
            if(myMAXKeysNumber <= 0){
                return;
            }
            if(myKeys.size() == myMAXKeysNumber){
                Object cachedKey = myKeys.removeLast();
                myCache.remove(cachedKey);
            }
            myKeys.addFirst(key);
            myCache.put(key, value);
        }
        
        public Object fetch(Object key){
            int ind = myKeys.indexOf(key);
            if(ind != -1){
                if(ind != 0){
                    Object cachedKey = myKeys.remove(ind);
                    myKeys.addFirst(cachedKey);
                } 
                return myCache.get(key);
            }
            return null;
        }
        
        public void clear(){
            myKeys.clear();
            myCache.clear();
        }
    }
}
