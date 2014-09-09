package org.oxymores.monitoring;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ServerSocketFactory;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

/**
 * This class represents a TCP server that implements the Zabbix Client protocol. It therefore can be queries directly by a Zabbix server.
 * Parameters are found inside the conf.properties file.
 */
public class ZabbixClient
{
    private static Logger log = Logger.getLogger(ZabbixClient.class);

    private boolean run = true;
    private ServerSocket serverSocket;

    ZabbixClient() throws UnknownHostException, IOException
    {
        // Connect to the domain controller
        JBossApi api = JBossApi.create();
        Properties p = JBossApi.getProperties();

        // Utility items
        StringWriter writer = null;
        Socket clientSocket = null;
        InputStream iss = null;

        // Create Zabbix listener
        ExecutorService pool = Executors.newFixedThreadPool(Integer.parseInt(p.getProperty("thread_pool", "10")));
        serverSocket = ServerSocketFactory.getDefault().createServerSocket(Integer.parseInt(p.getProperty("local_port", "9752")));

        // Wait for connections
        while (run)
        {
            clientSocket = serverSocket.accept();

            // Read the key from the stream. It is ended by \n (0x10) and can optionally have a ZBXD0x01 header
            writer = new StringWriter();
            iss = clientSocket.getInputStream();
            int filterOut = 0;
            while (true)
            {
                byte b = (byte) iss.read();
                if (filterOut > 0)
                {
                    filterOut--;
                    continue;
                }
                if (b == -1 || b == 10)
                {
                    // \n or end of stream will end the key.
                    break;
                }
                if (b == 1)
                {
                    // Happens from zabbix sender: the ZBX header is sent by the getter. In that case, ignore length (8 next bytes).
                    writer = new StringWriter();
                    filterOut = 8;
                    continue;
                }

                writer.write(b);
            }
            // Don't close the stream - it would close the socket.

            // Create a thread to serve the key
            clientSocket.setSoTimeout(10000);
            clientSocket.setTcpNoDelay(true);
            pool.submit(new ZabbixClientThread(writer.toString(), clientSocket.getOutputStream(), api));
        }

        log.info("Zabbix client has shut down");
    }

    /**
     * Currently unused. The agent may one day use active items.
     */
    void activeChecksInit(String zabbixdns, int port, String hostname) throws IOException
    {
        Socket zabbix_server = new Socket(zabbixdns, port);
        zabbix_server.setSoTimeout(3000);
        OutputStreamWriter osw = new OutputStreamWriter(zabbix_server.getOutputStream());
        osw.write("{\"request\":\"active checks\",\"host\":\"" + hostname + "\"}");
        osw.flush();

        InputStream is = zabbix_server.getInputStream();
        StringWriter writer = new StringWriter();
        IOUtils.copy(is, writer);
        String res = writer.toString();
        System.out.println(res);
        writer.close();
        is.close();
        osw.close();
        zabbix_server.close();
    }

    void stop()
    {
        this.run = false;
        try
        {
            serverSocket.close();
        }
        catch (IOException e)
        {
            log.warn("During shutdown, the server socket had a problem to close: ", e);
        }
    }
}
