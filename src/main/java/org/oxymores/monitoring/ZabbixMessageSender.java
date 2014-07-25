package org.oxymores.monitoring;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.Charset;

/**
 * For zabbix_sender usage. Not used in this program any more.
 * 
 */
public class ZabbixMessageSender
{
    private String answer = "{\"request\":\"sender data\", \"data\":[";

    public String getMessage()
    {
        // -1 is for final comma
        return answer.substring(0, answer.length() - 1) + "]}";
    }

    public void addItem(String host, String key, String value)
    {
        answer = answer + "{\"host\": \"" + host + "\",\"key\": \"" + key + "\",\"value\": \"" + value + "\"},";
    }

    public void send(String zabbixdns, int port) throws IOException
    {
        Socket zabbix_server = new Socket(zabbixdns, port);
        zabbix_server.setSoTimeout(3000);
        OutputStreamWriter osw = new OutputStreamWriter(zabbix_server.getOutputStream(), Charset.forName("ISO-8859-1"));
        osw.write(this.getMessage());
        osw.flush();

        InputStream is = zabbix_server.getInputStream();

        is.close();
        osw.close();
        zabbix_server.close();
    }
}
