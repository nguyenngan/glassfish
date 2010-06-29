/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2010 Sun Microsystems, Inc. All rights reserved.
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
package com.sun.enterprise.v3.admin.cluster;

import com.sun.enterprise.config.serverbeans.*;
import com.sun.enterprise.config.serverbeans.Cluster;
import com.sun.enterprise.util.StringUtils;
import com.sun.enterprise.util.SystemPropertyConstants;
import com.sun.enterprise.util.cluster.InstanceInfo;
import java.util.*;
import java.util.logging.*;
import org.glassfish.api.ActionReport;
import org.glassfish.api.ActionReport.ExitCode;
import org.glassfish.api.I18n;
import org.glassfish.api.Param;
import org.glassfish.api.admin.AdminCommand;
import org.glassfish.api.admin.AdminCommandContext;
import org.jvnet.hk2.annotations.*;
import org.glassfish.api.admin.ServerEnvironment;
import org.glassfish.api.admin.config.ReferenceContainer;
import org.jvnet.hk2.component.*;

/**
 * AdminCommand to list all instances and their states
 *
 * @author Byron Nevins
 */
@Service(name = "list-instances")
@I18n("list.instances.command")
@Scoped(PerLookup.class)
public class ListInstancesCommand implements AdminCommand {
    //@Inject(name = ServerEnvironment.DEFAULT_INSTANCE_NAME)
    //private Server dasServer;

    @Inject
    private Domain domain;
    @Inject
    private ServerEnvironment env;
    @Inject
    private Servers allServers;
    @Inject
    private Configs configs;
    @Param(optional = true, defaultValue = "2000")
    private String timeoutmsec;
    @Param(optional = true, defaultValue = "false")
    private boolean standaloneonly;
    @Param(optional = true, defaultValue = "false")
    private boolean nostatus;
    @Param(optional = true, primary = true)
    String target;
    private List<InstanceInfo> infos = new LinkedList<InstanceInfo>();
    private List<Server> serverList;

    // if showDas is true then they entered the string "server" as the target
    // this is weird but stipulated by IT 12104
    private boolean showDas = false;

    @Override
    public void execute(AdminCommandContext context) {
        // setup
        int timeoutInMsec;
        try {
            timeoutInMsec = Integer.parseInt(timeoutmsec);
        }
        catch (Exception e) {
            timeoutInMsec = 2000;
        }

        ActionReport report = context.getActionReport();
        Logger logger = context.getLogger();
        serverList = createServerList();

        if (serverList == null) {
            report.setActionExitCode(ActionReport.ExitCode.FAILURE);
            report.setMessage(Strings.get("list.instances.badTarget", target));
            return;
        }
        // Require that we be a DAS
        if (!env.isDas()) {
            String msg = Strings.get("list.instances.onlyRunsOnDas");
            logger.warning(msg);
            report.setActionExitCode(ExitCode.FAILURE);
            report.setMessage(msg);
            return;
        }

        if (nostatus)
            noStatus(report, serverList);
        else
            yesStatus(report, serverList, timeoutInMsec, logger);

        report.setActionExitCode(ExitCode.SUCCESS);

    }

    private void noStatus(ActionReport report, List<Server> serverList) {
        if (serverList.size() < 1) {
            report.addSubActionsReport().setMessage(NONE);
        }
        else
            for (Server server : serverList) {
                boolean clustered = server.getCluster() != null;

                if (standaloneonly && clustered)
                    continue;

                String name = server.getName();

                if (showDas || notDas(name))
                    report.addSubActionsReport().setMessage(name);
            }
    }

    private boolean notDas(String name) {
        return !SystemPropertyConstants.DAS_SERVER_NAME.equals(name);
    }

    private void yesStatus(ActionReport report, List<Server> serverList, int timeoutInMsec, Logger logger) {
        // Gather a list of InstanceInfo -- one per instance in domain.xml
        RemoteInstanceCommandHelper helper = new RemoteInstanceCommandHelper(env, serverList, configs);

        for (Server server : serverList) {
            boolean clustered = server.getCluster() != null;

            if (standaloneonly && clustered)
                continue;

            String name = server.getName();
            if (name == null)
                continue;   // can this happen?!?

            // skip DAS maybe
            if (showDas || notDas(name)) {
                InstanceInfo ii = new InstanceInfo(
                        name, helper.getAdminPort(server), helper.getHost(server),
                        logger, timeoutInMsec);
                infos.add(ii);
            }
        }
        if (infos.size() < 1)
            report.addSubActionsReport().setMessage(NONE);
        else
            for (InstanceInfo ii : infos) {
                String s = ii.isRunning() ? RUNNING : NOT_RUNNING;
                report.addSubActionsReport().setMessage(ii.getName() + " " + s);
            }
    }

    /*
     * return null means the target is garbage
     * return empty list means the target was an empty cluster
     */
    private List<Server> createServerList() {
        // 1. no target specified
        if (!StringUtils.ok(target))
            return allServers.getServer();

        if (target.equals(SystemPropertyConstants.DAS_SERVER_NAME))
            showDas = true;

        // what is it?!?
        ReferenceContainer rc = domain.getReferenceContainerNamed(target);
        // 2. the name of a node
        if (rc == null) {
            return getServersForNode(target);
        }
        else if (rc.isServer()) {
            List<Server> l = new LinkedList<Server>();
            l.add((Server) rc);
            return l;
        }
        else if (rc.isCluster()) { // can't be anything else currently! (June 2010)
            Cluster cluster = (Cluster) rc;
            return cluster.getInstances();
        }
        else
            return null;
    }

    private List<Server> getServersForNode(String nodeName) {
        boolean foundNode = false;
        Nodes nodes = domain.getNodes();

        if (nodes != null) {
            List<Node> nodeList = nodes.getNode();
            if (nodeList != null) {
                for (Node node : nodeList) {
                    if (nodeName.equals(node.getName())) {
                        foundNode = true;
                        break;
                    }
                }
            }
        }
        if (!foundNode)
            return null;
        else
            return domain.getInstancesOnNode(target);
    }
    // these are all not localized because REST etc. depends on them.
    private static final String NONE = "Nothing to list.";
    private static final String RUNNING = "running";
    private static final String NOT_RUNNING = "not running";
}
