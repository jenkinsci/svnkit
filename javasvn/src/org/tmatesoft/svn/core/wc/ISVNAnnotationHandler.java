package org.tmatesoft.svn.core.wc;

import java.util.Date;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 19.06.2005
 * Time: 3:03:05
 * To change this template use File | Settings | File Templates.
 */
public interface ISVNAnnotationHandler {

    public void handleLine(String line, long revision, String author, Date date);
}
