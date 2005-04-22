/*
 * Created on 22.04.2005
 */
package org.tmatesoft.svn.core.io;

import java.util.Date;

public class SVNLock {
    
    private String myPath;
    private String myID;
    private String myOwner;
    private String myComment;
    
    private Date myCreationDate;
    private Date myExpirationDate;
    
    public SVNLock(String path, String id, String owner, String comment, Date created, Date expires) {
        myPath = path;
        myID = id;
        myOwner = owner;
        myComment = comment;
        myCreationDate = created;
        myExpirationDate = expires;
    }

    public String getComment() {
        return myComment;
    }

    public Date getCreationDate() {
        return myCreationDate;
    }

    public Date getExpirationDate() {
        return myExpirationDate;
    }

    public String getID() {
        return myID;
    }

    public String getOwner() {
        return myOwner;
    }

    public String getPath() {
        return myPath;
    }
    
    public String toString() {
        StringBuffer result = new StringBuffer();
        result.append("path=");
        result.append(myPath);
        result.append(", token=");
        result.append(myID);
        result.append(", owner=");
        result.append(myOwner);
        if (myComment != null) {
            result.append(", comment=");
            result.append(myComment);
        }
        result.append(", created=");
        result.append(myCreationDate);
        if (myExpirationDate != null) {
            result.append(", expires=");
            result.append(myExpirationDate);
        }        
        return result.toString();
    }
}
