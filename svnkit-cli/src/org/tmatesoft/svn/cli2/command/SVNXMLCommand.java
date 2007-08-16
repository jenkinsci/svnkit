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
package org.tmatesoft.svn.cli2.command;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;

/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public abstract class SVNXMLCommand extends SVNCommand {
    
    protected static final int XML_STYLE_NORMAL = 1;
    protected static final int XML_STYLE_PROTECT_PCDATA = 2;
    protected static final int XML_STYLE_SELF_CLOSING = 4;

    protected SVNXMLCommand(String name, String[] aliases) {
        super(name, aliases);
    }
    
    protected void printXMLHeader(String header) {
        getSVNEnvironment().getOut().print("<?xml version=\"1.0\"?>\n");
        getSVNEnvironment().getOut().print("<" + header + ">\n");
    }

    protected void printXMLFooter(String header) {
        getSVNEnvironment().getOut().print("</" + header + ">\n");
    }
    

    protected StringBuffer openCDataTag(String tagName, String cdata, StringBuffer target) {
        if (cdata == null) {
            return target;
        }
        target = openXMLTag(tagName, XML_STYLE_PROTECT_PCDATA, null, target);
        target.append(SVNEncodingUtil.xmlEncodeCDATA(cdata));
        target = closeXMLTag(tagName, target);
        return target;
    }


    protected StringBuffer openXMLTag(String tagName, int style, String attr, String value, StringBuffer target) {
        Map attrs = new HashMap();
        attrs.put(attr, value);
        return openXMLTag(tagName, style, attrs, target);
    }

    protected StringBuffer openXMLTag(String tagName, int style, Map attributes, StringBuffer target) {
        target = target == null ? new StringBuffer() : target;
        target.append("<");
        target.append(tagName);
        if (attributes != null) {
            for (Iterator names = attributes.keySet().iterator(); names.hasNext();) {
                String name = (String) names.next();
                String value = (String) attributes.get(name);
                target.append("\n   ");
                target.append(name);
                target.append("=\"");
                target.append(SVNEncodingUtil.xmlEncodeAttr(value));
                target.append("\"");
            }
            attributes.clear();
        }
        if (style == XML_STYLE_SELF_CLOSING) {
            target.append("/");
        }
        target.append(">");
        if (style != XML_STYLE_PROTECT_PCDATA) {
            target.append("\n");
        }
        return target;
    }

    protected StringBuffer closeXMLTag(String tagName, StringBuffer target) {
        target = target == null ? new StringBuffer() : target;
        target.append("</");
        target.append(tagName);
        target.append(">\n");
        return target;
    }
}
