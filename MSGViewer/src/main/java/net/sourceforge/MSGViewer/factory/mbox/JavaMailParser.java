package net.sourceforge.MSGViewer.factory.mbox;

import at.redeye.FrameWork.utilities.StringUtils;
import net.sourceforge.MSGViewer.factory.mbox.headers.DateHeader;
import net.sourceforge.MSGViewer.factory.mbox.headers.FromEmailHeader;
import net.sourceforge.MSGViewer.factory.mbox.headers.ToEmailHeader;
import com.auxilii.msgparser.Message;
import com.auxilii.msgparser.attachment.FileAttachment;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Enumeration;
import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Header;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import org.apache.log4j.Logger;

/**
 *
 * @author martin
 */
public class JavaMailParser
{
    private static final Logger LOGGER = Logger.getLogger(JavaMailParser.class.getName());

    private static final FromEmailHeader FROM_PARSER = new FromEmailHeader();
    private static final ToEmailHeader TO_PARSER =  new ToEmailHeader();
    private static final DateHeader DATE_PARSER = new DateHeader();

    public Message parse( File file ) throws Exception
    {
        javax.mail.Message jmsg = parseJMessage(file);

        Message msg = new Message();

        FROM_PARSER.parse(msg, getAddresses(jmsg.getFrom()) );
        TO_PARSER.parse(msg, getAddresses(jmsg.getFrom()) );
        msg.setSubject(jmsg.getSubject());


        msg.setHeaders(getHeaders(jmsg.getAllHeaders()));
        DATE_PARSER.parse(msg, getFirstHeader(jmsg.getHeader("Date")) );
        msg.setMessageId(getFirstHeader(jmsg.getHeader("Message-Id")));

        msg.setBodyText("");
        msg.setBodyRTF("");

        parse( msg, jmsg );

        return msg;
    }

    private javax.mail.Message parseJMessage(File file) throws MessagingException, IOException {
        try (InputStream stream = new FileInputStream(file)) {
            Session session = Session.getInstance(System.getProperties());
            return new MimeMessage(session, stream);
        }
    }

    private void parse( Message msg, Part part ) throws MessagingException, IOException
    {
        LOGGER.info("Content Type: " + part.getContentType());

        if( part.isMimeType("text/plain") && msg.getBodyText().isEmpty() )
        {
            msg.setBodyText((String)part.getContent());

        } else if( part.isMimeType("multipart/*")) {

           Multipart multipart = (Multipart) part.getContent();

           for( int i = 0; i < multipart.getCount(); i++ )
           {
              BodyPart sub_part = multipart.getBodyPart(i);

              parse(msg,sub_part);
           }
        } else {
            String disp = part.getDisposition();

            if( disp == null && part.getFileName() == null && part.isMimeType("text/html") ) {
                // this is our html message body
                byte[] bytes = getContent(part);

                StringBuilder sb = new StringBuilder();

                sb.append(new String(bytes,getCharset(part.getContentType())));
                sb.append("<!-- \\purehtml -->");

                msg.setBodyRTF(sb.toString());
                LOGGER.debug(msg.getBodyRTF());
            } else if (disp == null || disp.equalsIgnoreCase(Part.ATTACHMENT)) {
                // many mailers don't include a Content-Disposition

                MimeBodyPart mpart = (MimeBodyPart)part;

                FileAttachment att = new FileAttachment();
                att.setMimeTag(getMime(part.getContentType()));
                att.setFilename(part.getFileName());

                String cid = mpart.getContentID();
                if( cid != null ) {
                    cid = StringUtils.strip(cid, "<>");
                    att.setCid(cid);
                }

                att.setSize(mpart.getSize());

                if( att.getFilename() == null ) {
                    att.setFilename("");
                }

                att.setData(getContent(part));

                msg.addAttachment(att);
            } else {
                LOGGER.warn("Unparseable part");
            }
        }
    }

    private String getCharset( String content )
    {
        if( content.matches(".*;\\s*charset=.*") )
        {
            int idx = content.indexOf('=');

            String charset = content.substring(idx+1);

            byte c[] = new byte[2];
            c[0] = ' ';
            c[1] = '\0';

            charset = StringUtils.strip(charset,"\"");

            try {
                String test = new String(c,charset);
            } catch( UnsupportedEncodingException ex ) {
                LOGGER.error("Invalid encoding: " + content + "=>'" + charset +"'", ex);
                return "ASCII";
            }

            return charset;
        }

        return "ASCII";
    }

    private byte[] getContent(Part mp) throws IOException, MessagingException
    {
        InputStream in = mp.getInputStream();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte bytes[] = new byte[1024];

        for (int len; (len = in.read(bytes)) > 0;) {
            bos.write(bytes, 0, len);
        }

        return bos.toByteArray();
    }

    private String getMime( String content_type )
    {
        int idx = content_type.indexOf('\n');
        if( idx < 0 ) {
            return content_type;
        }

        String mime =  content_type.substring(0, idx).trim();

        return StringUtils.strip_post(mime,";");
    }

    private String getFirstHeader(String headers[] )
    {
        if( headers == null ) {
            return "";
        }

        return headers[0];
    }

    private static String getAddresses( Address addresses[] )
    {
        if( addresses == null ) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        for( Address addr : addresses ) {
            if( sb.length() > 0 ) {
                sb.append(",");
            }

            sb.append(addr.toString());
        }

        return sb.toString();
    }

    private String getHeaders(Enumeration<Header> allHeaders)
    {
       StringBuilder sb = new StringBuilder();

       while( allHeaders.hasMoreElements() )
       {
           Header h = allHeaders.nextElement();
           sb.append(h.getName());
           sb.append(": ");
           sb.append(h.getValue());

           if( allHeaders.hasMoreElements() ) {
               sb.append("\r\n");
           }
       }

       return sb.toString();
    }

}
