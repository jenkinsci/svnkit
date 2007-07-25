package org.tmatesoft.svn.core.internal.server.dav;

import java.io.IOException;
import java.io.Reader;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;

public class DAVServlet extends HttpServlet {

    SVNRepository myRepository = null;

    private String getLocation() {
        if (myRepository == null) {
            return null;
        }
        return myRepository.getLocation().getURIEncodedPath();
    }

    private boolean isRepositoryLocation(String path) {
        return getLocation().equals(path);
    }

    public void init(ServletConfig servletConfig) throws ServletException {
        String SVNPath = servletConfig.getInitParameter("SVNPath");
        FSRepositoryFactory.setup();
        try {
            myRepository = SVNRepositoryFactory.create(SVNURL.parseURIEncoded(SVNPath));
        } catch (SVNException svne) {
            throw new ServletException(svne);
        }
    }

    public ServletConfig getServletConfig() {
        return null;
    }

    public void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if ("PROPFIND".equals(request.getMethod())) {
            doPropfind(request, response);
        }
    }

    private void doPropfind(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String requestedPath = request.getRequestURI();
        if (isRepositoryLocation(requestedPath)) {
            ServletInputStream servletInputStream = request.getInputStream();
            Reader servletReader = request.getReader();
            DAVUtil.setProperties(requestedPath, null);
        }


    }

    public String getServletInfo() {
        return null;
    }

    public void destroy() {
    }
}
