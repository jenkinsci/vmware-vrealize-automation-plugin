package com.inkysea.vmware.vra.jenkins.plugin.model;

import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import net.sf.json.JSONObject;
import com.fasterxml.jackson.databind.JsonNode;


/**
 * Created by kthieler on 2/24/16.
 */
public class Deployment {

    private PluginParam params;
    private DestroyParam dParams;

    private Request request;
    private PrintStream logger;
    private String DESTROY_TEMPLATE_URL;
    private String DESTROY_URL;
    private String deploymentName;
    private String parentResourceID;
    private JsonObject deploymentResources;
    private String businessGroupId;
    private String tenantId;
    public JsonObject bluePrintTemplate;

    private String jsonString = "{\"@type\":\"ResourceActionRequest\", \"resourceRef\":{\"id\":\"\"}, \"resourceActionRef\"\n" +
            ":{\"id\":\"\"}, \"organization\":{\"tenantRef\":\"\", \"tenantLabel\"\n" +
            ":\"\", \"subtenantRef\":\"\", \"subtenantLabel\":\"\"\n" +
            "}, \"state\":\"SUBMITTED\", \"requestNumber\":0, \"requestData\":{\"entries\":[]}}";


    private List<List<String>> machineList = new ArrayList<List<String>>();
    private ArrayList<String> machineDataList = new ArrayList<String>();

    private List<List<String>> loadBalancerList = new ArrayList<List<String>>();
    private ArrayList<String> loadBalancerDataList = new ArrayList<String>();


    public Deployment(PrintStream logger, PluginParam params) throws IOException {

        this.params = params;
        this.logger = logger;

        this.request  = new Request(logger, params);

        this.bluePrintTemplate = this.request.GetBluePrintTemplate();



    }

    public Deployment(PrintStream logger, DestroyParam params) throws IOException {

        this.dParams = params;
        this.logger = logger;

        this.request  = new Request(logger, params);


    }

    public boolean Create() throws IOException, InterruptedException {

        boolean rcode = false;

        // merge deployment options into request blueprint

        JsonParser parser = new JsonParser();

        for ( RequestParam option : params.getRequestParams()){


            if (option.getJson().isEmpty() ){

                logger.println("Request Parameter is null. skipping to next parameter");


            }else {

                logger.println("Request Parameter : " + option.getJson());


                this.bluePrintTemplate = merge(this.bluePrintTemplate.getAsJsonObject(),
                        parser.parse(option.getJson()).getAsJsonObject());
            }
        }

        logger.println("Requesting Blueprint with JSON template : " + this.bluePrintTemplate);
        request.ProvisionBluePrint(this.bluePrintTemplate);


        if (this.params.isWaitExec()) {
            while (!request.IsRequestComplete()) {
                System.out.println("Execution status : " + request.RequestStatus().toString());
                Thread.sleep(10 * 1000);
            }

            switch (request.RequestStatus()) {
                case SUCCESSFUL:
                    System.out.println("Request completed successfully");
                    DeploymentResources();
                    rcode = true;
                    break;
                case FAILED:
                    rcode = false;
                    throw new IOException("Request execution failed. Please go to vRA for more details");
                case REJECTED:
                    rcode = false;
                    throw new IOException("Request execution cancelled. Please go to vRA for more details");
            }
        }

        return rcode;

    }


    private void getMachineList() {

        JsonArray contentArray = this.deploymentResources.getAsJsonArray("content");

        for (JsonElement content : contentArray) {

            if (content.getAsJsonObject().get("resourceType").getAsString().contains("Infrastructure.Virtual")) {

                JsonObject jsonData = content.getAsJsonObject().getAsJsonObject("data");
                JsonArray  networkArray = jsonData.getAsJsonArray("NETWORK_LIST");


                for (JsonElement e : networkArray) {
                    JsonElement jsonNetworkData = e.getAsJsonObject().get("data");

                    machineDataList.add(content.getAsJsonObject().get("resourceType").getAsString());
                    machineDataList.add(jsonData.getAsJsonObject().get("Component").getAsString());
                    machineDataList.add(content.getAsJsonObject().get("name").getAsString());
                    machineDataList.add(jsonNetworkData.getAsJsonObject().get("NETWORK_NAME").getAsString());
                    machineDataList.add(jsonNetworkData.getAsJsonObject().get("NETWORK_ADDRESS").getAsString());

                    machineList.add(machineDataList);

                }

            }
        }

    }

    private void getLoadBalancerList() {

        JsonArray contentArray = this.deploymentResources.getAsJsonArray("content");

        for (JsonElement content : contentArray) {

            if (content.getAsJsonObject().get("resourceType").getAsString().contains("Infrastructure.Network.LoadBalancer")) {

                JsonObject jsonData = content.getAsJsonObject().getAsJsonObject("data");

                loadBalancerDataList.add(content.getAsJsonObject().get("resourceType").getAsString());
                loadBalancerDataList.add(jsonData.getAsJsonObject().get("Name").getAsString());
                loadBalancerDataList.add(jsonData.getAsJsonObject().get("LoadBalancerInfo").getAsString());

                loadBalancerList.add(loadBalancerDataList);

                }

            }

    }

    public Map <String, String> getMachineHashMap() throws IOException {

        Map machineMap = null;

        getMachineList();


        for( List machine : this.machineList ){
            //creat map named grup__machine_name__network_name :  network address
            for ( Object data : machine ){
                System.out.println(data.toString());
            }

        }

        return machineMap;
    }

    public Map<String, String> getDeploymentComponents(String count) {
        // Prefix outputs with stack name to prevent collisions with other stacks created in the same build.
        HashMap<String, String> map = new HashMap<String, String>();

        String deploymentName = getDeploymentName();

        map.put("VRADEP_"+count+"_NAME", deploymentName);
        map.put("VRADEP_"+count+"_TENANT", params.getTenant());


        getMachineList();

        for( List machine : this.machineList ){
            //creat map named grup__machine_name__network_name :  network address
            for ( Object data : machine ){
                //tenant_deployment_group_machine_network = IP
                map.put(params.getTenant().toUpperCase() + "_" + deploymentName.toUpperCase()+"_"+
                                machine.get(1).toString().toUpperCase()+"_"+
                                machine.get(2).toString().toUpperCase()+"_"+
                                machine.get(3).toString().toUpperCase(),
                                machine.get(4).toString().toUpperCase());
            }

        }

        getLoadBalancerList();

        for( List loadbalancer : this.loadBalancerList ){
            //creat map named grup__machine_name__network_name :  network address
            for ( Object data : loadbalancer ){
                //tenant_deployment_group_machine_network = IP
                map.put(params.getTenant().toUpperCase() + "_" + deploymentName.toUpperCase()+
                                loadbalancer.get(1).toString().toUpperCase(),
                                loadbalancer.get(2).toString());
            }

        }



        return map;
    }

    public String getDeploymentName(){

        JsonArray contentArray = this.deploymentResources.getAsJsonArray("content");
        for (JsonElement content : contentArray) {

            if (content.getAsJsonObject().get("resourceType").getAsString().equals("composition.resource.type.deployment")) {

                this.deploymentName = content.getAsJsonObject().get("name").getAsString();
                System.out.println("Name :" + this.deploymentName );

            }
        }
        String depName = this.deploymentName;
        return depName;
    }

    public void DeploymentResources() throws IOException{

            this.deploymentResources  = request.GetRequestResourceView();

    }


    public void getParentResourceID() throws IOException{

        JsonArray contentArray = this.deploymentResources.getAsJsonArray("content");
        for (JsonElement content : contentArray) {

            if (content.getAsJsonObject().get("resourceType").getAsString().equals("composition.resource.type.deployment")) {

                this.parentResourceID = content.getAsJsonObject().get("resourceId").getAsString();
            }
        }
    }



    public void getParentResourceID(String name) throws IOException{

        JsonArray contentArray = this.deploymentResources.getAsJsonArray("content");

        for (JsonElement content : contentArray) {
            System.out.println("Content :" + content.getAsJsonObject().get("name").getAsString() );

            if (content.getAsJsonObject().get("name").getAsString().equals(name)) {
                this.parentResourceID = content.getAsJsonObject().get("resourceId").getAsString();
                System.out.println("ParentID :" + this.parentResourceID );
                break;
            }
        }
    }

    public String getDestroyURL() throws IOException {

        String URL = "";

        return URL;
    }

    public String getDestroyAction() throws IOException {

        this.getParentResourceID();

        JsonObject actions = this.request.getResourceActions(this.parentResourceID);

        JsonArray contentArray = actions.getAsJsonArray("content");

        String actionID = "";

        for (JsonElement content : contentArray) {
                System.out.println(content.getAsJsonObject().get("name").getAsString());
            if (content.getAsJsonObject().get("name").getAsString().equals("Destroy")) {
                 actionID = content.getAsJsonObject().get("id").getAsString();
                 System.out.println(actionID);
                 break;
            }
        }
        return actionID;

    }

    private void getTenant(){

        JsonArray contentArray = this.deploymentResources.getAsJsonArray("content");

        for (JsonElement content : contentArray) {
            if (content.getAsJsonObject().get("resourceId").getAsString().equals(this.parentResourceID)) {
                this.tenantId = content.getAsJsonObject().get("tenantId").getAsString();
                System.out.println("tenantID :" + this.tenantId );
                break;
            }
        }
    }

    private void getBusinessGroup(){
        JsonArray contentArray = this.deploymentResources.getAsJsonArray("content");

        for (JsonElement content : contentArray) {
            if (content.getAsJsonObject().get("resourceId").getAsString().equals(this.parentResourceID)) {
                this.businessGroupId = content.getAsJsonObject().get("businessGroupId").getAsString();
                System.out.println("tenantID :" + this.businessGroupId );
                break;
            }
        }
    }

    public static JsonNode merge(JsonNode mainNode, JsonNode updateNode) {

        Iterator<String> fieldNames = updateNode.fieldNames();
        while (fieldNames.hasNext()) {

            String fieldName = fieldNames.next();
            JsonNode jsonNode = mainNode.get(fieldName);
            // if field exists and is an embedded object
            if (jsonNode != null && jsonNode.isObject()) {
                merge(jsonNode, updateNode.get(fieldName));
            }
            else {
                if (mainNode instanceof ObjectNode) {
                    // Overwrite field
                    JsonNode value = updateNode.get(fieldName);
                    ((ObjectNode) mainNode).put(fieldName, value);
                }
            }

        }

        return mainNode;
    }

    public static JsonObject merge(JsonObject mainJson, JsonObject updateJson) throws IOException {

        JsonParser parser = new JsonParser();
        JsonObject returnJSON;

        ObjectMapper mapper = new ObjectMapper();

        String json1 = mainJson.toString();
        String json2 = updateJson.toString();

        System.out.println("Original BP request : "+json1);
        System.out.println("JSON to merge : "+json2);


        JsonNode mainNode = mapper.readTree(json1);
        returnJSON = parser.parse(mainNode.toString()).getAsJsonObject();
        JsonNode updateNode = mapper.readTree(json2);

        returnJSON = parser.parse(merge(mainNode,updateNode).toString()).getAsJsonObject();

        /*Iterator<String> fieldNames = updateNode.fieldNames();

        while (fieldNames.hasNext()) {
            String updatedFieldName = fieldNames.next();
            System.out.println("FieldName Next : "+updatedFieldName );

            JsonNode valueToBeUpdated = mainNode.get(updatedFieldName);
            System.out.println("valueToBeUpdated  : "+valueToBeUpdated.toString() );

            JsonNode updatedValue = updateNode.get(updatedFieldName);
            System.out.println("updatedValue  : "+updatedValue.toString() );


            // If the node is an @ArrayNode
            if (valueToBeUpdated != null && updatedValue.isArray()) {
                // running a loop for all elements of the updated ArrayNode
                for (int i = 0; i < updatedValue.size(); i++) {
                    JsonNode updatedChildNode = updatedValue.get(i);
                    // Create a new Node in the node that should be updated, if there was no corresponding node in it
                    // Use-case - where the updateNode will have a new element in its Array
                    if (valueToBeUpdated.size() <= i) {
                        ((ArrayNode) valueToBeUpdated).add(updatedChildNode);
                    }
                    // getting reference for the node to be updated
                    JsonNode childNodeToBeUpdated = valueToBeUpdated.get(i);
                    updatedValue = mapper.readTree( merge(parser.parse(childNodeToBeUpdated.toString()).getAsJsonObject(),
                            parser.parse(updatedChildNode.toString()).getAsJsonObject()).toString());
                }
                // if the Node is an @ObjectNode
            } else if (valueToBeUpdated != null && valueToBeUpdated.isObject()) {
                System.out.println("In ObjectNode "+updatedFieldName);
                //returnJSON =

                //mainNode = mapper.readTree(merge(parser.parse(valueToBeUpdated.toString()).getAsJsonObject(),
                //         parser.parse(updatedValue.toString()).getAsJsonObject()).toString());
                JsonObject test = merge(parser.parse(valueToBeUpdated.toString()).getAsJsonObject(),
                                parser.parse(updatedValue.toString()).getAsJsonObject());
                updatedValue = mapper.readTree(test.toString());
                mainNode = updatedValue;

                System.out.println("Leaving ObjectNode "+updatedFieldName+" with "+updatedValue);


            } else {
                if (mainNode instanceof ObjectNode) {
                    System.out.println("Updating "+updatedFieldName+" from "+valueToBeUpdated+" to "+updatedValue);
                    ((ObjectNode) mainNode).replace(updatedFieldName, updatedValue);
                    System.out.println("JSON after replace : "+mainNode);

                }else{
                    System.out.println("Error ");
                    return returnJSON;
                }
            }
            System.out.println("Done with "+updatedFieldName+" JSON "+mainNode);

        }

        returnJSON = parser.parse(mainNode.toString()).getAsJsonObject();
        System.out.println("Returning with :"+returnJSON);
        */
        return returnJSON;

    }


    public boolean Destroy( String DeploymentName ) throws IOException {

        System.out.println("Destroying Deployment "+DeploymentName);

        // Get ResrouceView to find parentID from name
        this.deploymentResources = this.request.GetResourceView();
        System.out.println("JSON Obj "+this.deploymentResources);

        this.getParentResourceID(DeploymentName);
        // Get actionID for destroy
        return this.Destroy();

    }

    public boolean Destroy() throws IOException {

        if( this.parentResourceID == null ) {
            System.out.println("Destroying Deployment");

            DeploymentResources();
            this.getParentResourceID();
        }

        String actionID = this.getDestroyAction();
        getBusinessGroup();
        getTenant();
/*
        JsonObject json = request.getResourceActionsRequestTemplate(parentResourceID, actionID);

        json.addProperty("description", "test");
        JsonObject jsonData = json.getAsJsonObject("data");
        jsonData.addProperty("description", "test");
        jsonData.addProperty("reasons", "test");

        System.out.println(json);
        request.ResourceActionsRequest(parentResourceID, actionID, json);
*/

        System.out.println("JSON Destroy "+ jsonString);


        JsonElement jsonDestroyElement = new JsonParser().parse(jsonString);
        JsonObject jsonDestroyObject = jsonDestroyElement.getAsJsonObject();

        JsonObject jsonResourceReb = jsonDestroyObject.getAsJsonObject("resourceRef");
        jsonResourceReb.addProperty("id", this.parentResourceID);

        JsonObject jsonResourceAction = jsonDestroyObject.getAsJsonObject("resourceActionRef");
        jsonResourceAction.addProperty("id", actionID);

        JsonObject jsonOrganizationAction = jsonDestroyObject.getAsJsonObject("organization");
        jsonOrganizationAction.addProperty("tenantRef", this.tenantId);
        jsonOrganizationAction.addProperty("tenantLabel", this.tenantId);
        jsonOrganizationAction.addProperty("subtenantRef", this.businessGroupId);





        System.out.println("JSON Destroy "+jsonDestroyObject.toString());

        request.PostRequest(jsonDestroyObject.toString());

        return true;

    }

}