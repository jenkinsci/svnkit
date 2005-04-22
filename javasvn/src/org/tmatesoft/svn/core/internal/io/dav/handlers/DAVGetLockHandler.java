/*
 * Created on 22.04.2005
 */
package org.tmatesoft.svn.core.internal.io.dav.handlers;

import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.io.dav.DAVResponse;
import org.tmatesoft.svn.core.internal.io.dav.IDAVResponseHandler;
import org.xml.sax.Attributes;


public class DAVGetLockHandler extends DAVPropertiesHandler {

    public static StringBuffer generateGetLockRequest(StringBuffer body) {
        return DAVPropertiesHandler.generatePropertiesRequest(body, new DAVElement[] {DAVElement.LOCK_DISCOVERY});
    }

    private boolean myIsHandlingToken;
    private String myID;
    private String myComment;
    private String myExpiration;

    public DAVGetLockHandler() {
        super(new IDAVResponseHandler() {
            public void handleDAVResponse(DAVResponse response) {
            }
        });
    }    
    public String getComment() {
        return myComment;
    }
    public String getExpiration() {
        return myExpiration;
    }
    public String getID() {
        return myID;
    }

    protected void startElement(DAVElement parent, DAVElement element, Attributes attrs) {
        if (element == DAVElement.LOCK_TOKEN) {
            myIsHandlingToken = true;
        }
    }
    
    protected void endElement(DAVElement parent, DAVElement element, StringBuffer cdata) {
        if (element == DAVElement.HREF && myIsHandlingToken && cdata != null) {
            myID = cdata.toString();
        } else if (element == DAVElement.LOCK_TOKEN) {
            myIsHandlingToken = false;
        } else if (element == DAVElement.LOCK_OWNER && cdata != null) {
            myComment = cdata.toString();
        } else if (element == DAVElement.LOCK_TIMEOUT && cdata != null) {
            myExpiration = cdata.toString();
        } 
    }}
