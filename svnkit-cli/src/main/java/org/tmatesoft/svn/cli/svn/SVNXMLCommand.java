/*
 * ====================================================================
 * Copyright (c) 2004-2012 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.cli.svn;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.CharacterCodingException;
import java.nio.ByteBuffer;
import java.io.UnsupportedEncodingException;

import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.util.SVNBase64;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNXMLUtil;
import org.tmatesoft.svn.core.wc.SVNPropertyData;

/**
 * @author TMate Software Ltd.
 * @version 1.3
 */
public abstract class SVNXMLCommand extends SVNCommand {

    protected SVNXMLCommand(String name, String[] aliases) {
        super(name, aliases);
    }

    protected void printXMLHeader(String header) {
        StringBuffer xmlBuffer = new StringBuffer();
        SVNXMLUtil.addXMLHeader(xmlBuffer, true);
        SVNXMLUtil.openXMLTag(null, header, SVNXMLUtil.XML_STYLE_NORMAL, null, xmlBuffer);
        getSVNEnvironment().getOut().print(xmlBuffer.toString());
    }

    protected void printXMLFooter(String header) {
        StringBuffer xmlBuffer = new StringBuffer();
        SVNXMLUtil.closeXMLTag(null, header, xmlBuffer);
        getSVNEnvironment().getOut().print(xmlBuffer.toString());
    }


    protected StringBuffer openCDataTag(String tagName, String cdata, StringBuffer target) {
        return SVNXMLUtil.openCDataTag(null, tagName, cdata, target);
    }


    protected StringBuffer openXMLTag(String tagName, int style, String attr, String value, StringBuffer target) {
        return SVNXMLUtil.openXMLTag(null, tagName, style | SVNXMLUtil.XML_STYLE_ATTRIBUTE_BREAKS_LINE, attr, value, target);
    }

    protected StringBuffer openXMLTag(String tagName, int style, Map attributes, StringBuffer target) {
        return SVNXMLUtil.openXMLTag(null, tagName, style | SVNXMLUtil.XML_STYLE_ATTRIBUTE_BREAKS_LINE, attributes, target);
    }

    protected StringBuffer closeXMLTag(String tagName, StringBuffer target) {
        return SVNXMLUtil.closeXMLTag(null, tagName, target);
    }

    protected StringBuffer printXMLPropHash(StringBuffer buffer, SVNProperties propHash, boolean namesOnly) {
        if (propHash != null && !propHash.isEmpty()) {
            buffer = buffer == null ? new StringBuffer() : buffer;
            for (Iterator propNames = propHash.nameSet().iterator(); propNames.hasNext();) {
                String propName = (String) propNames.next();
                SVNPropertyValue propVal = propHash.getSVNPropertyValue(propName);
                if (namesOnly) {
                    buffer = openXMLTag("property", SVNXMLUtil.XML_STYLE_SELF_CLOSING, "name", propName, buffer);
                } else {
                    buffer = addXMLProp(new SVNPropertyData(propName, propVal, null), buffer);                    
                }
            }
        }
        return buffer;
    }

    protected StringBuffer addXMLProp(SVNPropertyData property, StringBuffer xmlBuffer) {
        String value = property.getValue().getString();
        value = value == null ? "" : value;
        boolean isXMLSafe = true;
        if (property.getValue().isBinary()) {
            CharsetDecoder decoder = Charset.forName("UTF-8").newDecoder();
            decoder.onMalformedInput(CodingErrorAction.REPORT);
            decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
            try {
                value = decoder.decode(ByteBuffer.wrap(property.getValue().getBytes())).toString();
            } catch (CharacterCodingException e) {
                isXMLSafe = false;
            }
        }
        if (value != null && isXMLSafe) {
            isXMLSafe = SVNEncodingUtil.isXMLSafe(value);
        }

        Map attrs = new TreeMap();
        attrs.put("name", property.getName());
        if (!isXMLSafe) {
            attrs.put("encoding", "base64");
            if (value != null) {
                try {
                    value = SVNBase64.byteArrayToBase64(value.getBytes("UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    value = SVNBase64.byteArrayToBase64(value.getBytes());
                }
            } else {
                value = SVNBase64.byteArrayToBase64(property.getValue().getBytes());
            }
        }
        value = SVNEncodingUtil.xmlEncodeCDATA(value);
        xmlBuffer = openXMLTag("property", SVNXMLUtil.XML_STYLE_PROTECT_CDATA, attrs, xmlBuffer);
        xmlBuffer.append(value);
        xmlBuffer = closeXMLTag("property", xmlBuffer);
        return xmlBuffer;
    }
}
