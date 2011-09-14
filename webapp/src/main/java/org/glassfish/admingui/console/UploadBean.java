/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.glassfish.admingui.console;


import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.SessionScoped;
import javax.faces.bean.ViewScoped;
import javax.faces.context.FacesContext;
import javax.faces.event.ValueChangeEvent;
import org.apache.myfaces.trinidad.model.UploadedFile;
import org.glassfish.admingui.console.event.DragDropEvent;
import org.glassfish.admingui.console.rest.RestUtil;
import org.glassfish.admingui.console.util.FileUtil;
import org.glassfish.admingui.console.util.GuiUtil;
import org.glassfish.admingui.console.util.TargetUtil;


/**
 *
 * @author anilam
 */
@ManagedBean
@ViewScoped
public class UploadBean {
    private UploadedFile _file;
    private File tmpFile;
    private String appName;
    private String desc;
    private String contextRoot;
    private List<Map<String, Object>> metaData;

    private String database;
    private String eeTemplate;
    private String loadBalancer;

    private List<String> eeTemplates = new ArrayList<String>() {{
        add("GlassFish Small");
        add("GlassFish Medium");
        add("GlassFish Large");
    }};

    private List<String> databases = new ArrayList<String>() {{
        add("Derby");
        add("MySQL");
        add("Oracle");
    }};

    private List<String> loadBalancers = new ArrayList<String>() {{
        add("foo");
        add("bar");
        add("baz");
    }};

    public void fileUploaded(ValueChangeEvent event) {
        System.out.println("------ in filUploaded");
        UploadedFile file = (UploadedFile) event.getNewValue();
        try{
            if (file != null) {
                //FacesContext context = FacesContext.getCurrentInstance();
                //FacesMessage message = new FacesMessage( "Successfully uploaded file " + file.getFilename() + " (" + file.getLength() + " bytes)");
                //context.addMessage(event.getComponent().getClientId(context), message);
                // Here's where we could call file.getInputStream()
                System.out.println("getFilename=" + file.getFilename());
                System.out.println("getLength=" + file.getLength());
                System.out.println("getContentType=" + file.getContentType());
                File tf = FileUtil.inputStreamToFile(file.getInputStream(), file.getFilename());
                tmpFile = tf;
                Map attrs = new HashMap();
                attrs.put("archive", tmpFile.getAbsolutePath());
                Map appData = (Map) RestUtil.restRequest(REST_URL + "/applications/_get-service-metadata", attrs, "GET", null, null, false, true).get("data");

                //each Map is a Service that will be provisioned
                metaData = (List<Map<String, Object>>)((Map) appData.get("extraProperties")).get("list");
                for(Map oneService : metaData){
                    oneService.put("serviceType", oneService.get("service-type"));   //in the table cell, if there is a 'dash' in the key, it won't show up.
                    String templateId = (String) oneService.get("template-id");
                    if (templateId != null){
                        oneService.put("templateList", getTemplateList ((String) oneService.get("service-type")));
                        oneService.put("templateId", templateId);
                    }
                }

                System.out.println("metaData = " + metaData);
                Map dpAttrs = new HashMap();
                dpAttrs.put("archive" , tmpFile.getAbsolutePath());
                dpAttrs.put("test", "test String");
                dpAttrs.put("modifiedServiceDesc", metaData);
                Map res = (Map) RestUtil.restRequest(REST_URL + "/applications/_generate-glassfish-services-deployment-plan", dpAttrs, "GET", null, null, false, true).get("data");
                System.out.println(res);
            }
        }catch(Exception ex){
            ex.printStackTrace();
        }
    }


    public UploadedFile getFile() {
        return _file;
    }

    public void setFile(UploadedFile file) {
        _file = file;
    }

    public String doDeploy(){
        System.out.println("=================== doDeploy()");
        Map payload = new HashMap();
        payload.put("id", this.tmpFile.getAbsolutePath());
        if (!GuiUtil.isEmpty(this.appName)){
            payload.put("name", this.appName);
        }
        if (!GuiUtil.isEmpty(this.contextRoot)){
            payload.put("contextroot", this.contextRoot);
        }
        /*
        if (!GuiUtil.isEmpty(this.desc)){
            payload.put("name", this.desc);
        }
         *
         */
        Map sessionMap = FacesContext.getCurrentInstance().getExternalContext().getSessionMap();
        List deployingApps = (List)sessionMap.get("_deployingApps");
        try {
            if (deployingApps == null ){
                deployingApps = new ArrayList();
                sessionMap.put("_deployingApps", deployingApps);
            }
            deployingApps.add(this.appName);
            RestUtil.restRequest(REST_URL + "/applications/application", payload, "post", null, null, false, true);
            return "/demo/overview";
        } catch (Exception ex) {
            if (deployingApps != null && deployingApps.contains(this.appName)){
                deployingApps.remove(this.appName);
            }
            ex.printStackTrace();
            System.out.println("------------- do Deploy returns NULL");
            return null;
        }
    }

    public String getAppName(){
        return appName;
    }
    public void setAppName(String nm){
        this.appName = nm;
    }

    public String getDescription(){
        return desc;
    }
    public void setDescription(String description){
        this.desc = description;
    }

    public String getContextRoot(){
        return contextRoot;
    }
    public void setContextRoot(String ctxRoot){
        this.contextRoot = ctxRoot;
    }


    public List getMetaData(){
        return metaData;
    }
    public void setMetaData(List nm){
        this.metaData = nm;
    }

    //For now, since backend only supports one Virtualization setup, we will just return the list if anyone exist.
    //Later, probably need to pass in the virtualiztion type to this method.
    private static List<String> getTemplateList(String type){
        List<String> tList = new ArrayList();
        try{
            List<String> virts = RestUtil.getChildNameList(REST_URL+"/virtualizations");
            for(String virtType : virts){
                List<String> virtInstances = RestUtil.getChildNameList(REST_URL+"/virtualizations/" + virtType);
                if ( (virtInstances != null ) && (virtInstances.size() > 0)){
                    //get the templates for this V that is the same service type
                    String templateEndpoint = REST_URL+"/virtualizations/" + virtType + "/" + virtInstances.get(0) + "/template";
                    if (RestUtil.doesProxyExist(templateEndpoint )){
                        Map<String, String> templateEndpoints = RestUtil.getChildMap(templateEndpoint);
                        for(String oneT : templateEndpoints.keySet()){
                            Map<String, String> tempIndexes = RestUtil.getChildMap(templateEndpoints.get(oneT) + "/template-index");
                            for(String oneI : tempIndexes.keySet()){
                                Map attrs = RestUtil.getAttributesMap(tempIndexes.get(oneI));
                                if ("ServiceType".equals (attrs.get("type"))  &&  type.equals(attrs.get("value"))){
                                    //finally found it
                                    tList.add(oneT);
                                }
                            }
                        }
                    }
                }
            }
        }catch(Exception ex){

        }
        return tList;
    }

    public String databaseDropListener(DragDropEvent event) {
        String value = (String) event.getData();
        if (database != null) {
            databases.add(database);
        }
        database = value;
        databases.remove(database);
        Collections.sort(databases);

        return null;
    }

    public String loadBalancerDropListener(DragDropEvent event) {
        String value = (String) event.getData();
        if (loadBalancer != null) {
            loadBalancers.add(loadBalancer);
        }
        loadBalancer = value;
        loadBalancers.remove(loadBalancer);
        Collections.sort(loadBalancers);

        return null;
    }

    public String eeTemplateDropListener(DragDropEvent event) {
        String value = (String) event.getData();
        if (eeTemplate != null) {
            eeTemplates.add(eeTemplate);
        }
        eeTemplate = value;
        eeTemplates.remove(eeTemplate);
        Collections.sort(eeTemplates);

        return null;
    }

    static final String REST_URL="http://localhost:4848/management/domain";
}
