/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.examples.repository;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.io.ISVNCredentialsProvider;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.core.io.SVNSimpleCredentialsProvider;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.core.SVNProperty;

/*
 * This example shows how to fetch a file and its properties from the repository
 * at the latest (HEAD) revision . If the file is a text (either it has no
 * svn:mime-type property at all or if has and the property value is text/-like)
 * its contents as well as properties will be displayed in the console,
 * otherwise - only properties. 
 * As an example here's a part of one of the
 * program layouts (for the default url and file path used in the program):
 *  
 * File property: svn:eol-style=LF File property: svn:entry:revision=645 
 * File property: svn:entry:checksum=6b55de2c879b135fec9657309f5c9769 
 * File property: svn:wc:ra_dav:version-url=/svn/jsvn/!svn/ver/301/trunk/www/usage.html 
 * File property: svn:entry:last-author=alex 
 * File property: svn:entry:committed-date=2005-02-19T23:03:10.978491Z 
 * File property: svn:entry:committed-rev=301 
 * File contents: 
 * 
 * <html><head><title>How to Use :: Documentation :: Pure Java Subversion (SVN) Client Library</title>
 *
 * <meta name="keywords" content="Subversion,SVN,Version Control,Java,Library,Development,Team,Teamwork,Configuration Management,Software Configuration Management,SCM,CM,Revision Control,Collaboration,Open Source,Software Development,Collaborative Software Development">   
 * <meta name="description" content="Pure Java Subversion Library. Open Source, provided by TMate Software">
 *
 * <style>
 * html body {
 *     margin: 0px;
 *     padding: 0px;
 *     padding-top: 0.2em;
 *     margin-left: 1em;
 * ......................................
 * --------------------------------------------- 
 * Repository latest revision: 645
 */
public class DisplayFile {
    /*
     * args parameter is used to obtain a repository location URL, user's
     * account name & password to authenticate him to the server, the file path
     * in the rpository (the file path should be relative to the the
     * path/to/repository part of the repository location URL).
     */
    public static void main(String[] args) {
        /*
         * Default values:
         */
        String url = "http://72.9.228.230:8080/svn/jsvn/trunk";
        String name = "anonymous";
        String password = "anonymous";
        String filePath = "www/usage.html";
        /*
         * Initializes the library (it must be done before ever using the
         * library itself)
         */
        setupLibrary();

        if (args != null) {
            /*
             * Obtains a repository location URL
             */
            url = (args.length >= 1) ? args[0] : url;
            /*
             * Obtains a file path
             */
            filePath = (args.length >= 2) ? args[1] : filePath;
            /*
             * Obtains an account name (will be used to authenticate the user to
             * the server)
             */
            name = (args.length >= 3) ? args[2] : name;
            /*
             * Obtains a password
             */
            password = (args.length >= 4) ? args[3] : password;
        }
        SVNRepositoryLocation location;
        SVNRepository repository = null;
        try {
            /*
             * Parses the URL string and creates an SVNRepositoryLocation which
             * represents the repository location - it can be
             * any versioned entry inside the repository.
             */
            location = SVNRepositoryLocation.parseURL(url);
            /*
             * Creates an instance of SVNRepository to work with the repository.
             * All user's requests to the repository are relative to the
             * repository location used to create this SVNRepository.
             */
            repository = SVNRepositoryFactory.create(location);
        } catch (SVNException svne) {
            /*
             * Perhaps a malformed URL is the cause of this exception
             */
            System.err
                    .println("error while creating an SVNRepository for the location '"
                            + url + "': " + svne.getMessage());
            System.exit(1);
        }

        /*
         * Creates a usre's authentication manager.
         */
        ISVNOptions myOptions = SVNWCUtil.createDefaultOptions(true);
        myOptions.setDefaultAuthentication(name, password);

        /*
         * Sets the manager of the user's authentication credentials that will 
         * be used to authenticate the user to the server (if needed) during 
         * operations handled by the SVNRepository.
         */
        repository.setAuthenticationManager(myOptions);

        /*
         * This Map will be used to get the file properties. Each Map key is a
         * property name and the value associated with the key is the property
         * value.
         */
        Map fileProperties = new HashMap();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            /*
             * Checks up if the specified path really corresponds to a file. If
             * doesn't the program exits. SVNNodeKind is that one who says what is
             * located at a path in a revision. -1 means the latest revision.
             */
            SVNNodeKind nodeKind = repository.checkPath(filePath, -1);
            
            if (nodeKind == SVNNodeKind.NONE) {
                System.err.println("There is no entry at '" + url + "'.");
                System.exit(1);
            } else if (nodeKind == SVNNodeKind.DIR) {
                System.err.println("The entry at '" + url
                        + "' is a directory while a file was expected.");
                System.exit(1);
            }
            /*
             * Gets the contents and properties of the file located at filePath
             * in the repository at the latest revision (which is meant by a
             * negative revision number).
             */
            repository.getFile(filePath, -1, fileProperties, baos);

        } catch (SVNException svne) {
            System.err.println("error while fetching the file contents and properties: " + svne.getMessage());
            System.exit(1);
        }

        /*
         * Here the SVNProperty class is used to get the value of the
         * svn:mime-type property (if any). SVNProperty is used to facilitate
         * the work with versioned properties.
         */
        String mimeType = (String) fileProperties.get(SVNProperty.MIME_TYPE);

        /*
         * SVNProperty.isTextType method checks up the value of the mime-type
         * file property and says if the file is a text (true) or not (false).
         */
        boolean isTextType = SVNProperty.isTextType(mimeType);

        Iterator iterator = fileProperties.keySet().iterator();
        /*
         * Displays file properties.
         */
        while (iterator.hasNext()) {
            String propertyName = (String) iterator.next();
            String propertyValue = (String) fileProperties.get(propertyName);
            System.out.println("File property: " + propertyName + "="
                    + propertyValue);
        }
        /*
         * Displays the file contents in the console if the file is a text.
         */
        if (isTextType) {
            System.out.println("File contents:");
            System.out.println();
            try {
                baos.writeTo(System.out);
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        } else {
            System.out
                    .println("File contents can not be displayed in the console since the mime-type property says that it's not a kind of a text file.");
        }
        /*
         * Gets the latest revision number of the repository
         */
        long latestRevision = -1;
        try {
            latestRevision = repository.getLatestRevision();
        } catch (SVNException svne) {
            System.err.println("error while fetching the latest repository revision: " + svne.getMessage());
            System.exit(1);
        }
        System.out.println("");
        System.out.println("---------------------------------------------");
        System.out.println("Repository latest revision: " + latestRevision);
        System.exit(0);
    }

    /*
     * Initializes the library to work with a repository either via svn:// 
     * (and svn+ssh://) or via http:// (and https://)
     */
    private static void setupLibrary() {
        /*
         * for DAV (over http and https)
         */
        DAVRepositoryFactory.setup();
        /*
         * for SVN (over svn and svn+ssh)
         */
        SVNRepositoryFactoryImpl.setup();
    }
}
