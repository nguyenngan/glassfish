/**
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
* Copyright 2009 Sun Microsystems, Inc. All rights reserved.
* Generated code from the com.sun.enterprise.config.serverbeans.*
* config beans, based on  HK2 meta model for these beans
* see generator at org.admin.admin.rest.GeneratorResource
* date=Mon May 04 14:01:02 PDT 2009
* Very soon, this generated code will be replace by asm or even better...more dynamic logic.
* Ludovic Champenois ludo@dev.java.net
*
**/
package org.glassfish.admin.rest.resources;
import com.sun.enterprise.config.serverbeans.*;
import javax.ws.rs.*;
import org.glassfish.admin.rest.TemplateResource;
import com.sun.grizzly.config.dom.NetworkConfig;
public class NetworkConfigResource extends TemplateResource<NetworkConfig> {

	@Path("transports/")
	public TransportsResource getTransportsResource() {
		TransportsResource resource = resourceContext.getResource(TransportsResource.class);
		resource.setEntity(getEntity().getTransports() );
		return resource;
	}
	@Path("protocols/")
	public ProtocolsResource getProtocolsResource() {
		ProtocolsResource resource = resourceContext.getResource(ProtocolsResource.class);
		resource.setEntity(getEntity().getProtocols() );
		return resource;
	}
	@Path("network-listeners/")
	public NetworkListenersResource getNetworkListenersResource() {
		NetworkListenersResource resource = resourceContext.getResource(NetworkListenersResource.class);
		resource.setEntity(getEntity().getNetworkListeners() );
		return resource;
	}
}
