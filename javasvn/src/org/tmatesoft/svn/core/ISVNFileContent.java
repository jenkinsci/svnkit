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

package org.tmatesoft.svn.core;

import java.io.*;
import org.tmatesoft.svn.core.io.*;

/**
 * @author Marc Strapetz
 */
public interface ISVNFileContent {

	public boolean hasWorkingCopyContent();

	public void getWorkingCopyContent(OutputStream os) throws SVNException;

	public boolean hasBaseFileContent() throws SVNException;

	public void getBaseFileContent(OutputStream os) throws SVNException;

	public void setWorkingCopyContent(InputStream is) throws IOException, SVNException;

	public void deleteWorkingCopyContent() throws SVNException;
}