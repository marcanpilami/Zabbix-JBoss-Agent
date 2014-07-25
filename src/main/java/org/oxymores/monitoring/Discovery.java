package org.oxymores.monitoring;

import java.io.IOException;
import java.util.List;

import org.jboss.as.cli.CommandFormatException;

public class Discovery
{
    public static String discoverHosts(JBossApi api) throws CommandFormatException, IOException
    {
        String res = "{ \"data\": [";
        List<String> nodes = api.runListQuery("/:read-children-names(child-type=host)");

        for (String s : nodes)
        {
            res += " { \"{#ASHOST}\":\"" + s + "\"},";
        }

        res = res.substring(0, res.length() - 1) + "]}";

        return res;

        // return res;
    }

    public static String discoverAs(JBossApi api) throws CommandFormatException, IOException
    {
        String res = "{ \"data\": [";
        List<String> nodes = api.runListQuery("/:read-children-names(child-type=host)");

        for (String s : nodes)
        {
            List<String> ass = api.runListQuery("/host=" + s + "/:read-children-names(child-type=server-config)");
            for (String a : ass)
            {
                res += " { \"{#ASHOST}\":\"" + s + "\", \"{#ASNAME}\":\"" + a + "\"},";
            }
        }

        res = res.substring(0, res.length() - 1) + "]}";

        return res;
    }

    public static String discoverDatasources(JBossApi api) throws CommandFormatException, IOException
    {
        String res = "{ \"data\": [";
        List<String> nodes = api.runListQuery("/:read-children-names(child-type=profile)");

        for (String s : nodes)
        {
            List<String> ass = api.runListQuery("/profile=" + s + "/subsystem=datasources/:read-children-names(child-type=data-source)");
            for (String a : ass)
            {
                res += " { \"{#DSPROFILE}\":\"" + s + "\", \"{#DSNAME}\":\"" + a + "\"},";
            }
        }

        res = res.substring(0, res.length() - 1) + "]}";

        return res;
    }

    public static String discoverDeployments(JBossApi api) throws CommandFormatException, IOException
    {
        String res = "{ \"data\": [";
        List<String> nodes = api.runListQuery("/:read-children-names(child-type=server-group)");

        for (String s : nodes)
        {
            List<String> ass = api.runListQuery("/server-group=" + s + "/:read-children-names(child-type=deployment)");
            for (String a : ass)
            {
                res += " { \"{#SRVGROUP}\":\"" + s + "\", \"{#DPNAME}\":\"" + a + "\"},";
            }
        }

        res = res.substring(0, res.length() - 1) + "]}";

        return res;
    }
    
    public static String discoverDeploymentsOnAs(JBossApi api) throws CommandFormatException, IOException
    {
        String res = "{ \"data\": [";
        List<String> nodes = api.runListQuery("/:read-children-names(child-type=host)");

        for (String s : nodes)
        {
            List<String> ass = api.runListQuery("/host=" + s + "/:read-children-names(child-type=server)");
            for (String a : ass)
            {
                List<String> dps = api.runListQuery("/host=" + s + "/server=" + a + "/:read-children-names(child-type=deployment)");
                for (String d : dps)
                {
                    res += " { \"{#DPHOST}\":\"" + s + "\", \"{#DPAS}\":\"" + a + "\", \"{#DPNAME}\":\"" + d + "\"},";
                }
                
            }
        }

        res = res.substring(0, res.length() - 1) + "]}";
        return res;
    }
    
    public static String discoverDatasourcesOnAs(JBossApi api) throws CommandFormatException, IOException
    {
        String res = "{ \"data\": [";
        List<String> nodes = api.runListQuery("/:read-children-names(child-type=host)");

        for (String s : nodes)
        {
            List<String> ass = api.runListQuery("/host=" + s + "/:read-children-names(child-type=server)");
            for (String a : ass)
            {
                List<String> dps = api.runListQuery("/host=" + s + "/server=" + a + "/subsystem=datasources/:read-children-names(child-type=data-source)");
                for (String d : dps)
                {
                    res += " { \"{#DSHOST}\":\"" + s + "\", \"{#DSAS}\":\"" + a + "\", \"{#DSNAME}\":\"" + d + "\"},";
                }
                
            }
        }

        res = res.substring(0, res.length() - 1) + "]}";
        return res;
    }
}
