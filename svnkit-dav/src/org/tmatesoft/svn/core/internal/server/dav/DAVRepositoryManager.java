package org.tmatesoft.svn.core.internal.server.dav;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletConfig;

import org.tmatesoft.svn.core.io.SVNRepository;

public class DAVRepositoryManager {

    private Map myRepositories;

    public DAVRepositoryManager(ServletConfig config) {
        myRepositories = new HashMap();
    }

    public DAVResource createDAVResource(String requestURI, String label, boolean useCheckedIn) {
        DAVResource resource = new DAVResource(requestURI, label, useCheckedIn);
        resource.setRepository((SVNRepository) myRepositories.get(resource.getRepositoryName()));
        
        //TODO: check params from uri, user per directory access, locks, etc.
        return resource;
    }

}
