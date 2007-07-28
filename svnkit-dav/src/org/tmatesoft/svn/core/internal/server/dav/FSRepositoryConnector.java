package org.tmatesoft.svn.core.internal.server.dav;

import org.tmatesoft.svn.core.internal.io.fs.FSRepository;
import org.tmatesoft.svn.core.io.SVNRepository;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.ServletConfig;
import java.util.HashMap;
import java.util.Map;

public class FSRepositoryConnector {
    private static FSRepositoryConnector myInstance = new FSRepositoryConnector();
    private Map myRepositories;

    private FSRepositoryConnector() {

    }

    public void init(ServletConfig config) {
        myRepositories = new HashMap();
        //TODO: config handling, adding (repositoryName , SVNRepository) pairs to myRepositories Map. Handling SVNPatentPath
    }


    public void getDAVResource(HttpServletRequest request, DAVResource resource, String label, boolean useCheckedIn) {
        if (resource == null) {
            resource = new DAVResource();
        }
        String requestURI = request.getRequestURI();
        DAVResourceUtil.parseURI(requestURI, resource);
        resource.setRepository((SVNRepository) myRepositories.get(resource.getRepositoryName()));
        //TODO: check params from uri, user per directory access, locks, etc.
    }


    public void getDAVResourceState(DAVResource resource) {

    }

    public static FSRepositoryConnector getInstance() {
        return myInstance;
    }

}
