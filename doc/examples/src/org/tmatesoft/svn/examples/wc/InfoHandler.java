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
package org.tmatesoft.svn.examples.wc;

import org.tmatesoft.svn.core.wc.ISVNInfoHandler;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.io.SVNNodeKind;

public class InfoHandler implements ISVNInfoHandler{
    
    /*
     * This is an implementation of ISVNHandler.handleInfo(SVNInfo info)
     */
    public void handleInfo(SVNInfo info) {
        System.out.println("-----------------INFO-----------------");
        System.out.println("Local Path: " + info.getFile().getPath());
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
        if(info.getKind()==SVNNodeKind.FILE){
            System.out.println("Checksum: " + info.getChecksum());
        }
    }
}
