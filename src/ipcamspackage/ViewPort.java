/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ipcamspackage;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import javax.imageio.ImageIO;
import javax.swing.JPanel;

/**
 *
 * @author pspkazy
 */
public class ViewPort extends JPanel {

    public ViewPort() {

    }

    Thread td;
    Stream strm;
   
    int d = 0;
    BufferedImage currentframe;
    Boolean running = false;

    public void setRunning(Boolean running) {
        this.running = running;
    }

    private static final String CONTENT_LENGTH = "Content-length: ";
    private static final String CONTENT_TYPE = "Content-type: image/jpeg";
    private InputStream urlStream;
    private ViewPort frame;
    private StringWriter stringWriter;

    public void prepare(ViewPort viewer, URL url, String username, String password) throws IOException {

        frame = viewer;
        String userpass = username + ":" + password;
        String basicAuth = "Basic " + javax.xml.bind.DatatypeConverter.printBase64Binary(userpass.getBytes());

        URLConnection urlConn = url.openConnection();
        urlConn.setRequestProperty("Authorization", basicAuth);
        urlConn.setReadTimeout(5000);
        urlConn.connect();
        urlStream = urlConn.getInputStream();
        stringWriter = new StringWriter(128);

        strm = new Stream(viewer);
        td = new Thread(strm);
    }

    public void startthread() {
        td.start();
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (running == true) {
            g.drawImage(currentframe, 0, 0, null);
            this.repaint();
        }
    }

    public void setbuffimg(BufferedImage image) {
        currentframe = image;
    }

    //=================================================
    //
    //STREAM
    //
    //=================================================
    public class Stream implements Runnable {

        private boolean processing = true;

        public Stream(ViewPort viewer) throws IOException {

        }

        /**
         * Stop the loop, and allow it to clean up
         */
        public synchronized void stop() {
            processing = false;
        }

        /**
         * Keeps running while process() returns true
         *
         * Each loop asks for the next JPEG image and then sends it to our
         * JPanel to draw
         *
         * @see java.lang.Runnable#run()
         */
        @Override
        public void run() {
            while (processing) {
                try {
                    byte[] imageBytes = retrieveNextImage();
                    ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
                    BufferedImage image = ImageIO.read(bais);
                    setbuffimg(image);                 //hook repaint method
                    repaint();
                } catch (SocketTimeoutException ste) {
                    System.err.println("failed stream read: " + ste);
                    System.out.println("Lost Camera connection: " + ste);   // hook console
                    repaint();
                    stop();
                } catch (IOException e) {
                    System.err.println("failed stream read: " + e);
                    stop();
                }
            }

            // close streams
            try {
                urlStream.close();
            } catch (IOException ioe) {
                System.err.println("Failed to close the stream: " + ioe);
            }
        }

        /**
         * Using the <i>urlStream</i> get the next JPEG image as a byte[]
         *
         * @return byte[] of the JPEG
         * @throws IOException
         */
        private byte[] retrieveNextImage() throws IOException {
            boolean haveHeader = false;
            int currByte = -1;

            String header = null;
            // build headers
            // the DCS-930L stops it's headers
            while ((currByte = urlStream.read()) > -1 && !haveHeader) {
                stringWriter.write(currByte);

                String tempString = stringWriter.toString();
                int indexOf = tempString.indexOf(CONTENT_TYPE);
                if (indexOf > 0) {
                    haveHeader = true;
                    header = tempString;
                }
            }

            // 255 indicates the start of the jpeg image
            while ((urlStream.read()) != 255) {
                // just skip extras
            }

            // rest is the buffer
            int contentLength = contentLength(header);
            byte[] imageBytes = new byte[contentLength + 1];
            // since we ate the original 255 , shove it back in
            imageBytes[0] = (byte) 255;
            int offset = 1;
            int numRead = 0;
            while (offset < imageBytes.length
                    && (numRead = urlStream.read(imageBytes, offset, imageBytes.length - offset)) >= 0) {
                offset += numRead;
            }

            stringWriter = new StringWriter(128);

            return imageBytes;
        }

        // dirty but it works content-length parsing
        private int contentLength(String header) {
            int indexOfContentLength = header.indexOf(CONTENT_LENGTH);
            int valueStartPos = indexOfContentLength + CONTENT_LENGTH.length();
            int indexOfEOL = header.indexOf('\n', indexOfContentLength);

            String lengthValStr = header.substring(valueStartPos, indexOfEOL).trim();

            int retValue = Integer.parseInt(lengthValStr);

            return retValue;
        }
    }
}

//Streamer class

