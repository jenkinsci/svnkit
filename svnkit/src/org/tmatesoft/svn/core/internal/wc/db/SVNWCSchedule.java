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
package org.tmatesoft.svn.core.internal.wc.db;


/**
 * The schedule states an entry can be in.
 *
 * @author  TMate Software Ltd.
 */
public enum SVNWCSchedule {

      /** Nothing special here */
      NORMAL,

      /** Slated for addition */
      ADD,

      /** Slated for deletion */
      DELETE,

      /** Slated for replacement (delete + add) */
      REPLACE
}
