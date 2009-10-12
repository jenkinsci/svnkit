/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNClassLoader {
    private static final String SVNKIT_PROPERTIES = "svnkit.properties";
    private static final String SVNKIT_PROPERTIES_SYSTEM_PROPERTY = "svnkit.properties.property";
    
    private static final String DEFAULT_PROPERTIES = "svnkit.adminareafactory.1=org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea16Factory\n" + 
                                                     "svnkit.adminareafactory.2=org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea15Factory\n" +
                                                     "svnkit.adminareafactory.3=org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea14Factory\n" +
                                                     "svnkit.adminareafactory.4=org.tmatesoft.svn.core.internal.wc.admin.SVNXMLAdminAreaFactory\n" +
                                                     "svnkit.repcachemanagerfactory.1=org.tmatesoft.svn.core.internal.io.fs.repcache.FSRepresentationCacheManagerFactory\n" +
                                                     "svnkit.repcachemanagerfactory.2=org.tmatesoft.svn.core.internal.io.fs.repcache.FSEmptyRepresentationCacheManagerFactory\n" +
                                                     "svnkit.saslauthenticator.1=org.tmatesoft.svn.core.internal.io.svn.sasl.SVNSaslAuthenticator\n"; 

    
    public static Map loadProperties() throws SVNException {
        Properties props = new Properties(); 
        InputStream resourceStream = new ByteArrayInputStream(DEFAULT_PROPERTIES.getBytes());
        
        try {
            //1. first load default props
            props.load(resourceStream);
            
            //2. and 3. second try to locate a props file from a system property
            //if none found, use default name for the props file
            String svnkitPropertiesResource = System.getProperty(SVNKIT_PROPERTIES_SYSTEM_PROPERTY, SVNKIT_PROPERTIES);
            resourceStream = SVNClassLoader.class.getClassLoader().getResourceAsStream(svnkitPropertiesResource);
            if (resourceStream != null) {
                props.load(resourceStream);
            }
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage());
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        } finally {
            SVNFileUtil.closeFile(resourceStream);
        }

        //4. try to iterate over possible keys and load values from 
        //system properties with the same names
        //Properties finalProps = new Properties(); 
        Map finalProps = new TreeMap();
        for (Iterator propNamesIter = props.keySet().iterator(); propNamesIter.hasNext();) {
            String key = (String) propNamesIter.next();
            String value = props.getProperty(key);
            value = System.getProperty(key, value);
            //finalProps.setProperty(key, value);
            finalProps.put(key, value);
        }

        return finalProps;
    }
}
