# JBoss Zabbix Agent

This is a small utility that allows to easily monitor JBoss AS 7 (&8) inside Zabbix. It is basically a dedicated Zabbix agent written in Java
that translates Zabbix keys into (remote) JBoss CLI commands and returns the results. It has no dependencies whatsoever.

Agent Installation
*************************

The agent runs on any machine (Windows, Unix) that has Java 6+ and can connect to the native administration port of JBoss (usually 9999).
It does not have to be on the same server as JBoss.

* Select the account that should run the agent. Default is zabbix. 
* Using that account, unzip the file onto your system (Windows or Linux). 
* Modify conf/conf.properties: you must specify the remote (or local) JBoss server or domain controller with a valid JBoss account
* Run bin/connector.sh start (it will create a daemon)

Starting multiple agents
****************************

By default, connector.sh will use the configuration file conf/conf.properties to identify the JBoss server to connect to.
You can also specify environment variable CONFFILE before running connector.sh to specify a different configuration file:

    export CONFFILE="/path/to/conf.properties"
    ./bin/connector.sh start

This allows to run multiple agents monitoring each a different Jboss cluster with a single install.
Please note that all agents will share the same query definition file (items.txt).

Template import
******************

Inside the zip, there is a template/zabbix_template.xml file. Just import it inside Zabbix 2.0+ and associate it with a host.

The host must have an agent interface corresponding to the agent installed in the previous section.


Discovery
**************

The given template relies on low level item discovery (lld/llid) to discover all servers inside a domain, all deployments, etc. Please look
at the template lld configuration if you want to reuse this.

Extending
**************

The agent comes with all the CLI queries corresponding to the given template. It can be extended: just edit the file conf/items.txt
and add queries in the form:

    zabbix_key;/my/jboss/cli/query:read-attribute()

Inside the JBoss query, you can use %1$s, %2$s, etc... to designate the arguments of the Zabbix key. E.g.: 

    jboss.jvm.status;/host=%2$s/server-config=%1$s:read-attribute(name=status)

will work for the Zabbix key jboss.jvm.status[MyAsName,MyHostName]

In case the attribute you seek is not readable by read-attribute but only as a subproperty of another property, you can use this 
(in this example, we get the "used" sub property of property heap-memory-usage):

    jboss.jvm.memory.heap.used;/host=%2$s/server=%1$s/core-service=platform-mbean/type=memory:read-attribute(name=heap-memory-usage)!used

