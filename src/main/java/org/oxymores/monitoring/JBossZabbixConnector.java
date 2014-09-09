package org.oxymores.monitoring;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;

import org.apache.log4j.Logger;

/**
 * The entry point of the connector. (main function + shutdown hook)
 */
public class JBossZabbixConnector extends Thread
{
    private static Logger log = Logger.getLogger(JBossZabbixConnector.class);

    private static ZabbixClient cl = null;

    public static void main(String[] args) throws Exception
    {
        if (args.length > 0)
        {
            test(args);
            return;
        }

        log.info("Starting JBoss Zabbix Connector");
        Runtime.getRuntime().addShutdownHook(new JBossZabbixConnector());
        cl = new ZabbixClient();
    }

    public static void test(String[] args)
    {
        JBossApi api = JBossApi.create();

        // Run (not thread!) a client resolution.
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ZabbixClientThread th = new ZabbixClientThread(args[0], os, api);
        th.run();
        try
        {
            System.out.println(os.toString("UTF-8"));
        }
        catch (UnsupportedEncodingException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Stop hook
     */
    @Override
    public void run()
    {
        log.info("Shutting down the connector");
        if (cl != null)
        {
            cl.stop();
        }
    }
}
