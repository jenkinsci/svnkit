package org.tmatesoft.svn.core.wc2;

/* The <b>File Kind</b> enum is used to describe the kind of item. This can be:
 * <ul>
 * <li>FILE - file
 * <li>DIRECTORY - directory
 * <li>SYMLINK - symlink
 * <li>UNKNOWN - not known kind
 * </ul>
 */
public enum SvnFileKind {
    FILE, DIRECTORY, SYMLINK, UNKNOWN;
}
