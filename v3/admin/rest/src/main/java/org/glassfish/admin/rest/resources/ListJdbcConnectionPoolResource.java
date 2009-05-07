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
import java.util.List;
import org.glassfish.admin.rest.TemplateListOfResource;
import com.sun.enterprise.config.serverbeans.JdbcConnectionPool;
public class ListJdbcConnectionPoolResource extends TemplateListOfResource<JdbcConnectionPool> {


	@Path("{Name}/")
	public JdbcConnectionPoolResource getJdbcConnectionPoolResource(@PathParam("Name") String id) {
		JdbcConnectionPoolResource resource = resourceContext.getResource(JdbcConnectionPoolResource.class);
		for (JdbcConnectionPool c: entity){
			if(c.getName().equals(id)){
				resource.setEntity(c);
			}
		}
		return resource;
	}

}
