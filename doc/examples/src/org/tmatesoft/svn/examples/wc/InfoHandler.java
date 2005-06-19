/*
 * Created on 19.06.2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package org.tmatesoft.svn.examples.wc;

import org.tmatesoft.svn.core.wc.ISVNInfoHandler;
import org.tmatesoft.svn.core.wc.SVNInfo;

public class InfoHandler implements ISVNInfoHandler{
    /*
     * This is an implementation of ISVNHandler.handleInfo(SVNInfo info)
     */
    public void handleInfo(SVNInfo info) {
        System.out.println("-----------------INFO-----------------");
        String relPath = info.getFile().getPath();
        System.out.println("Path: " + info.getFile().getPath());
        System.out.println("URL: " + info.getURL());
        System.out.println("Repository UUID: " + info.getRepositoryUUID());
        System.out.println("Revision: " + info.getRevision().getNumber());
        System.out.println("Node Kind: " + info.getKind().toString());
        System.out.println("Schedule: " + (info.getSchedule()!=null ? info.getSchedule() : "normal"));
        System.out.println("Last Changed Author: " + info.getAuthor());
        System.out.println("Last Changed Revision: "
                + info.getCommittedRevision().getNumber());
        System.out.println("Last Changed Date: " + info.getCommittedDate());
        if(info.getPropTime()!=null){
	        System.out
	                .println("Properties Last Updated: " + info.getPropTime());
        }
    }
}
