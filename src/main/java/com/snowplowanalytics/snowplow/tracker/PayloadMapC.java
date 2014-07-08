/*
 * Copyright (c) 2014 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */

package com.snowplowanalytics.snowplow.tracker;

// Java
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Random;
import java.util.Set;

// Apache Commons
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// JSON
import com.fasterxml.jackson.databind.JsonNode;

/**
 * PayloadMapC implements the PayloadMap interface
 *  The PayloadMap is used to store all the parameters and configurations that are used
 *  to send data via the HTTP GET request.
 *
 * @version 0.2.0
 * @author Kevin Gleason
 */
public class PayloadMapC implements PayloadMap{
    // Logging
    private final Logger logger = LoggerFactory.getLogger(PayloadMapC.class);

    private LinkedHashMap<String,String> parameters;
    private LinkedHashMap<String,Boolean> configurations;

    /**
     * Create an empty PayloadMap with a timestamp.
     */
    public PayloadMapC(){
        this.parameters = new LinkedHashMap<String, String>();
        this.configurations = new LinkedHashMap<String, Boolean>();
    }

    /**
     * Can be constructed with the two payload lists.
     * @param parameters A list of all parameter key-value-pairs
     * @param configurations A list of all configurations.
     */
    public PayloadMapC(LinkedHashMap<String,String> parameters, LinkedHashMap<String,Boolean> configurations){
        this.parameters = parameters;
        this.configurations = configurations;
    }


    /* Transaction Configuration functions
     *   Sets the transaction id once for the life of the event
     *   Timestamp of the event -- Needed with SQL autofill?
     *   Left void/mutable since they take place on instantiation.
     */
    public void setTransactionID(){
        this.parameters.put("tid", makeTransactionID());
    }

    //GUID
    private String makeTransactionID(){
        Random r = new Random(); //NEED ID RANGE
        return String.valueOf(r.nextInt(999999-100000+1) + 100000);
    }

    /**
     * {@inheritDoc}
     * @return
     */
    public void setTimestamp(){
        this.setTimestamp(0);
    }

    /**
     * {@inheritDoc}
     * @return
     */
    public void setTimestamp(float timestamp){
        if(timestamp == 0)
            this.parameters.put("dtm", String.valueOf(System.currentTimeMillis()));
        else
            this.parameters.put("dtm", String.valueOf(timestamp));
    }

    /* Addition functions
     *  Used to add different sources of key=>value pairs to a map.
     *  Map is then used to build "Associative array for getter function.
     *  Some use Base64 encoding
     */
    private String base64Encode(String string) throws UnsupportedEncodingException{
        return Base64.encodeBase64URLSafeString(string.getBytes(Charset.forName("US-ASCII")));
    }

    /**
     * {@inheritDoc}
     * @param key The parameter key
     * @param val The parameter value
     * @return
     */
    public PayloadMap add(String key, String val){
        this.parameters.put(key, val);
        return new PayloadMapC(parameters, configurations);
    }

    /**
     * {@inheritDoc}
     * @param dictInfo Information is parsed elsewhere from string to JSON then passed here
     * @param encode_base64 Whether or not to encode before transferring to web. Default true.
     * @return
     * @throws UnsupportedEncodingException
     */
    public PayloadMap addUnstructured(JsonNode dictInfo, boolean encode_base64)
            throws UnsupportedEncodingException{
        //Encode parameter
        if (dictInfo == null)
            return this;  //Catch this in contractor
        String json = dictInfo.toString();
        if (encode_base64) {
            json = base64Encode(json);
            this.parameters.put("ue_px", json);
        }
        else
            this.parameters.put("ue_pr", json);
        return new PayloadMapC(this.parameters, this.configurations);
    }

    /**
     * {@inheritDoc}
     * @param jsonObject JSON object to be added
     * @param encode_base64 Whether or not to encode before transferring to web. Default true.
     * @return
     * @throws UnsupportedEncodingException
     */
    public PayloadMap addJson(JsonNode jsonObject, boolean encode_base64)
        throws UnsupportedEncodingException{
        //Encode parameter
        if (jsonObject == null)  ///CATCH IF JSON LEFT NULL
            return this;         ///need to figure out purpose of JSON
        String json = null;
        try {
            json = Util.defaultMapper().writeValueAsString(jsonObject);
        } catch (IOException e) {
            logger.error("Parsing error in addJson: {}", e);
        }
        if (encode_base64) {
            json = base64Encode(json);
            this.parameters.put("cx", json);
        }
        else
            this.parameters.put("co", json);
        return new PayloadMapC(this.parameters, this.configurations);
    }

    /**
     * {@inheritDoc}
     * @param p Platform
     * @param tv Tracker Version
     * @param tna Namespace
     * @param aid App_id
     * @return
     */
    public PayloadMap addStandardNvPairs(String p, String tv, String tna, String aid){
        this.parameters.put("p",p);
        this.parameters.put("tv",tv);
        this.parameters.put("tna",tna);
        this.parameters.put("aid",aid);
        return new PayloadMapC(this.parameters, this.configurations);
    }

    /**
     * {@inheritDoc}
     * @param config_title Key of the configuration
     * @param config Value of the configuration
     * @return
     */
    public PayloadMap addConfig(String config_title, boolean config){
        this.configurations.put(config_title, config);
        return new PayloadMapC(this.parameters, this.configurations);
    }

    //// WEB RELATED FUNCTIONS
    /**
     * {@inheritDoc}
     * @param page_url The URL or the page being tracked.
     * @param page_title The Title of the page being tracked.
     * @param referrer The referrer of the page being tracked.
     * @param context Additional JSON context (optional)
     * @param timestamp
     * @return
     * @throws UnsupportedEncodingException
     */
    public PayloadMap trackPageViewConfig(String page_url, String page_title, String referrer,
                                          JsonNode context, long timestamp) throws UnsupportedEncodingException{
        this.parameters.put("e", "pv");
        this.parameters.put("url", page_url);
        this.parameters.put("page", page_title);
        this.parameters.put("refr", referrer);
        this.parameters.put("evn", Constants.DEFAULT_VENDOR);
        this.setTimestamp(timestamp);
        if (context==null)
            return new PayloadMapC(this.parameters, this.configurations);
        PayloadMap tmp = new PayloadMapC(this.parameters, this.configurations);
        return tmp.addJson(context, this.configurations.get("encode_base64"));
    }

    /**
     * {@inheritDoc}
     * @param category Category of the event being tracked.
     * @param action Action of the event being tracked
     * @param label Label of the event being tracked.
     * @param property Property of the event being tracked.
     * @param value Value associated with the property being tracked.
     * @param context Additional JSON context (optional)
     * @param timestamp
     * @return
     * @throws UnsupportedEncodingException
     */
    public PayloadMap trackStructuredEventConfig(String category, String action, String label, String property,
                                                 String value, JsonNode context, long timestamp)
            throws UnsupportedEncodingException{
        this.parameters.put("e","se");
        this.parameters.put("se_ca", category);
        this.parameters.put("se_ac", action);
        this.parameters.put("se_la", label);
        this.parameters.put("se_pr", property);
        this.parameters.put("se_va", value);
        this.parameters.put("evn", Constants.DEFAULT_VENDOR);
        this.setTimestamp(timestamp);
        if (context==null)
            return new PayloadMapC(this.parameters, this.configurations);
        PayloadMap tmp = new PayloadMapC(this.parameters, this.configurations);
        return tmp.addJson(context, this.configurations.get("encode_base64"));
    }

    /**
     * {@inheritDoc}
     * @param eventVendor The vendor the the event information.
     * @param eventName A name for the unstructured event being tracked.
     * @param dictInfo The unstructured information being tracked in dictionary form.
     * @param context Additional JSON context for the tracking call (optional)
     * @param timestamp
     * @return
     * @throws UnsupportedEncodingException
     */
    public PayloadMap trackUnstructuredEventConfig(String eventVendor, String eventName, JsonNode dictInfo,
                                                   JsonNode context, long timestamp) throws UnsupportedEncodingException{
        this.parameters.put("e","ue");
        this.setTimestamp(timestamp);
        PayloadMap tmp = new PayloadMapC(this.parameters, this.configurations);
        tmp = tmp.addUnstructured(dictInfo, this.configurations.get("encode_base64"));
        if (context==null)
            return tmp;
        return tmp.addJson(context, this.configurations.get("encode_base64"));
    }

    /**
     * {@inheritDoc}
     * @param order_id ID of the item.
     * @param sku SKU value of the item.
     * @param price Price of the item.
     * @param quantity Quantity of the item.
     * @param name Name of the item.
     * @param category Category of the item.
     * @param currency Currency used for the purchase.
     * @param context Additional JSON context for the tracking call (optional)
     * @param transaction_id Transaction ID, if left blank new value will be generated.
     * @param timestamp
     * @return
     * @throws UnsupportedEncodingException
     */
    public PayloadMap trackEcommerceTransactionItemConfig(String order_id, String sku,
                                                          String price, String quantity,
                                                          String name, String category,
                                                          String currency, JsonNode context,
                                                          String transaction_id, long timestamp)
            throws UnsupportedEncodingException{
        this.parameters.put("e","ti");
        this.parameters.put("tid", (transaction_id==null) ? String.valueOf(makeTransactionID()) : transaction_id);
        this.parameters.put("ti_id", order_id);
        this.parameters.put("ti_sk", sku);
        this.parameters.put("ti_nm", name);
        this.parameters.put("ti_ca", category);
        this.parameters.put("ti_pr", String.valueOf(price));
        this.parameters.put("ti_qu", String.valueOf(quantity));
        this.parameters.put("ti_cu", currency);
        this.parameters.put("evn", Constants.DEFAULT_VENDOR);
        this.setTimestamp(timestamp);
        if (context==null)
            return new PayloadMapC(this.parameters, this.configurations);
        PayloadMap tmp = new PayloadMapC(this.parameters, this.configurations);
        return tmp.addJson(context, this.configurations.get("encode_base64"));
    }

    /**
     * {@inheritDoc}
     * @param order_id The transaction ID, will be generated if left null
     * @param total_value The total value of the transaction.
     * @param affiliation Affiliations to the transaction (optional)
     * @param tax_value Tax value of the transaction (optional)
     * @param shipping Shipping costs of the transaction (optional)
     * @param city The customers city.
     * @param state The customers state.
     * @param country The customers country.
     * @param currency The currency used for the purchase
     * @param context Additional JSON context for the tracking call (optional)
     * @param timestamp
     * @return
     * @throws UnsupportedEncodingException
     */
    public PayloadMap trackEcommerceTransactionConfig(String order_id, String total_value,
                                                      String affiliation, String tax_value,
                                                      String shipping, String city,
                                                      String state, String country,
                                                      String currency, JsonNode context, long timestamp)
            throws UnsupportedEncodingException{
        this.setTransactionID();
        this.parameters.put("e", "tr");
        this.parameters.put("tr_id", order_id);
        this.parameters.put("tr_tt", total_value);
        this.parameters.put("tr_af", affiliation);
        this.parameters.put("tr_tx", tax_value);
        this.parameters.put("tr_sh", shipping);
        this.parameters.put("tr_ci", city);
        this.parameters.put("tr_st", state);
        this.parameters.put("tr_co", country);
        this.parameters.put("tr_cu", currency);
        this.parameters.put("evn", Constants.DEFAULT_VENDOR);
        this.setTimestamp(timestamp);
        if (context==null)
            return new PayloadMapC(this.parameters, this.configurations);
        PayloadMap tmp = new PayloadMapC(this.parameters, this.configurations);
        return tmp.addJson(context, this.configurations.get("encode_base64"));
    }

    /* Getter functions.
     *  Can be used to get key sets of parameters and configurations
     *  Also used to get the linked hash maps of the parameters and configurations
    */
    public Set<String> getParamKeySet(){ return this.parameters.keySet(); }

    public Set<String> getConfigKeySet(){ return this.configurations.keySet(); }

    public String getParam(String key){ return this.parameters.get(key); }

    public boolean getConfig(String key){ return this.configurations.get(key); }

    public LinkedHashMap<String,String> getParams() { return this.parameters; }

    public LinkedHashMap<String,Boolean> getConfigs() { return this.configurations; }

    public String toString(){
        return "Parameters: " +this.parameters.toString() +
                "\nConfigurations: " + this.configurations.toString();
    }

}
