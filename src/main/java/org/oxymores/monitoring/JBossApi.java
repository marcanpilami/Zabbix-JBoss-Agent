package org.oxymores.monitoring;

import java.io.Closeable;
import java.io.IOException;
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

    public JBossApi(final Properties p)
    {
        CallbackHandler m = new CallbackHandler()
        {
            public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException
            {
                for (Callback current : callbacks)
                {
                    log.debug("Authentication callback was called with vallback of type " + current.getClass());
                    if (current instanceof NameCallback)
                    {
                        NameCallback ncb = (NameCallback) current;
                        ncb.setName(p.getProperty("jboss_admin_user"));
                    }
                    else if (current instanceof PasswordCallback)
                    {
                        PasswordCallback pcb = (PasswordCallback) current;
                        pcb.setPassword(p.getProperty("jboss_admin_password").toCharArray());
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
            /*
             * URL f = this.getClass().getClassLoader().getResource("jboss-cli.xml"); if (f != null) {
             * log.info("Using JBoss CLI configuration file inside " + (new File(f.getPath()).getParentFile()));
             * System.setProperty("user.dir", (new File(f.getPath()).getParentFile()).getAbsolutePath()); }
             */
            c = DomainClient.Factory.create(InetAddress.getByName(p.getProperty("jboss_server_name")),
                    Integer.parseInt(p.getProperty("jboss_server_port")), m);
            ctx = CommandContextFactory.getInstance().newCommandContext();
            ctx.setResolveParameterValues(false);
            ctx.setSilent(true);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public void close() throws IOException
    {
        c.close();
    }

    public String runSingleQuery(String query, String attr) throws CommandFormatException, IOException
    {
        synchronized (ctx)
        {
            log.trace("running query " + query);
            ModelNode n = ctx.buildRequest(query);
            ModelNode res = c.execute(n);
            if (attr != null)
            {
                return res.get("result").get(attr).asString();
            }
            else
            {
                return res.get("result").asString();
            }
        }
    }

    public List<String> runListQuery(String query) throws CommandFormatException, IOException
    {
        synchronized (ctx)
        {
            log.trace("running query " + query);
            ModelNode n = ctx.buildRequest(query);
            ModelNode rq = c.execute(n);

            List<String> res = new ArrayList<String>();

            for (ModelNode r : rq.get("result").asList())
            {
                res.add(r.asString());
            }

            return res;
        }
    }
}
