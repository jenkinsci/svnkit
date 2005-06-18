package org.tmatesoft.svn.core.wc;

import java.io.OutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Date;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 19.06.2005
 * Time: 3:12:10
 * To change this template use File | Settings | File Templates.
 */
public class DefaultSVNAnnotationHandler implements ISVNAnnotationHandler {

    private OutputStream myDst;
    private String myEncoding;
    private StringBuffer myBuffer;

    public DefaultSVNAnnotationHandler(OutputStream dst, String encoding) {
        myDst = dst;
        myEncoding = encoding != null ? encoding : System.getProperty("file.encoding");
        myBuffer = new StringBuffer();
    }

    public void handleLine(String line, long revision, String author, Date date) {
        if (myDst == null) {
            return;
        }
        myBuffer.delete(0, myBuffer.length());
        myBuffer.append(" ");
        myBuffer.append(Long.toString(revision));
        myBuffer.append(" ");
        myBuffer.append(author);
        myBuffer.append(" ");
        myBuffer.append(line);
        byte[] bytes;
        if (myEncoding != null) {
            try {
                bytes = myBuffer.toString().getBytes(myEncoding);
            } catch (UnsupportedEncodingException e) {
                bytes = myBuffer.toString().getBytes();
            }
        } else {
            bytes = myBuffer.toString().getBytes();
        }
        try {
            myDst.write(bytes);
        } catch (IOException e) {
            //
        }
    }

}
