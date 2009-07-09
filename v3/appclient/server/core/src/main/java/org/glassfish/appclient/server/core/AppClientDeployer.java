/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2009 Sun Microsystems, Inc. All rights reserved.
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
package org.glassfish.appclient.server.core;

import com.sun.enterprise.config.serverbeans.Applications;
import com.sun.enterprise.module.Module;
import java.io.IOException;
import java.util.jar.Manifest;
import org.glassfish.api.container.EndpointRegistrationException;
import org.glassfish.appclient.server.core.jws.NamingConventions;
import org.glassfish.appclient.server.core.jws.servedcontent.FixedContent;
import org.glassfish.deployment.common.DownloadableArtifacts;
import com.sun.enterprise.config.serverbeans.Domain;
import com.sun.enterprise.deployment.Application;
import com.sun.enterprise.deployment.ApplicationClientDescriptor;
import com.sun.enterprise.deployment.archivist.AppClientArchivist;
import com.sun.enterprise.module.ModulesRegistry;
import com.sun.logging.LogDomains;
import java.beans.PropertyChangeEvent;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.api.container.RequestDispatcher;
import org.glassfish.api.deployment.DeployCommandParameters;
import org.glassfish.internal.api.ServerContext;
import org.glassfish.api.deployment.DeploymentContext;
import org.glassfish.api.deployment.MetaData;
import org.glassfish.api.deployment.UndeployCommandParameters;
import org.glassfish.appclient.server.core.jws.RestrictedContentAdapter;
import org.glassfish.appclient.server.core.jws.servedcontent.StaticContent;
import org.glassfish.deployment.common.DeploymentException;
import org.glassfish.javaee.core.deployment.JavaEEDeployer;
import org.jvnet.hk2.annotations.Inject;
import org.jvnet.hk2.annotations.Scoped;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.component.PostConstruct;
import org.jvnet.hk2.component.Singleton;
import org.jvnet.hk2.config.ConfigListener;
import org.jvnet.hk2.config.UnprocessedChangeEvent;
import org.jvnet.hk2.config.UnprocessedChangeEvents;

/**
 * AppClient module deployer.
 * <p>
 * Prepares JARs for download to the admin client and tracks which JARs should
 * be downloaded for each application.  (Downloads occur during
 * <code>deploy --retrieve</code> or <code>get-client-stubs</code> command
 * processing, or during Java Web Start launches of app clients.  Also creates
 * AppClientServerApplication instances for each client to provide Java Web Start
 * support.
 * <p>
 * Main responsibilities:
 * <ul>
 * <li>create a new facade JAR for each of the developer's original app client
 * JARs, and
 * <li>create a new facade JAR for the EAR (if the app client is part of an EAR), and
 * <li>manage internal data structures that map each deployed app to the app
 * client-related JARs that should be downloaded for that app.
 * </ul>
 * Each app client facade JAR contains:
 * <ul>
 * <li>a manifest which:
 *      <ul>
 *      <li>lists the GlassFish app client facade class as the Main-Class
 *      <li>contains a Class-Path entry referring to the developer's original JAR
 * and any JARs in the EAR's library directory,
 *      <li>contains a GlassFish-specific item that is a relative URI pointing to the
 * corresponding original JAR
 *      <li>contains a GlassFish-specific item identifying the main class in the
 * developer's original JAR
 *      <li>contains a copy of the SplashScreen-Image item from the developer's
 * original JAR, if there is one
 *      </ul>
 * <li>the app client facade main class that prepares the ACC runtime environment before
 * transferring control to the developer's main class
 * <li>a copy of the splash screen image from the developer's original JAR, if
 * there is one
 * </ul>
 *
 * If the app client being deployed is part of an EAR, then the EAR facade
 * represents an "app client group" and contains:
 * <ul>
 * <li>a manifest which:
 *      <ul>
 *      <li>declares the GlassFish EAR facade class as the Main-Class
 *      <li>lists the URIs to the individual app client facade JARs in a
 * GlassFish-specific item
 *      </ul>
 * <li>the GlassFish app client group facade main class
 * </ul>
 *<p>
 * For backward compatibility, the generated facade JAR is named
 * ${appName}Client.jar and is downloaded into the local directory the user
 * specifies on the <code>deploy --retrieve</code> or <code>get-client-stubs</code>
 * command.  Other JARs - the developer's original app client JAR(s)
 * and any required library JARs - are downloaded into a subdirectory within
 * that local directory named ${appName}Client.  This segregates the files for
 * different clients into different subdirectories to avoid naming collisions if
 * the user downloads multiple clients into the same local directory.
 *
 * @author tjquinn
 * 
 */
@Service
@Scoped(Singleton.class)
public class AppClientDeployer
        extends JavaEEDeployer<AppClientContainerStarter, AppClientServerApplication>
        implements PostConstruct, ConfigListener {

    private Logger logger;

    public static final String APPCLIENT_FACADE_CLASS_FILE = "org/glassfish/appclient/client/AppClientFacade.class";
    public static final String APPCLIENT_COMMAND_CLASS_NAME = "org.glassfish.appclient.client.AppClientFacade";
    public static final Attributes.Name GLASSFISH_APPCLIENT_MAIN_CLASS =
            new Attributes.Name("GlassFish-AppClient-Main-Class");

    public static final Attributes.Name GLASSFISH_APPCLIENT =
            new Attributes.Name("GlassFish-AppClient");

    public static final Attributes.Name SPLASH_SCREEN_IMAGE =
            new Attributes.Name("SplashScreen-Image");

    private static final String GF_CLIENT_MODULE_NAME = "org.glassfish.appclient.gf-client-module";

    /** Save the helper across phases in the deployment context's appProps */
    public static final String HELPER_KEY_NAME = "org.glassfish.appclient.server.core.helper";


    @Inject
    protected ServerContext sc;

    @Inject
    protected Domain domain;

    @Inject
    private ServerContext serverContext;

    @Inject
    private DownloadableArtifacts downloadInfo;

    @Inject
    private ModulesRegistry modulesRegistry;

    @Inject
    private Applications applications;

    @Inject
    private RequestDispatcher requestDispatcher;

    /** the class loader which knows about the org.glassfish.appclient.gf-client-module */
    private ClassLoader gfClientModuleClassLoader;


    private final ConcurrentHashMap<String,RestrictedContentAdapter> earLevelAdapters = new
            ConcurrentHashMap<String, RestrictedContentAdapter>();

    /*
     * Each app client server application will listen for config change
     * events - for creation, deletion, or change of java-web-start-enabled
     * property settings.  Because they are not handled as services hk2 will
     * not automatically register them for notification.  This deployer, though,
     * is a service and so by implementing ConfigListener is registered
     * by hk2 automatically for config changes.  The following Set collects
     * all app client server applications so the deployer can forward
     * notifications to each app client server app.
     */
    final private Set<AppClientServerApplication> appClientApps =
            new HashSet<AppClientServerApplication>();

    private URI installRootURI;
    private URI umbrellaRootURI;

    public AppClientDeployer() {
    }

    protected String getModuleType() {
        return "appclient";
    }

    public void postConstruct() {
        logger = LogDomains.getLogger(AppClientDeployer.class, LogDomains.ACC_LOGGER);
        for (Module module : modulesRegistry.getModules(GF_CLIENT_MODULE_NAME)) {
            gfClientModuleClassLoader = module.getClassLoader();
        }
        installRootURI = serverContext.getInstallRoot().toURI();
        umbrellaRootURI = new File(installRootURI).getParentFile().toURI();

        startSystemContentAdapter();
    }

    @Override
    public MetaData getMetaData() {
        return new MetaData(false, null, new Class[]{Application.class});
    }

    @Override
    public AppClientServerApplication load(AppClientContainerStarter containerStarter, DeploymentContext dc) {
        AppClientDeployerHelper helper = savedHelper(dc);
        final AppClientServerApplication newACServerApp =
                new AppClientServerApplication(dc, this, helper,
                requestDispatcher, applications, logger);
        appClientApps.add(newACServerApp);
        return newACServerApp;
    }

    public void unload(AppClientServerApplication application, DeploymentContext dc) {
        appClientApps.remove(application);
    }

    /**
     * Clean any files and artifacts that were created during the execution
     * of the prepare method.
     *
     * @param dc deployment context
     */
    @Override
    public void clean(DeploymentContext dc) {
        super.clean(dc);
        UndeployCommandParameters params = dc.getCommandParameters(UndeployCommandParameters.class);
        downloadInfo.clearArtifacts(params.name);
    }

    synchronized void addEARLevelContent(
            final String appName, final String uriString, final StaticContent content) throws EndpointRegistrationException, IOException {
        RestrictedContentAdapter adapter = earLevelAdapters.get(appName);
        if (adapter == null) {
            adapter = new RestrictedContentAdapter();
            earLevelAdapters.put(appName, adapter);
            requestDispatcher.registerEndpoint(contextRootForEARAdapter(appName), adapter, null);
        }
        adapter.addContentIfAbsent(uriString, content);
    }

    private String contextRootForEARAdapter(final String appName) {
        return NamingConventions.JWSAPPCLIENT_APP_PREFIX + "/" + appName;
    }

    @Override
    protected void generateArtifacts(DeploymentContext dc) throws DeploymentException {
        DeployCommandParameters params = dc.getCommandParameters(DeployCommandParameters.class);

        try {
            final AppClientArchivist archivist = habitat.getComponent(AppClientArchivist.class);
            AppClientDeployerHelper helper = createAndSaveHelper(
                    dc, archivist, gfClientModuleClassLoader);
            helper.prepareJARs();
            downloadInfo.addArtifacts(params.name(), helper.earLevelDownloads());
            downloadInfo.addArtifacts(params.name(), helper.clientLevelDownloads());
        } catch (Exception ex) {
            throw new DeploymentException(ex);
        }
    }
    
    private AppClientDeployerHelper createAndSaveHelper(final DeploymentContext dc,
            final AppClientArchivist archivist, final ClassLoader clientModuleLoader) throws IOException {
        final AppClientDeployerHelper h =
            AppClientDeployerHelper.newInstance(dc, archivist, clientModuleLoader);
        dc.addTransientAppMetaData(HELPER_KEY_NAME + moduleURI(dc), h.proxy());
        return h;
    }

    private AppClientDeployerHelper savedHelper(final DeploymentContext dc) {
        final String key = HELPER_KEY_NAME + moduleURI(dc);
        AppClientDeployerHelper h = dc.getTransientAppMetaData(key,
                AppClientDeployerHelper.Proxy.class).helper();
        if (h == null) {
            h = dc.getTransientAppMetaData(key,
                    StandaloneAppClientDeployerHelper.class);
        }
        return h;
    }

    private String moduleURI(final DeploymentContext dc) {
        ApplicationClientDescriptor acd = dc.getModuleMetaData(ApplicationClientDescriptor.class);
        return acd.getModuleDescriptor().getArchiveUri();
    }

    private static String generatedEARFacadeName(final String earName) {
        return earName + "Client.jar";
    }

    private void startSystemContentAdapter() {

        final Map<String,StaticContent> systemContent = new HashMap<String,StaticContent>();

        try {
            File gfClientJAR = new File(
                    new File(installRootURI.getRawPath(), "modules"),
                    "gf-client.jar");

            final String classPathExpr = getGFClientModuleClassPath(gfClientJAR);
            final URI gfClientJARURI = gfClientJAR.toURI();

            systemContent.put(systemPath(gfClientJARURI), new FixedContent(new File(gfClientJARURI)));
            for (String classPathElement : classPathExpr.split(" ")) {
                final URI uri = gfClientJARURI.resolve(classPathElement);
                systemContent.put(systemPath(uri), new FixedContent(new File(uri)));
            }

            RestrictedContentAdapter systemAdapter = new RestrictedContentAdapter(systemContent);
            requestDispatcher.registerEndpoint(NamingConventions.JWSAPPCLIENT_SYSTEM_PREFIX,
                    systemAdapter, null);

            if (logger.isLoggable(Level.FINE)) {
                logger.fine("Registered system content adapter serving " + systemAdapter);
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "enterprise.deployment.appclient.jws.errStartSystemAdapter", e);
        }
    }

    private String systemPath(final URI systemFileURI) {
        return NamingConventions.JWSAPPCLIENT_SYSTEM_PREFIX + "/" +
                umbrellaRootURI.relativize(systemFileURI).getPath();
    }


    private String getGFClientModuleClassPath(final File gfClientJAR) throws IOException {
        final JarFile jf = new JarFile(gfClientJAR);
        final Manifest mf = jf.getManifest();
        Attributes mainAttrs = mf.getMainAttributes();
        return mainAttrs.getValue(Attributes.Name.CLASS_PATH);
    }

    public UnprocessedChangeEvents changed(PropertyChangeEvent[] events) {
        /* Record any events we tried to process but could not. */
        List<UnprocessedChangeEvent> unprocessedEvents =
                new ArrayList<UnprocessedChangeEvent>();
//        System.out.println("****** AppClientDeployer.changed invoked");
        for (ConfigListener listener : appClientApps) {
//            System.out.println("******      changed invoking listener " + listener.toString());
            final UnprocessedChangeEvents unprocessedEventsFromOneListener =
                    listener.changed(events);
            if (unprocessedEventsFromOneListener != null) {
                unprocessedEvents.addAll(unprocessedEventsFromOneListener.getUnprocessed());
            }
        }

        return (unprocessedEvents.size() > 0) ? new UnprocessedChangeEvents(unprocessedEvents) : null;
    }
}
