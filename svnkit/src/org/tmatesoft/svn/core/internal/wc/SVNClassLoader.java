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
import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.fs.repcache.IFSRepresentationCacheManagerFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNAuthenticator;
import org.tmatesoft.svn.core.internal.io.svn.SVNConnection;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaFactory;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNClassLoader {
    private static final String SVNKIT_PROPERTIES = "svnkit.properties";
    private static final String SVNKIT_PROPERTIES_PATH = "svnkit.properties.property";
    
    private static final String DEFAULT_PROPERTIES = "svnkit.adminareafactory.1=org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea16Factory\n" + 
                                                     "svnkit.adminareafactory.2=org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea15Factory\n" +
                                                     "svnkit.adminareafactory.3=org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea14Factory\n" +
                                                     "svnkit.adminareafactory.4=org.tmatesoft.svn.core.internal.wc.admin.SVNXMLAdminAreaFactory\n" +
                                                     "svnkit.repcachemanagerfactory.1=org.tmatesoft.svn.core.internal.io.fs.repcache.FSRepresentationCacheManagerFactory\n" +
                                                     "svnkit.repcachemanagerfactory.2=org.tmatesoft.svn.core.internal.io.fs.repcache.FSEmptyRepresentationCacheManagerFactory\n" +
                                                     "svnkit.saslauthenticator.1=org.tmatesoft.svn.core.internal.io.svn.sasl.SVNSaslAuthenticator\n" +
                                                     "svnkit.saslauthenticator.2=org.tmatesoft.svn.core.internal.io.svn.SVNPlainAuthenticator\n"; 

    
    public static Collection getAvailableAdminAreaFactories() {
        Collection instances = getAllClasses("svnkit.adminareafactory.");
        Collection factories = new TreeSet();
        for (Iterator instanceIter = instances.iterator(); instanceIter.hasNext();) {
            Object object = instanceIter.next();
            if (object instanceof SVNAdminAreaFactory) {
                factories.add(object);
            }
        }
        return factories;
    }
    
    public static SVNAuthenticator getSASLAuthenticator(SVNConnection connection) throws SVNException {
        Class saslAuthenticatorClass = getFirstAvailableClass("svnkit.saslauthenticator.");
        if (saslAuthenticatorClass != null) {
            try {
                Constructor constructor = saslAuthenticatorClass.getConstructor(new Class[] { SVNConnection.class });
                if (constructor != null) {
                    return (SVNAuthenticator) constructor.newInstance(new Object[] { connection });
                }
            } catch (Throwable th) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Could not instantiate a SASL authenticator: {0}", th.getMessage());
                SVNErrorManager.error(err, SVNLogType.DEFAULT);
            }
        }
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "No SASL authenticator class found!");
        SVNErrorManager.error(err, SVNLogType.NETWORK);
        return null;
    }
    
    public static IFSRepresentationCacheManagerFactory getFSRepresentationCacheManagerFactory() {
        Class repCacheManagerFactoryClass = getFirstAvailableClass("svnkit.repcachemanagerfactory.");
        if (repCacheManagerFactoryClass != null) {
            Object object = null;
            try {
                object = repCacheManagerFactoryClass.newInstance();
            } catch (Throwable th) {
                SVNDebugLog.getDefaultLog().logFine(SVNLogType.FSFS, "Could not instantiate " + repCacheManagerFactoryClass.getName() + ": " + th.getMessage());
            }

            if (object != null && object instanceof IFSRepresentationCacheManagerFactory) {
                return (IFSRepresentationCacheManagerFactory) object;
            }
        } 
        return null;
    }
    
    private static Collection getAllClasses(String keyPrefix) {
        Collection classes = new TreeSet();
        Map svnkitProps = SVNClassLoader.loadProperties();
        for (Iterator svnkitPropsIter = svnkitProps.keySet().iterator(); svnkitPropsIter.hasNext();) {
            String key = (String) svnkitPropsIter.next();
            if (key.startsWith(keyPrefix)) {
                String className = (String) svnkitProps.get(key);
                if (className == null) {
                    continue;
                }
                
                Class clazz = null;
                try {
                    clazz = SVNClassLoader.class.getClassLoader().loadClass(className);
                } catch (Throwable th) {
                    SVNDebugLog.getDefaultLog().logFine(SVNLogType.WC, "Could not load class " + className + ": " + th.getMessage());
                    continue;
                }

                if (clazz == null) {
                    SVNDebugLog.getDefaultLog().logFine(SVNLogType.WC, "Could not load class " + className);
                    continue;
                }    

                Object object = null;
                try {
                    object = clazz.newInstance();
                } catch (Throwable th) {
                    SVNDebugLog.getDefaultLog().logFine(SVNLogType.WC, "Could not instantiate class " + className + ": " + th.getMessage());
                    continue;
                }

                if (object != null) {
                    classes.add(object);
                }
            }
        }
        return classes;
    }
    
    private static Class getFirstAvailableClass(String keyPrefix) {
        Map svnkitProps = SVNClassLoader.loadProperties();
        for (Iterator svnkitPropsIter = svnkitProps.keySet().iterator(); svnkitPropsIter.hasNext();) {
            String key = (String) svnkitPropsIter.next();
            if (key.startsWith(keyPrefix)) {
                String className = (String) svnkitProps.get(key);
                if (className == null) {
                    continue;
                }
                
                Class clazz = null;
                try {
                    clazz = SVNClassLoader.class.getClassLoader().loadClass(className);
                } catch (ClassNotFoundException e) {
                    SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, "Failed to load class '" + className + "': " + e.getMessage());
                }
                
                if (clazz != null) {
                    return clazz;
                }
            }
        }
        return null;
    }
    
    private static Map loadProperties() {
        Map finalProps = new TreeMap();
        Properties props = new Properties(); 
        InputStream resourceStream = new ByteArrayInputStream(DEFAULT_PROPERTIES.getBytes());
        
        try {
            //1. first load default props
            props.load(resourceStream);
        } catch (IOException ioe) {
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, "Failed to load default SVNKit properties: " + ioe.getMessage());
        }

        String svnkitPropertiesResource = System.getProperty(SVNKIT_PROPERTIES_PATH, SVNKIT_PROPERTIES);
        try {
            //2. and 3. second try to locate a props file from a system property
            //if none found, use default name for the props file
            resourceStream = SVNClassLoader.class.getClassLoader().getResourceAsStream(svnkitPropertiesResource);
            if (resourceStream != null) {
                props.load(resourceStream);
            }
        } catch (IOException e) {
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, "Failed to load '" + svnkitPropertiesResource + "': " + e.getMessage());
        } finally {
            SVNFileUtil.closeFile(resourceStream);
        }

        //4. try to iterate over possible keys and load values from 
        //system properties with the same names
        //Properties finalProps = new Properties(); 
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
