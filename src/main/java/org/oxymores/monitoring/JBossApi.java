package org.oxymores.monitoring;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;

import org.apache.log4j.Logger;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.dmr.ModelNode;

/**
 * Utility class handling all interactions with the JBoss CLI
 */
public class JBossApi implements Closeable
{
    private static Logger log = Logger.getLogger(JBossApi.class);

    DomainClient c;
    CommandContext ctx;
    final Properties cnxProps;

    public static Properties getProperties()
    {
        // Load properties
        Properties p = new Properties();
        String file = System.getProperty("config", "conf.properties");
        InputStream props = null;
        try
        {
            props = JBossApi.class.getClassLoader().getResourceAsStream(file);
            if (props == null)
            {
                throw new RuntimeException("could not find the property file");
            }
            p.load(props);
        }
        catch (Exception e)
        {
            throw new RuntimeException("Could not load configuration file " + file, e);
        }
        finally
        {
            try
            {
                if (props != null)
                {
                    props.close();
                }
            }
            catch (IOException e)
            {
                // Nothing to do
            }
        }
        return p;
    }

    public static JBossApi create()
    {
        // Load configuration file
        Properties p = getProperties();

        // Connect to the JBoss domain controller
        JBossApi api = new JBossApi(p);
        return api;
    }

    private JBossApi(final Properties p)
    {
        this.cnxProps = p;
        reconnect();
    }

    private void reconnect()
    {
        CallbackHandler m = new CallbackHandler()
        {
            public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException
            {
                for (Callback current : callbacks)
                {
                    log.debug("Authentication callback was called with callback of type " + current.getClass());
                    if (current instanceof NameCallback)
                    {
                        NameCallback ncb = (NameCallback) current;
                        ncb.setName(cnxProps.getProperty("jboss_admin_user"));
                    }
                    else if (current instanceof PasswordCallback)
                    {
                        PasswordCallback pcb = (PasswordCallback) current;
                        pcb.setPassword(cnxProps.getProperty("jboss_admin_password").toCharArray());
                    }
                    else if (current instanceof RealmCallback)
                    {
                        RealmCallback rcb = (RealmCallback) current;
                        rcb.setText(rcb.getDefaultText());
                    }
                    else
                    {
                        throw new UnsupportedCallbackException(current);
                    }
                }
            }
        };

        try
        {
            c = DomainClient.Factory.create(InetAddress.getByName(cnxProps.getProperty("jboss_server_name")),
                    Integer.parseInt(cnxProps.getProperty("jboss_server_port")), m);
            ctx = CommandContextFactory.getInstance().newCommandContext();
            ctx.setResolveParameterValues(false);
            ctx.setSilent(true);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public synchronized void close() throws IOException
    {
        c.close();
    }

    public synchronized String runSingleQuery(String query, String attr) throws CommandFormatException, IOException
    {
        log.trace("running query " + query);
        ModelNode n = ctx.buildRequest(query);
        ModelNode rq = c.execute(n);

        if (!rq.get("outcome").asString().toUpperCase().equals("SUCCESS"))
        {
            throw new RuntimeException(rq.get("failure-description").asString());
        }

        if (attr != null)
        {
            return rq.get("result").get(attr).asString();
        }
        else
        {
            return rq.get("result").asString();
        }
    }

    public synchronized List<String> runListQuery(String query) throws CommandFormatException, IOException
    {
        log.trace("running query " + query);
        ModelNode n = ctx.buildRequest(query);
        ModelNode rq = c.execute(n);

        if (!rq.get("outcome").asString().toUpperCase().equals("SUCCESS"))
        {
            log.error(rq.get("failure-description"));
            throw new RuntimeException(rq.get("failure-description").asString());
        }

        List<String> res = new ArrayList<String>();

        for (ModelNode r : rq.get("result").asList())
        {
            res.add(r.asString());
        }

        return res;
    }
}
