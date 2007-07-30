package org.tmatesoft.svn.core.internal.server.dav;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.server.dav.handlers.DAVHandlerFactory;
import org.tmatesoft.svn.core.internal.server.dav.handlers.ServletDAVHandler;

public class DAVServlet extends HttpServlet {
    
    public void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        DAVRepositoryManager connector = new DAVRepositoryManager(getServletConfig());
        try {
            ServletDAVHandler handler = DAVHandlerFactory.createHandler(connector, request, response);
            handler.execute();
        } catch (SVNException e) {
            throw new ServletException(e);
        }
        response.flushBuffer();
    }
}