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
import java.util.Collection;
import java.util.Iterator;

import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.io.ISVNCredentialsProvider;
import org.tmatesoft.svn.core.io.SVNDirEntry;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.core.io.SVNSimpleCredentialsProvider;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

/*
 * This example shows how to get the repository tree at the latest (HEAD)
 * revision starting with the directory that is the path/to/repository part of
 * the repository location URL. The main point is SVNRepository.getDir() method
 * that is called recursively for each directory (till the end of the tree).
 * getDir collects all entries located inside a directory and returns them as a
 * java.util.Collection. As an example here's one of the program layouts (for
 * the default url used in the program ):
 * 
 * Repository Root: /svn/jsvn Repository UUID:
 * 0a862816-5deb-0310-9199-c792c6ae6c6e
 * 
 * /rss2.php (author:alex; revision:345) 
 * /build.html (author:alex; revision:406)
 * /feed (author:alex; revision:210)
 * /feed/rss_util.php (author:alex; revision:210) 
 * /feed/lgpl.txt (author:alex; revision:33)
 * /feed/feedcreator.class.php (author:alex; revision:33) 
 * /feed/rss.php
 * (author:alex; revision:33) 
 * /ant.html (author:alex; revision:193)
 * /license.html (author:alex; revision:193) 
 * /status.html (author:alex; revision:439) 
 * /usage.html (author:alex; revision:301) 
 * /logging.html (author:alex; revision:397) 
 * /.htaccess (author:alex; revision:51)
 * /subclipse.html (author:alex; revision:406) 
 * /index.php (author:alex; revision:535) 
 * /plugin.xml (author:alex; revision:208) 
 * /rss.php (author:alex; revision:345) 
 * /list.html (author:alex; revision:535)
 * 
 * --------------------------------------------- 
 * Repository latest revision: 645
 */
public class DisplayRepositoryTree {
    /*
     * args parameter is used to obtain a repository location URL, user's
     * account name & password to authenticate him to the server.
     */
    public static void main(String[] args) {
        /*
         * default values:
         */
        String url = "http://72.9.228.230:8080/svn/jsvn/branches/jorunal";
        String name = "anonymous";
        String password = "anonymous";

        /*
         * initializes the library (it must be done before ever using the
         * library itself)
         */
        setupLibrary();
        if (args != null) {
            /*
             * obtains a repository location URL
             */
            url = (args.length >= 1) ? args[0] : url;
            /*
             * obtains an account name (will be used to authenticate the user to
             * the server)
             */
            name = (args.length >= 2) ? args[1] : name;
            /*
             * obtains a password
             */
            password = (args.length >= 3) ? args[2] : password;
        }
        SVNRepositoryLocation location;
        SVNRepository repository = null;
        try {
            /*
             * Parses the URL string and creates an SVNRepositoryLocation which
             * represents the repository location (you can think of this
             * location as of a current repository session directory; it can be
             * any versioned directory inside the repository).
             */
            location = SVNRepositoryLocation.parseURL(url);
            /*
             * Creates an instance of SVNRepository to work with the repository.
             * All user's requests to the repository are relative to the
             * repository location used to create this SVNRepository.
             *  
             */
            repository = SVNRepositoryFactory.create(location);
        } catch (SVNException svne) {
            /*
             * Perhaps a malformed URL is the cause of this exception
             */
            System.err
                    .println("error while creating an SVNRepository for location '"
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
        try {
            /*
             * Checks up if the specified path/to/repository part of the URL
             * really corresponds to a directory. If doesn't the program exits.
             * SVNNodeKind is that one who says what is located at a path in a
             * revision. -1 means the latest revision.
             */
            SVNNodeKind nodeKind = repository.checkPath("", -1);
            if (nodeKind == SVNNodeKind.NONE) {
                System.err.println("There is no entry at '" + url + "'.");
                System.exit(1);
            } else if (nodeKind == SVNNodeKind.FILE) {
                System.err.println("The entry at '" + url + "' is a file while a directory was expected.");
                System.exit(1);
            }
            /*
             * getRepositoryRoot returns the actual root directory where the
             * repository was created
             */
            System.out.println("Repository Root: "
                    + repository.getRepositoryRoot());
            /*
             * getRepositoryUUID returns Universal Unique IDentifier (UUID) - an
             * identifier of the repository
             */
            System.out.println("Repository UUID: "
                    + repository.getRepositoryUUID());
            System.out.println("");

            /*
             * Displays the repository tree at the current path - "" (what means
             * the path/to/repository directory)
             */
            listEntries(repository, "");
        } catch (SVNException svne) {
            System.err.println("error while listing entries: "
                    + svne.getMessage());
            System.exit(1);
        }
        /*
         * Gets the latest revision number of the repository
         */
        long latestRevision = -1;
        try {
            latestRevision = repository.getLatestRevision();
        } catch (SVNException svne) {
            System.err
                    .println("error while fetching the latest repository revision: "
                            + svne.getMessage());
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

    /*
     * Called recursively to obtain all entries that make up the repository tree
     * repository - an SVNRepository which interface is used to carry out the
     * request, in this case it's a request to get all entries in the directory
     * located at the path parameter;
     * 
     * path is a directory path relative to the repository location path (that
     * is a part of the URL used to create an SVNRepository instance);
     *  
     */
    private static void listEntries(SVNRepository repository, String path)
            throws SVNException {
        /*
         * Gets the contents of the directory specified by path at the latest
         * revision (for this purpose -1 is used here as the revision number to
         * mean HEAD-revision) getDir returns a Collection of SVNDirEntry
         * elements. SVNDirEntry represents information about the directory
         * entry. Here this information is used to get the entry name, the name
         * of the person who last changed this entry, the number of the revision
         * when it was last changed and the entry type to determine whether it's
         * a directory or a file. If it's a directory listEntries steps into a
         * next recursion to display the contents of this directory. The third
         * parameter of getDir is null and means that a user is not interested
         * in directory properties. The fourth one is null, too - the user
         * doesn't provide its own Collection instance and uses the one returned
         * by getDir.
         */
        Collection entries = repository.getDir(path, -1, null,
                (Collection) null);
        Iterator iterator = entries.iterator();
        while (iterator.hasNext()) {
            SVNDirEntry entry = (SVNDirEntry) iterator.next();
            System.out.println("/" + (path.equals("") ? "" : path + "/")
                    + entry.getName() + " (author:" + entry.getAuthor()
                    + "; revision:" + entry.getRevision() + ")");
            /*
             * Checking up if the entry is a directory.
             */
            if (entry.getKind() == SVNNodeKind.DIR) {
                listEntries(repository, (path.equals("")) ? entry.getName()
                        : path + "/" + entry.getName());
            }
        }
    }
}