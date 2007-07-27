package org.tmatesoft.svn.core.internal.server.dav;

import org.tmatesoft.svn.core.internal.io.fs.FSRepository;

import javax.servlet.http.HttpServletRequest;

public class FSRepositoryConnector {

    private FSRepository myRepository;

    public void getDAVResource(HttpServletRequest request, DAVResource resource, boolean labelAllowed, boolean useCheckedIn) {
    }

    public void getDAVResourceState(DAVResource resource) {

    }

}
