package org.tmatesoft.svn.core.internal.server.dav;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.LinkedList;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.ParserConfigurationException;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.server.dav.handlers.DAVPropertiesHandler;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

public class DAVServlet extends HttpServlet {
    //TODO: Move SVNRepository to FSRepositoryConnector
    private SVNRepository myRepository = null;
    private FSRepositoryConnector myRC = null;
    private static SAXParserFactory mySAXParserFactory;
    private SAXParser mySAXParser;

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
        //TODO: Common for all methods. Specify exception type.       
        if ("PROPFIND".equals(request.getMethod())) {
            doPropfind(request, response);
        }
    }

    private void doPropfind(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        DAVResource resource = null;
        myRC.getDAVResource(request, resource, true, false);
        try {
            getDepth(request, DAVUtil.DEPTH_INFINITE);
            //TODO: native subversion examine if DEPTH_INFINITE is allowed
        } catch (ServletException se) {
            throw new ServletException(se);
        }
        DAVPropertiesHandler propHandler = new DAVPropertiesHandler();
        Collection result = new LinkedList();
        propHandler.setDAVPropetries(result);
        parseInput(request.getInputStream(), "PROPFIND", propHandler);

    }

    public String getServletInfo() {
        return null;
    }

    public void destroy() {
    }

    private void parseInput(InputStream is, String method, DefaultHandler handler) {
        if (mySAXParser == null) {
            try {
                mySAXParser = getSAXParserFactory().newSAXParser();
                XMLReader reader = mySAXParser.getXMLReader();
                reader.setContentHandler(handler);
                reader.setDTDHandler(handler);
                reader.setErrorHandler(handler);
                reader.setEntityResolver(handler);
                reader.parse(new InputSource(is));
            } catch (ParserConfigurationException e) {
            } catch (SAXException e) {
            } catch (IOException e) {

            }
        }
    }

    private int getDepth(HttpServletRequest request, int defaultDepth) throws ServletException {
        String depth = request.getHeader("Depth");
        if (depth == null) {
            return defaultDepth;
        } else if ("Infinity".equals(depth)) {
            return DAVUtil.DEPTH_INFINITE;
        } else if ("0".equals(depth)) {
            return DAVUtil.DEPTH_ZERO;
        } else if ("1".equals(depth)) {
            return DAVUtil.DEPTH_ONE;
        }
        throw new ServletException();
    }

    private static SAXParserFactory getSAXParserFactory() {
        if (mySAXParserFactory == null) {
            mySAXParserFactory = SAXParserFactory.newInstance();
            try {
                mySAXParserFactory.setFeature("http://xml.org/sax/features/namespaces", true);
            } catch (SAXNotRecognizedException e) {
            } catch (SAXNotSupportedException e) {
            } catch (ParserConfigurationException e) {
            }
            try {
                mySAXParserFactory.setFeature("http://xml.org/sax/features/validation", false);
            } catch (SAXNotRecognizedException e) {
            } catch (SAXNotSupportedException e) {
            } catch (ParserConfigurationException e) {
            }
            try {
                mySAXParserFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            } catch (SAXNotRecognizedException e) {
            } catch (SAXNotSupportedException e) {
            } catch (ParserConfigurationException e) {
            }
            mySAXParserFactory.setNamespaceAware(true);
            mySAXParserFactory.setValidating(false);
        }
        return mySAXParserFactory;
    }
}
