package org.tmatesoft.svn.core.internal.server.dav.handlers;

import org.tmatesoft.svn.core.internal.io.dav.handlers.BasicDAVHandler;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.SVNException;
import org.xml.sax.Attributes;

public class DAVPropertiesHandler extends BasicDAVHandler {

    public static void generatePropertiesResponse(StringBuffer body, DAVElement[] properties){

    }

    protected void startElement(DAVElement parent, DAVElement element, Attributes attrs) throws SVNException {
    }

    protected void endElement(DAVElement parent, DAVElement element, StringBuffer cdata) throws SVNException {
    }
}
