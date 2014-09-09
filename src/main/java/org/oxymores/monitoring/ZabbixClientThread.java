package org.oxymores.monitoring;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.jboss.as.cli.CommandFormatException;

public class ZabbixClientThread implements Runnable
{
    private static Logger log = Logger.getLogger(ZabbixClientThread.class);

    String key;
    OutputStream serverStream;
    JBossApi api;

    ZabbixClientThread(String key, OutputStream serverStream, JBossApi api)
    {
        this.key = key;
        this.serverStream = serverStream;
        this.api = api;
    }

    public void run()
    {
        try
        {
            // ////////////////////////////////////////////
            // Find data
            // ////////////////////////////////////////////

            String res;

            // Key analysis
            log.debug("Requested key: " + key);
            String key_root = key.split("\\[")[0];

            // Discovery?
            if ("discovery.hosts".equals(key_root))
            {
                res = Discovery.discoverHosts(api);
            }
            else if ("discovery.as".equals(key_root))
            {
                res = Discovery.discoverAs(api);
            }
            else if ("discovery.datasources".equals(key_root))
            {
                res = Discovery.discoverDatasources(api);
            }
            else if ("discovery.deployments".equals(key_root))
            {
                res = Discovery.discoverDeployments(api);
            }
            else if ("discovery.asdeployments".equals(key_root))
            {
                res = Discovery.discoverDeploymentsOnAs(api);
            }
            else if ("discovery.asdatasources".equals(key_root))
            {
                res = Discovery.discoverDatasourcesOnAs(api);
            }
            else
            {
                String[] key_args = key.split("\\[")[1].replace("]", "").split(",");

                // get the corresponding query from parameter file
                String query_raw = getQuery(key_root);
                if (query_raw == null)
                {
                    // Not supported item key
                    res = "";
                }
                else
                {
                    String attr = null;
                    log.trace("raw query: " + query_raw);
                    if (query_raw.split("!").length > 1)
                    {
                        attr = query_raw.split("!")[1];
                        query_raw = query_raw.split("!")[0];
                    }
                    String query = String.format(query_raw, (Object[]) key_args);

                    if (attr != null && attr.startsWith("sum"))
                    {
                        // Query with loops
                        attr = attr.substring(4, attr.length() - 1);
                        res = sum(query, "", Arrays.asList(attr.split(",")), api).toString();
                        attr = null;
                    }
                    else
                    {
                        // Direct query - no recursion needed
                        try
                        {
                            res = api.runSingleQuery(query, attr);
                        }
                        catch (Exception e)
                        {
                            // empty string returned = not supported.
                            res = "";
                            log.info("unsupported item requested: " + key);
                        }
                    }
                }
            }

            // ////////////////////////////////////////////
            // Send data back to Zabbix
            // ////////////////////////////////////////////

            log.trace(res);

            if (res.equals("undefined"))
            {
                res = "";
            }

            // Write ZBXD\x01
            serverStream.write(new byte[] { 0x5a, 0x42, 0x58, 0x44, 0x01 }, 0, 5);
            serverStream.flush();

            // Write data length (weird format - there should be a cleaner solution)
            String hex = Long.toHexString(res.length());
            for (int i = hex.length(); i < 16; i++)
            {
                hex = "0" + hex;
            }
            char[] hexa = hex.toCharArray();

            // Cannot use Byte for parsing - Java bytes are signed -128->127 and Zabbix needs a 0->255 byte.
            byte b1 = (byte) Integer.parseInt("" + hexa[14] + hexa[15], 16);
            byte b2 = (byte) Integer.parseInt("" + hexa[12] + hexa[13], 16);
            byte b3 = (byte) Integer.parseInt("" + hexa[10] + hexa[11], 16);
            byte b4 = (byte) Integer.parseInt("" + hexa[8] + hexa[9], 16);
            byte b5 = (byte) Integer.parseInt("" + hexa[6] + hexa[7], 16);
            byte b6 = (byte) Integer.parseInt("" + hexa[4] + hexa[5], 16);
            byte b7 = (byte) Integer.parseInt("" + hexa[2] + hexa[3], 16);
            byte b8 = (byte) Integer.parseInt("" + hexa[0] + hexa[1], 16);

            serverStream.write(new byte[] { b1, b2, b3, b4, b5, b6, b7, b8 });
            serverStream.flush();

            // Result itself
            OutputStreamWriter osw = new OutputStreamWriter(serverStream, Charset.forName("ISO-8859-1"));
            osw.write(res);
            osw.flush();

            // Done: always close the socket to free TCP resources & signal Zabbix server
            osw.close();
            serverStream.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private volatile static Map<String, String> queries = null;
    private static Date lastLoaded = new Date();
    private static URL queryFile = null;

    private static String getQuery(String keyRoot) throws IOException
    {
        if (queries == null || (queryFile != null && lastLoaded.before(new Date((new File(queryFile.getPath())).lastModified()))))
        {
            synchronized (ZabbixClientThread.class)
            {
                if (queries == null || (queryFile != null && lastLoaded.before(new Date((new File(queryFile.getPath())).lastModified()))))
                {
                    queries = new HashMap<String, String>();
                    if (queryFile == null)
                    {
                        queryFile = JBossZabbixConnector.class.getClassLoader().getResource("items.txt");
                        if (queryFile == null)
                        {
                            throw new RuntimeException("oups");
                        }
                    }
                    log.info("Reloading query file: " + queryFile.getFile());
                    lastLoaded = new Date();
                    BufferedReader br = new BufferedReader(new FileReader(new File(queryFile.getPath())));

                    String line = br.readLine();

                    while (line != null)
                    {
                        if (line.isEmpty() || "\n".equals(line) || line.startsWith("#"))
                        {
                            line = br.readLine();
                            continue;
                        }
                        String key = line.split(";")[0];
                        String q = line.split(";")[1];
                        queries.put(key, q);
                        line = br.readLine();
                    }
                    br.close();
                }
            }
        }

        return queries.get(keyRoot);
    }

    /**
     * 
     * @param query_base
     *            without a first /
     * @param root
     *            should be "" on first call
     * @param types
     * @param api
     */
    public static Long sum(String query_base, String root, List<String> types, JBossApi api) throws CommandFormatException, IOException
    {
        if (types.size() == 0)
        {
            // End of recursion: run the query!
            String query = root + "/" + query_base;
            String res = api.runSingleQuery(query, null);
            if (res.equals("undefined"))
            {
                // The given item may not exist on all loops
                return 0L;
            }
            else
            {
                return Long.parseLong(res);
            }
        }

        Long res = 0L;
        String typed = types.get(0);
        String addRoot = typed.split(":")[0];
        String type = typed.split(":")[1];
        List<String> remaingintypes = types.subList(1, types.size());

        String query = root + addRoot + ":read-children-names(child-type=" + type + ")";
        for (String val : api.runListQuery(query))
        {
            res += sum(query_base, root + addRoot + type + "=" + val, remaingintypes, api);
        }
        return res;
    }
}
