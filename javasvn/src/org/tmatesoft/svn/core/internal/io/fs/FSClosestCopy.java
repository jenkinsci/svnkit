/*
 * Created on 23.11.2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package org.tmatesoft.svn.core.internal.io.fs;

/**
 * @author Tim
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
public class FSClosestCopy{
	//revision node
	private FSRevisionNode revNode;
	
	//path 
    private String path;
	
	//constructors
	public FSClosestCopy(){		
	}	
	public FSClosestCopy(FSRevisionNode newRevNode, String newPath){
		revNode = newRevNode;
		path = newPath;
	}
    
	//accessors
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
