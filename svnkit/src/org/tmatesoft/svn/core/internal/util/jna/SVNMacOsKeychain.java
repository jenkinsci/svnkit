/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.util.jna;

import java.io.UnsupportedEncodingException;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.util.SVNLogType;

import com.sun.jna.Pointer;

/**
 * @author TMate Software Ltd.
 * @version 1.3
 */
class SVNMacOsKeychain {

    private static final int FALSE = 0;
    private static final int TRUE = 1;

    private static final int ERR_SEC_ITEM_NOT_FOUND = -25300;

    static boolean isEnabled() {
        return true;
    }

    public static synchronized boolean setData(String realm, String userName, char[] data, boolean nonInteractive) throws SVNException {
        final ISVNMacOsSecurityLibrary library = JNALibraryLoader.getMacOsSecurityLibrary();
        if (library == null) {
            return false;
        }
        if (realm == null || userName == null) {
            return false;
        }

        if (nonInteractive) {
            library.SecKeychainSetUserInteractionAllowed(FALSE);
        }

        byte[] rawRealm = null;
        byte[] rawUserName = null;
        byte[] rawData = null;
        try {
            rawRealm = realm.getBytes("UTF-8");
            rawUserName = userName.getBytes("UTF-8");
            rawData = new String(data).getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e);
            SVNErrorManager.error(error, e, SVNLogType.DEFAULT);
        }

        Pointer[] item = new Pointer[1];
        int status = library.SecKeychainFindGenericPassword(null, rawRealm.length, rawRealm,
                rawUserName.length, rawUserName, null, null, item);

        if (status == ERR_SEC_ITEM_NOT_FOUND) {
            status = library.SecKeychainAddGenericPassword(null, rawRealm.length, rawRealm,
                    rawUserName.length, rawUserName, rawData.length, rawData, null);
        } else {
            status = library.SecKeychainItemModifyAttributesAndData(item[0], null, rawData.length, rawData);
        }

        if (nonInteractive) {
            library.SecKeychainSetUserInteractionAllowed(TRUE);
        }

        return status == 0;
    }

    public static synchronized char[] getData(String realm, String userName, boolean nonInteractive) throws SVNException {
        ISVNMacOsSecurityLibrary library = JNALibraryLoader.getMacOsSecurityLibrary();
        if (library == null) {
            return null;
        }

        if (nonInteractive) {
            library.SecKeychainSetUserInteractionAllowed(FALSE);
        }

        byte[] rawRealm;
        byte[] rawUserName;
        try {
            rawRealm = realm.getBytes("UTF-8");
            rawUserName = userName.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e);
            SVNErrorManager.error(error, e, SVNLogType.DEFAULT);
            return null;
        }
        int[] dataLengthHolder = new int[1];
        Pointer[] dataHolder = new Pointer[1];

        int status = library.SecKeychainFindGenericPassword(null, rawRealm.length, rawRealm, rawUserName.length, rawUserName,
                dataLengthHolder, dataHolder, null);

        if (nonInteractive) {
            library.SecKeychainSetUserInteractionAllowed(TRUE);
        }

        if (status != 0) {
            return null;
        }
        if (dataHolder[0] == null) {
            return null;
        }
        byte[] rawData = dataHolder[0].getByteArray(0, dataLengthHolder[0]);

        char[] data;
        try {
            data = new String(rawData, "UTF-8").toCharArray();
        } catch (UnsupportedEncodingException e) {
            SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e);
            SVNErrorManager.error(error, e, SVNLogType.DEFAULT);
            data = null;
        } finally {
            library.SecKeychainItemFreeContent(null, dataHolder[0]);
        }
        return data;
    }
}
