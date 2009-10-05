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
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaFactory;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNClassLoader {
    private static final String FACTORIES = "adminfactories.properties";
    private static final String DEFAULT_FACTORIES_PROPERTIES = "factory1=org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea16Factory\n" + 
                                                               "factory2=org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea15Factory\n" +
                                                               "factory3=org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea14Factory\n" +
                                                               "factory4=org.tmatesoft.svn.core.internal.wc.admin.SVNXMLAdminAreaFactory\n"; 
    
    public static Collection loadAdminFactories() throws SVNException {
        return loadClassInstances(FACTORIES, DEFAULT_FACTORIES_PROPERTIES);
    }

    public static Collection loadClassInstances(String propertiesFileName, String defaultProperties) throws SVNException {
        Collection classNames = loadValues(propertiesFileName, defaultProperties);
        
        Collection instances = new TreeSet();
        for (Iterator classesIter = classNames.iterator(); classesIter.hasNext();) {
            String className = (String) classesIter.next();
            try {
                Class clazz = SVNClassLoader.class.getClassLoader().loadClass(className);
                if (clazz == null) {
                    SVNDebugLog.getDefaultLog().logFine(SVNLogType.WC, "Could not load class " + className);
                    continue;
                }    
                Object factoryObject = clazz.newInstance();
                if (factoryObject instanceof SVNAdminAreaFactory) {
                    SVNAdminAreaFactory adminAreaFactory = (SVNAdminAreaFactory) factoryObject;
                    instances.add(adminAreaFactory);
                }
            } catch (Throwable th) {
                SVNDebugLog.getDefaultLog().logFine(SVNLogType.WC, "Exception caught while loading class " + className + ": " + th.getMessage());
                continue;
            }
        }
        return instances;
    }
    
    private static Collection loadValues(String propertiesFileName, String defaultPropertiesContents) throws SVNException {
        Properties adminFactoriesProps = new Properties(); 
        InputStream resourceStream = SVNClassLoader.class.getClassLoader().getResourceAsStream(propertiesFileName);
        if (resourceStream == null) {
            resourceStream = new ByteArrayInputStream(defaultPropertiesContents.getBytes());
        }
        
        try {
            adminFactoriesProps.load(resourceStream);
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage());
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        
        List values = new LinkedList();
        for (Iterator keysIter = adminFactoriesProps.keySet().iterator(); keysIter.hasNext();) {
            String key = (String) keysIter.next();
            String value = adminFactoriesProps.getProperty(key);
            values.add(value);
        }
        return values;
    }
}
