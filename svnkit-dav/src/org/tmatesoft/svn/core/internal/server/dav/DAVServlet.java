package org.tmatesoft.svn.core.internal.server.dav;

import java.io.IOException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepository;

public class DAVServlet extends HttpServlet {

    SVNRepository myRepository = null;

    public void init(ServletConfig servletConfig) throws ServletException {
        String SVNPath = servletConfig.getInitParameter("SVNPath");
        FSRepositoryFactory.setup();
        try {
            myRepository = FSRepositoryFactory.create(SVNURL.parseURIEncoded(SVNPath));
        } catch (SVNException svne) {
            throw new ServletException(svne);
        }
    }

    public ServletConfig getServletConfig() {
        return null;
    }

    public void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    }

    public String getServletInfo() {
        return null;
    }

    public void destroy() {
    }
}
