package org.oxymores.monitoring;

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
        log.info("Starting JBoss Zabbix Connector");
        Runtime.getRuntime().addShutdownHook(new JBossZabbixConnector());
        cl = new ZabbixClient();
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
