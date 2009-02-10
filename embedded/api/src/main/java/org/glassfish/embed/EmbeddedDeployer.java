
package org.glassfish.embed;

import com.sun.enterprise.deploy.shared.ArchiveFactory;
import com.sun.enterprise.module.impl.ClassLoaderProxy;
import com.sun.enterprise.util.io.FileUtils;
import com.sun.enterprise.v3.server.ApplicationLifecycle;
import com.sun.enterprise.v3.server.SnifferManager;
import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.logging.*;
import org.glassfish.api.admin.ParameterNames;
import org.glassfish.api.container.Sniffer;
import org.glassfish.api.deployment.archive.ArchiveHandler;
import org.glassfish.api.deployment.archive.ReadableArchive;
import org.glassfish.deployment.common.DeploymentContextImpl;
import org.glassfish.embed.impl.SilentActionReport;
import org.glassfish.internal.data.ApplicationInfo;
import org.glassfish.server.ServerEnvironmentImpl;
import org.jvnet.hk2.component.Habitat;

/**
 *
 * @author bnevins
 */
public class EmbeddedDeployer {
    EmbeddedDeployer(Server server) throws EmbeddedException {
        this.server = server;
        mustBeStarted("EmbeddedDeployer Constructor");
        Habitat habitat = server.getHabitat();
        archiveFactory = habitat.getComponent(ArchiveFactory.class);
        appLife = server.getAppLife();
        efs = server.getInfo().getFileSystem();
        snifferManager = habitat.getComponent(SnifferManager.class);
        serverEnvironment = habitat.getComponent(ServerEnvironmentImpl.class);
    }


    /**
     * Deploys WAR/EAR/RAR/etc to server.
     *
     * @param archive
     * @throws EmbeddedException
     */

    public void deploy(File archive) throws EmbeddedException {
        try {
            mustBeStarted("deploy(File)");
            ReadableArchive a = archiveFactory.openArchive(archive);

            // TODO  WTF code.  For now just port from Server to here
            // WTF WTF WTF WTF
            // WTF WTF WTF WTF
            // WTF WTF WTF WTF
            if (!archive.isDirectory()) {
                ArchiveHandler h = appLife.getArchiveHandler(a);
                File appDir = new File(efs.getApplicationsDir(), a.getName());
                FileUtils.whack(appDir);
                appDir.mkdirs();
                h.expand(a, archiveFactory.createArchive(appDir));
                a.close();
                a = archiveFactory.openArchive(appDir);
            }
            deploy(a, new Properties());
        }
        catch (EmbeddedException ex) {
            throw ex;
        }
        catch (Exception ex) {
            throw new EmbeddedException(ex);
        }
    }

    /**
     * Deploys a {@link ReadableArchive} to this Server.
     * <p/>
     * <p/>
     * This overloaded version of the deploy method is for advanced users.
     * It allows you specifying additional parameters to be passed to the deploy command
     *
     * @param a
     * @param params
     * @return
     * @throws EmbeddedException
     */
    public void deploy(ReadableArchive a, Properties params) throws EmbeddedException {
        try {
            mustBeStarted("deploy(ReadableArchive, Properties)");
            ArchiveHandler h = appLife.getArchiveHandler(a);

            // now prepare sniffers
            //is this required?
            ClassLoader parentCL = createSnifferParentCL(null, snifferManager.getSniffers());

            ClassLoader cl = h.getClassLoader(parentCL, a);
            Collection<Sniffer> activeSniffers = snifferManager.getSniffers(a, cl);

            // TODO: we need to stop this totally type-unsafe way of passing parameters
            if (params == null) {
                params = new Properties();
            }
            params.put(ParameterNames.NAME, a.getName());
            params.put(ParameterNames.ENABLED, "true");
            final DeploymentContextImpl deploymentContext = new DeploymentContextImpl(Logger.getAnonymousLogger(), a, params, serverEnvironment);

            SilentActionReport r = new SilentActionReport();
            ApplicationInfo appInfo = appLife.deploy(activeSniffers, deploymentContext, r);
            r.check();

            //return new Application(this, appInfo, deploymentContext);
        }
        catch (EmbeddedException ex) {
            throw ex;
        }
        catch (Exception ex) {
            throw new EmbeddedException(ex);
        }
    }
    /**
     * Convenience method to deploy a scattered war archive on a given virtual server
     * and using the specified context root.
     *
     * @param war           the scattered war
     * @param contextRoot   the context root to use
     * @param virtualServer the virtual server ID
     * @return
     * @throws EmbeddedException
     */
    public void deployScattered(ScatteredWar war, String contextRoot, String virtualServer) throws EmbeddedException {
        Properties params = new Properties();
        if (virtualServer == null) {
            virtualServer = "server";
        }
        params.put(ParameterNames.VIRTUAL_SERVERS, virtualServer);
        if (contextRoot != null) {
            params.put(ParameterNames.CONTEXT_ROOT, contextRoot);
        }
        deploy(war, params);
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////           END public API       ////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Sets up a parent classloader that will be used to create a temporary application
     * class loader to load classes from the archive before the Deployers are available.
     * Sniffer.handles() method takes a class loader as a parameter and this class loader
     * needs to be able to load any class the sniffer load themselves.
     *
     * @param parent   parent class loader for this class loader
     * @param sniffers sniffer instances
     * @return a class loader with visibility on all classes loadable by classloaders.
     */
    private ClassLoader createSnifferParentCL(ClassLoader parent, Collection<Sniffer> sniffers) {
        // Use the sniffers class loaders as the delegates to the parent class loader.
        // This will allow any class loadable by the sniffer (therefore visible to the sniffer
        // class loader) to be also loadable by the archive's class loader.
        ClassLoaderProxy cl = new ClassLoaderProxy(new URL[0], parent);
        for (Sniffer sniffer : sniffers) {
            cl.addDelegate(sniffer.getClass().getClassLoader());
        }
        return cl;
    }

    private void mustBeStarted(String method) throws EmbeddedException {
        server.mustBeStarted(CLASS_NAME + method);
        
    }

    private Server                  server;
    private ArchiveFactory          archiveFactory;
    private ApplicationLifecycle    appLife;
    private EmbeddedFileSystem      efs;
    private SnifferManager          snifferManager;
    private ServerEnvironmentImpl   serverEnvironment;
    private static final String     CLASS_NAME = "EmbeddedDeployer.";
}
