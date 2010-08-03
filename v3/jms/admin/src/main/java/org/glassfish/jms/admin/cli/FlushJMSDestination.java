/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2010 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.glassfish.jms.admin.cli;

import org.glassfish.api.I18n;
import org.glassfish.api.Param;
import org.glassfish.api.ActionReport;
import org.glassfish.api.admin.AdminCommand;
import org.glassfish.api.admin.AdminCommandContext;
import org.glassfish.internal.api.ServerContext;
import org.glassfish.internal.api.Globals;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.List;

import com.sun.enterprise.config.serverbeans.Cluster;
import com.sun.enterprise.util.LocalStringManagerImpl;
import com.sun.enterprise.util.SystemPropertyConstants;
import com.sun.enterprise.config.serverbeans.*;
import com.sun.enterprise.connectors.jms.util.JmsRaUtil;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.MalformedObjectNameException;

import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.annotations.Scoped;
import org.jvnet.hk2.annotations.Inject;
import org.jvnet.hk2.component.PerLookup;

import com.sun.logging.LogDomains;

//import org.glassfish.api.admin.Cluster;
import org.glassfish.config.support.CommandTarget;
import org.glassfish.config.support.TargetType;
import org.glassfish.api.admin.RuntimeType;
import org.glassfish.api.admin.ServerEnvironment;


/**
 * Flush JMS Destination
 *
 * 
 */
@Service(name="flush-jmsdest")
@Scoped(PerLookup.class)
@I18n("flush.jms.dest")
@org.glassfish.api.admin.Cluster({RuntimeType.DAS})
@TargetType({CommandTarget.DAS,CommandTarget.STANDALONE_INSTANCE,CommandTarget.CLUSTER,CommandTarget.CONFIG})

public class FlushJMSDestination extends JMSDestination implements AdminCommand {


        static Logger logger = LogDomains.getLogger(FlushJMSDestination.class,LogDomains.ADMIN_LOGGER);
        final private static LocalStringManagerImpl localStrings = new LocalStringManagerImpl(FlushJMSDestination.class);
         private static final String DESTINATION_CONFIG_DOMAIN_TYPE
    			= MBEAN_DOMAIN_NAME
				+ ":type=" + "Destination"
				+ ",subtype=Config";

        @Param(name="destType", shortName="T", optional=false)
        String destType;

        @Param(name="dest_name", primary=true)
        String destName;

        @Param(optional=true)
        String target = SystemPropertyConstants.DEFAULT_SERVER_INSTANCE_NAME;

        @Inject
        com.sun.appserv.connectors.internal.api.ConnectorRuntime connectorRuntime;

        @Inject
        Domain domain;

        @Inject(name = ServerEnvironment.DEFAULT_INSTANCE_NAME)
        Config config;

        @Inject
        ServerContext serverContext;


        public void execute(AdminCommandContext context) {

            final ActionReport report = context.getActionReport();
            logger.entering(getClass().getName(), "flushJMSDestination",
            new Object[] {destName, destType});

             try{
                validateJMSDestName(destName);
                validateJMSDestType(destType);
            }catch (IllegalArgumentException e){
                report.setMessage(e.getMessage());
                report.setActionExitCode(ActionReport.ExitCode.FAILURE);
                return;
            }

            try {
                    flushJMSDestination(destName, destType, target);
                    return;
            } catch (Exception e) {
                logger.throwing(getClass().getName(), "flushJMSDestination", e);
                //e.printStackTrace();//handleException(e);
                report.setMessage(localStrings.getLocalString("flush.jms.dest.failed",
                                "Flush JMS Destination failed", e.getMessage()));
               report.setActionExitCode(ActionReport.ExitCode.FAILURE);
            }
         }

           // delete-jmsdest
        private void flushJMSDestination(String destName, String destType, String tgtName)
            throws Exception {

            logger.log(Level.FINE, "FlushJMSDestination ...");

            //MBeanServerConnection  mbsc = getMBeanServerConnection(tgtName);
            // check and use JMX
        try {
            CommandTarget ctarget = getTypeForTarget(target);
            if (ctarget == CommandTarget.CLUSTER || ctarget == CommandTarget.CLUSTERED_INSTANCE) {
            /* The MQ 4.1 JMX Apis do not clean up all
                * the destintations in all the instances
                 * in a broker cluster, in other words, JMX
                * operation purge is not cluster aware
                * So we have to ensure that we purge each instance
                * in the cluster one by one.
                * If one of them fail just log and proceed, we will
                * flag an error towards the end. Issue 6523135
                    * This works because we resolve the port numbers
                * even for standalone instances in MQAddressList.
                  */
               boolean success = true;
               Cluster cluster = null;
              if (ctarget == CommandTarget.CLUSTER){
                  cluster = Globals.get(Domain.class).getClusterNamed(target);
              }else {
                   List clustersList = Globals.get(Domain.class).getClusters().getCluster();
                   cluster = JmsRaUtil.getClusterForServer(clustersList, target);
              }
               List servers =cluster.getInstances(); //target.getServer();
               for (int server = 0; server < servers.size(); server++) {
                   try {
                     purgeJMSDestination(destName, destType, ((Server)servers.get(server)).getName());
                   } catch (Exception e) {
                   success = false;
                       //todo: enable localized string
                       logger.log(Level.SEVERE,/*localStrings.getLocalString("admin.mbeans.rmb.error_purging_jms_dest") +*/ ((Server)servers.get(server)).getName());
                       }
               }
               if (!success) {
                   //todo: enable localized string
                   throw new Exception();//localStrings.getLocalString("admin.mbeans.rmb.error_purging_jms_dest"));
               }

               } else {
                    purgeJMSDestination(destName, destType, tgtName);
        }

        } catch (Exception e) {
            logger.throwing(getClass().getName(), "flushJMSDestination", e);
            handleException(e);
        }
     }

       public void purgeJMSDestination(String destName, String destType, String tgtName)
               throws Exception { {

             logger.log(Level.FINE, "purgeJMSDestination ...");
              MQJMXConnectorInfo mqInfo = getMQJMXConnectorInfo(target, config, serverContext, domain, connectorRuntime);

               try {

                   MBeanServerConnection mbsc = mqInfo.getMQMBeanServerConnection();

                    if (destType.equalsIgnoreCase("topic")) {
                        destType = DESTINATION_TYPE_TOPIC;
                    } else if (destType.equalsIgnoreCase("queue")) {
                        destType = DESTINATION_TYPE_QUEUE;
                    }
                   ObjectName on = createDestinationConfig(destType, destName);
                                      String [] signature = null;
                                      Object [] params = null;

                    mbsc.invoke(on, "purge", params, signature);
               } catch (Exception e) {
                           //log JMX Exception trace as WARNING
                           logAndHandleException(e, "admin.mbeans.rmb.error_purging_jms_dest");
                       } finally {
                           try {
                               if(mqInfo != null) {
                                   mqInfo.closeMQMBeanServerConnection();
                               }
                           } catch (Exception e) {
                             handleException(e);
                           }
                       }
           }
       }
    private ObjectName createDestinationConfig(String destinationType,
					String destinationName)
				throws MalformedObjectNameException,
					NullPointerException  {
	String s = DESTINATION_CONFIG_DOMAIN_TYPE
			+ ",desttype="
			+ destinationType
			+ ",name="
			+ ObjectName.quote(destinationName);

	ObjectName o = new ObjectName(s);

	return (o);
    }

}
