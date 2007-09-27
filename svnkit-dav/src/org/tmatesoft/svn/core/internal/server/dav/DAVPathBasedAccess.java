/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.server.dav;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNTranslator;
import org.tmatesoft.svn.core.internal.wc.admin.SVNTranslatorInputStream;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVPathBasedAccess {
    private String myConfigurationPath;
    private int myCurrentLineNumber = 1;
    private int myCurrentLineColumn = 0;
    private char myUngottenChar = 0;
    private boolean myHasUngottenChar = false;

    public DAVPathBasedAccess(File pathBasedAccessConfiguration) throws SVNException {
        myConfigurationPath = pathBasedAccessConfiguration.getAbsolutePath();

        InputStream stream = null;
        try {
            stream = new SVNTranslatorInputStream(SVNFileUtil.openFileForReading(pathBasedAccessConfiguration), SVNTranslator.LF, true, null, false);
            parse(stream);
        } catch (SVNException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_INVALID_CONFIG_VALUE, "Failed to load the AuthzSVNAccessFile: ''{0}''", pathBasedAccessConfiguration.getAbsolutePath()));
        } catch (IOException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage()));
        } finally {
            SVNFileUtil.closeFile(stream);
        }
    }


    private String getConfigurationPath() {
        return myConfigurationPath;
    }

    private void increaseCurrentLineNumber() {
        myCurrentLineNumber++;
    }

    private int getCurrentLineNumber() {
        return myCurrentLineNumber;
    }

    private void increaseCurrentLineColumn() {
        myCurrentLineColumn++;
    }

    private void resetCurrentLineColumn() {
        myCurrentLineColumn = 0;
    }

    private int getCurrentLineColumn() {
        return myCurrentLineColumn;
    }

    private char getUngottenChar() {
        return myUngottenChar;
    }

//    private void setUngottenChar(char ungottenChar) {
//        myUngottenChar = ungottenChar;
//    }

    private boolean hasUngottenChar() {
        return myHasUngottenChar;
    }

    private void setHasUngottenChar(boolean hasUngottenChar) {
        this.myHasUngottenChar = hasUngottenChar;
    }

    private void parse(InputStream is) throws IOException, SVNException {
        boolean isEOF = false;
        do {
            int currentByte = skipWhitespace(is);
            switch (currentByte) {
                case'[':
                    if (getCurrentLineColumn() == 0) {
                        //TODO: parseSection(is);
                    } else {
                        SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_INVALID_CONFIG_VALUE, "''{0}'' : ''{1}'' : Section header must start in the first column", new Object[]{getConfigurationPath(), new Integer(getCurrentLineNumber())}));
                    }
                    break;
                case'#':
                    if (getCurrentLineColumn() == 0) {
                        skipToEndOfLine(is);
                        increaseCurrentLineNumber();
                    } else {
                        SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_INVALID_CONFIG_VALUE, "''{0}'' : ''{1}'' : Comment must start in the first column", new Object[]{getConfigurationPath(), new Integer(getCurrentLineNumber())}));
                    }
                    break;
                case '\n':
                    increaseCurrentLineNumber();
                    break;
                case -1:
                    isEOF = true;
                    break;
                default:
                    //TODO: parse options
            }
        } while (isEOF);


    }

    private int skipWhitespace(InputStream is) throws IOException {
        int currentByte = getc(is);
        while (currentByte > 0 && currentByte != '\n' && !Character.isWhitespace((char) currentByte)) {
            currentByte = getc(is);
            increaseCurrentLineColumn();
        }
        return currentByte;
    }

    private int skipToEndOfLine(InputStream is) throws IOException {
        int currentByte = getc(is);
        while (currentByte > 0 && currentByte != '\n') {
            currentByte = getc(is);
            resetCurrentLineColumn();
        }
        return currentByte;
    }

    private int getc(InputStream is) throws IOException {
        if (hasUngottenChar()) {
            setHasUngottenChar(false);
            return getUngottenChar();
        } else {
            return is.read();
        }
    }
//
//    private void ungetc(char ungottenChar) {
//        setUngottenChar(ungottenChar);
//        setHasUngottenChar(true);
//    }


}
