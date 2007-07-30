package org.tmatesoft.svn.core.internal.server.dav.handlers;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.server.dav.DAVDepth;
import org.tmatesoft.svn.core.internal.server.dav.DAVRepositoryManager;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.xml.sax.Attributes;

public class DAVPropfindHanlder extends ServletDAVHandler {

    private static final Set PROP_ELEMENTS = new HashSet();

    private static final DAVElement PROPFIND = DAVElement.getElement(DAVElement.DAV_NAMESPACE, "propfind");
    private static final DAVElement PROPNAME = DAVElement.getElement(DAVElement.DAV_NAMESPACE, "propname");
    private static final DAVElement ALLPROP = DAVElement.getElement(DAVElement.DAV_NAMESPACE, "allprop");

    private Collection myDAVElements;

    //TODO: specify all myDAVElements for PROP_ELEMENTS
    static {
        PROP_ELEMENTS.add(DAVElement.HREF);
        PROP_ELEMENTS.add(DAVElement.STATUS);
        PROP_ELEMENTS.add(DAVElement.BASELINE);
        PROP_ELEMENTS.add(DAVElement.BASELINE_COLLECTION);
        PROP_ELEMENTS.add(DAVElement.COLLECTION);
        PROP_ELEMENTS.add(DAVElement.VERSION_NAME);
        PROP_ELEMENTS.add(DAVElement.GET_CONTENT_LENGTH);
        PROP_ELEMENTS.add(DAVElement.CREATION_DATE);
        PROP_ELEMENTS.add(DAVElement.CREATOR_DISPLAY_NAME);
        PROP_ELEMENTS.add(DAVElement.BASELINE_RELATIVE_PATH);
        PROP_ELEMENTS.add(DAVElement.MD5_CHECKSUM);
        PROP_ELEMENTS.add(DAVElement.REPOSITORY_UUID);
        PROP_ELEMENTS.add(DAVElement.CHECKED_IN);
        PROP_ELEMENTS.add(DAVElement.RESOURCE_TYPE);
        PROP_ELEMENTS.add(DAVElement.VERSION_CONTROLLED_CONFIGURATION);
    }
   
    public DAVPropfindHanlder(DAVRepositoryManager connector, HttpServletRequest request, HttpServletResponse response) {
        super(connector, request, response);
    }

    protected void startElement(DAVElement parent, DAVElement element, Attributes attrs) throws SVNException {
        if (element == PROPFIND) {
            myDAVElements = new LinkedList();
        } else if ((element == ALLPROP || element == DAVElement.PROP || element == PROPNAME) && parent != PROPFIND) {
            invalidXML();
        }
    }

    protected void endElement(DAVElement parent, DAVElement element, StringBuffer cdata) throws SVNException {
        if (element == ALLPROP) {
            getDAVPropetries().add(ALLPROP);
        } else if (element == PROPNAME) {
            getDAVPropetries().add(PROPNAME);
        } else if (PROP_ELEMENTS.contains(element) && parent == DAVElement.PROP) {
            getDAVPropetries().add(element);
        } else {
            invalidXML();
        }
    }

    public Collection getDAVPropetries() {
        if (myDAVElements == null) {
            myDAVElements = new HashSet();
        }
        return myDAVElements;
    }
    
    public void execute() throws SVNException {
        String label = getRequestHeader(LABEL_HEADER);
        DAVResource resource = getRepositoryManager().createDAVResource(getRequestURI(), label, false);

        getRequestDepth(DAVDepth.DEPTH_INFINITE);
        //TODO: native subversion examine if DEPTH_INFINITE is allowed
        
        parseInput(getRequestInputStream());

        StringBuffer body = new StringBuffer();
        startMultistatus(body);
        
        DAVPropfindHanlder.generatePropertiesResponse(body, (DAVElement[]) getDAVPropetries().toArray(new DAVElement[getDAVPropetries().size()]), resource );
        
        finishMultiStatus(body);
        
        try {
            getResponseOutputStream().write(body.toString().getBytes());
        } catch (IOException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED));
        }
    }

    private void startMultistatus(StringBuffer body) {
        body.append("write header");
    }
    
    private void finishMultiStatus(StringBuffer body) {
        body.append("write footer");
    }
    
    private static void generatePropertiesResponse(StringBuffer body, DAVElement[] properties, DAVResource resourse) {
    }

}
