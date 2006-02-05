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

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSClosestCopy{

    private FSRevisionNode revNode;
	
    private String path;
	
	public FSClosestCopy(){		
	}	
	
    public FSClosestCopy(FSRevisionNode newRevNode, String newPath){
		revNode = newRevNode;
		path = newPath;
	}
    
	public FSRevisionNode getRevisionNode(){
		return revNode;
	}
	
    public String getPath(){
		return path;
	}
	
    public void setRevisionNode(FSRevisionNode newRevNode){
		revNode = newRevNode;
	}
	
    public void setPath(String newPath){
		path = newPath;
	}
}
