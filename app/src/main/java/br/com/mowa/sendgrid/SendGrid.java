package br.com.mowa.sendgrid;


import android.net.Uri;
import android.os.AsyncTask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EventListener;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by ygorpessoa on 10/05/16.
 */
public class SendGrid {
    private static final String VERSION = "2.2.2";
    private static final String USER_AGENT = "sendgrid/" + VERSION + ";java";

    private static final String PARAM_TO = "to[]";
    private static final String PARAM_TONAME = "toname[]";
    private static final String PARAM_CC = "cc[]";
    private static final String PARAM_BCC = "bcc[]";

    private static final String PARAM_FROM = "from";
    private static final String PARAM_FROMNAME = "fromname";
    private static final String PARAM_REPLYTO = "replyto";
    private static final String PARAM_SUBJECT = "subject";
    private static final String PARAM_HTML = "html";
    private static final String PARAM_TEXT = "text";
    private static final String PARAM_FILES = "files[%s]";
    private static final String PARAM_CONTENTS = "content[%s]";
    private static final String PARAM_XSMTPAPI = "x-smtpapi";
    private static final String PARAM_HEADERS = "headers";

    private String username;
    private String password;
    private String url;
    private String endpoint;

    /**
     * Constructor for using a username and password
     *
     * @param username SendGrid username
     * @param password SendGrid password
     */
    public SendGrid(String username, String password) {
        this.username = username;
        this.password = password;
        this.url = "https://api.sendgrid.com";
        this.endpoint = "/api/mail.send.json";
    }

    /**
     * Constructor for using an API key
     *
     * @param apiKey SendGrid api key
     */
    public SendGrid(String apiKey) {
        this.password = apiKey;
        this.username = null;
        this.url = "https://api.sendgrid.com";
        this.endpoint = "/api/mail.send.json";
    }

    public SendGrid setUrl(String url) {
        this.url = url;
        return this;
    }

    public SendGrid setEndpoint(String endpoint) {
        this.endpoint = endpoint;
        return this;
    }

    public String getVersion() {
        return VERSION;
    }


    public static String buildPostParameters(Object content) {
        String output = null;
        if ((content instanceof String) ||
                (content instanceof JSONObject) ||
                (content instanceof JSONArray)) {
            output = content.toString();
        } else if (content instanceof Map) {
            Uri.Builder builder = new Uri.Builder();
            HashMap hashMap = (HashMap) content;
            if (hashMap != null) {
                Iterator entries = hashMap.entrySet().iterator();
                while (entries.hasNext()) {
                    Map.Entry entry = (Map.Entry) entries.next();
                    builder.appendQueryParameter(entry.getKey().toString(), entry.getValue().toString());
                    entries.remove(); // avoids a ConcurrentModificationException
                }
                output = builder.build().getEncodedQuery();
            }
        }
        return output;
    }



    public String buildBody(Email email) {
        Map<String,Object> params = new HashMap<>();

        // We are using an API key
        if (this.username != null) {
            params.put("api_user", this.username);
            params.put("api_key", this.password);
        }

        String[] tos = email.getTos();
        String[] tonames = email.getToNames();
        String[] ccs = email.getCcs();
        String[] bccs = email.getBccs();

        // If SMTPAPI Header is used, To is still required. #workaround.
        if (tos.length == 0) {
            params.put(String.format(PARAM_TO, 0), email.getFrom());
        }
        for (String to : tos)
            params.put(PARAM_TO, to);
        for (String toname : tonames)
            params.put(PARAM_TONAME, toname);
        for (String cc : ccs)
            params.put(PARAM_CC, cc);
        for (String bcc : bccs)
            params.put(PARAM_BCC, bcc);
        // Files
        if (email.getAttachments().size() > 0) {
            Iterator it = email.getAttachments().entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry entry = (Map.Entry) it.next();
                params.put(String.format(PARAM_FILES, entry.getKey()), entry.getValue());
            }
        }

        if (email.getContentIds().size() > 0) {
            Iterator it = email.getContentIds().entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry entry = (Map.Entry) it.next();
                params.put(String.format(PARAM_CONTENTS, entry.getKey()), entry.getValue());
            }
        }

        if (email.getHeaders().size() > 0)
            params.put(PARAM_HEADERS, new JSONObject(email.getHeaders()).toString());

        if (email.getFrom() != null && !email.getFrom().isEmpty())
            params.put(PARAM_FROM, email.getFrom());

        if (email.getFromName() != null && !email.getFromName().isEmpty())
            params.put(PARAM_FROMNAME, email.getFromName());

        if (email.getReplyTo() != null && !email.getReplyTo().isEmpty())
            params.put(PARAM_REPLYTO, email.getReplyTo());

        if (email.getSubject() != null && !email.getSubject().isEmpty())
            params.put(PARAM_SUBJECT, email.getSubject());

        if (email.getHtml() != null && !email.getHtml().isEmpty())
            params.put(PARAM_HTML, email.getHtml());

        if (email.getText() != null && !email.getText().isEmpty())
            params.put(PARAM_TEXT, email.getText());

        String tmpString = email.smtpapi.jsonString();
        if (!tmpString.equals("{}"))
            params.put(PARAM_XSMTPAPI, tmpString);

        return buildPostParameters(params);
    }

    public void send(Email email, ResponseSendGrid.Listener listener, ResponseSendGrid.ErrorListener errorListener){

        String body = this.buildBody(email);
        String url = (this.url + this.endpoint);

        // Using an API key
        HashMap<String, String> customHeader = new HashMap<>();
        if (this.username == null) {
            customHeader.put("Authorization", "Bearer " + this.password);
            customHeader.put("Content-Type", "application/x-www-form-urlencoded");
        }

        new SendTask(url, customHeader, body, listener, errorListener).execute();

    }

    private class SendTask extends AsyncTask<String, Integer, JSONObject>  {

        private String url;
        Map<String, String> headers;
        String requestBody;
        ResponseSendGrid.Listener listener;
        ResponseSendGrid.ErrorListener errorListener;

        public SendTask(String url, Map<String, String> headers, String requestBody, ResponseSendGrid.Listener listener, ResponseSendGrid.ErrorListener errorListener) {
            this.url = url;
            this.headers = headers;
            this.requestBody = requestBody;
            this.listener = listener;
            this.errorListener = errorListener;
        }

        // Decode image in background.
        @Override
        protected JSONObject doInBackground(String... params) {
            return makeRequest("POST", url, headers, requestBody, errorListener);
        }


        // Once complete, see if ImageView is still around and set bitmap.
        @Override
        protected void onPostExecute(JSONObject response) {
            if (response != null) {
                listener.onResponse(response);
            }
        }
    }


    public static JSONObject makeRequest(String method, String apiAddress, Map<String, String> headers, String requestBody, ResponseSendGrid.ErrorListener errorListener) {

        try {
            URL url  = new URL(apiAddress);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

            urlConnection.setDoInput(true);
            urlConnection.setDoOutput(!method.equals("GET"));
            urlConnection.setRequestMethod(method);

            if (headers != null){
                for(String key: headers.keySet()){
                    urlConnection.setRequestProperty(key, headers.get(key));
                }
            }
            OutputStream outputStream = new BufferedOutputStream(urlConnection.getOutputStream());
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, "utf-8"));
            writer.write(requestBody);
            writer.flush();
            writer.close();
            outputStream.close();

            urlConnection.connect();

            InputStream inputStream;
            // get stream
            if (urlConnection.getResponseCode() < HttpURLConnection.HTTP_BAD_REQUEST) {
                inputStream = urlConnection.getInputStream();
            } else {
                inputStream = urlConnection.getErrorStream();
            }
            // parse stream
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            StringBuilder response = new StringBuilder();
            while ((line = bufferedReader.readLine()) != null) {
                response.append(line);
            }

            return new JSONObject(response.toString());

        } catch (IOException e) {
            e.printStackTrace();
            errorListener.onErrorResponse(new SendGridException(e));
        } catch (JSONException e) {
            errorListener.onErrorResponse(new SendGridException(e));
        }

        return null;

    }


    public static class SendGridException extends Exception {
        public SendGridException(Exception e) {
            super(e);
        }
    }

    public static class Email {
        private SMTPAPI smtpapi;
        private ArrayList<String> to;
        private ArrayList<String> toname;
        private ArrayList<String> cc;
        private String from;
        private String fromname;
        private String replyto;
        private String subject;
        private String text;
        private String html;
        private ArrayList<String> bcc;
        private Map<String, InputStream> attachments;
        private Map<String, String> contents;
        private Map<String, String> headers;

        public Email() {
            this.smtpapi = new SMTPAPI();
            this.to = new ArrayList<String>();
            this.toname = new ArrayList<String>();
            this.cc = new ArrayList<String>();
            this.bcc = new ArrayList<String>();
            this.attachments = new HashMap<String, InputStream>();
            this.contents = new HashMap<String, String>();
            this.headers = new HashMap<String, String>();
        }

        public Email addTo(String to) {
            this.to.add(to);
            return this;
        }

        public Email addTo(String[] tos) {
            this.to.addAll(Arrays.asList(tos));
            return this;
        }

        public Email addTo(String to, String name) {
            this.addTo(to);
            return this.addToName(name);
        }

        public Email setTo(String[] tos) {
            this.to = new ArrayList<String>(Arrays.asList(tos));
            return this;
        }

        public String[] getTos() {
            return this.to.toArray(new String[this.to.size()]);
        }

        public Email addSmtpApiTo(String to) throws JSONException {
            this.smtpapi.addTo(to);
            return this;
        }

        public Email addSmtpApiTo(String[] to) throws JSONException {
            this.smtpapi.addTos(to);
            return this;
        }

        public Email addToName(String toname) {
            this.toname.add(toname);
            return this;
        }

        public Email addToName(String[] tonames) {
            this.toname.addAll(Arrays.asList(tonames));
            return this;
        }

        public Email setToName(String[] tonames) {
            this.toname = new ArrayList<String>(Arrays.asList(tonames));
            return this;
        }

        public String[] getToNames() {
            return this.toname.toArray(new String[this.toname.size()]);
        }

        public Email addCc(String cc) {
            this.cc.add(cc);
            return this;
        }

        public Email addCc(String[] ccs) {
            this.cc.addAll(Arrays.asList(ccs));
            return this;
        }

        public Email setCc(String[] ccs) {
            this.cc = new ArrayList<String>(Arrays.asList(ccs));
            return this;
        }

        public String[] getCcs() {
            return this.cc.toArray(new String[this.cc.size()]);
        }

        public Email setFrom(String from) {
            this.from = from;
            return this;
        }

        public String getFrom() {
            return this.from;
        }

        public Email setFromName(String fromname) {
            this.fromname = fromname;
            return this;
        }

        public String getFromName() {
            return this.fromname;
        }

        public Email setReplyTo(String replyto) {
            this.replyto = replyto;
            return this;
        }

        public String getReplyTo() {
            return this.replyto;
        }

        public Email addBcc(String bcc) {
            this.bcc.add(bcc);
            return this;
        }

        public Email addBcc(String[] bccs) {
            this.bcc.addAll(Arrays.asList(bccs));
            return this;
        }

        public Email setBcc(String[] bccs) {
            this.bcc = new ArrayList<String>(Arrays.asList(bccs));
            return this;
        }

        public String[] getBccs() {
            return this.bcc.toArray(new String[this.bcc.size()]);
        }

        public Email setSubject(String subject) {
            this.subject = subject;
            return this;
        }

        public String getSubject() {
            return this.subject;
        }

        public Email setText(String text) {
            this.text = text;
            return this;
        }

        public String getText() {
            return this.text;
        }

        public Email setHtml(String html) {
            this.html = html;
            return this;
        }

        public String getHtml() {
            return this.html;
        }

        public Email addSubstitution(String key, String[] val) throws JSONException {
            this.smtpapi.addSubstitutions(key, val);
            return this;
        }

        public JSONObject getSubstitutions() throws JSONException {
            return this.smtpapi.getSubstitutions();
        }

        public Email addUniqueArg(String key, String val) throws JSONException {
            this.smtpapi.addUniqueArg(key, val);
            return this;
        }

        public JSONObject getUniqueArgs() throws JSONException {
            return this.smtpapi.getUniqueArgs();
        }

        public Email addCategory(String category) throws JSONException {
            this.smtpapi.addCategory(category);
            return this;
        }

        public String[] getCategories() throws JSONException {
            return this.smtpapi.getCategories();
        }

        public Email addSection(String key, String val) throws JSONException {
            this.smtpapi.addSection(key, val);
            return this;
        }

        public JSONObject getSections() throws JSONException {
            return this.smtpapi.getSections();
        }

        public Email addFilter(String filter_name, String parameter_name, String parameter_value) throws JSONException {
            this.smtpapi.addFilter(filter_name, parameter_name, parameter_value);
            return this;
        }

        public JSONObject getFilters() throws JSONException {
            return this.smtpapi.getFilters();
        }

        public Email setASMGroupId(int val) throws JSONException {
            this.smtpapi.setASMGroupId(val);
            return this;
        }

        public Integer getASMGroupId() throws JSONException {
            return this.smtpapi.getASMGroupId();
        }

        public Email setSendAt(int sendAt) throws JSONException {
            this.smtpapi.setSendAt(sendAt);
            return this;
        }

        public int getSendAt() throws JSONException {
            return this.smtpapi.getSendAt();
        }

        /**
         * Convenience method to set the template
         *
         * @param templateId The ID string of your template
         * @return this
         */
        public Email setTemplateId(String templateId) throws JSONException {
            this.getSMTPAPI().addFilter("templates", "enable", 1);
            this.getSMTPAPI().addFilter("templates", "template_id", templateId);
            return this;
        }

        public Email addAttachment(String name, File file) throws IOException, FileNotFoundException {
            return this.addAttachment(name, new FileInputStream(file));
        }

        public Email addAttachment(String name, String file) throws IOException {
            return this.addAttachment(name, new ByteArrayInputStream(file.getBytes()));
        }

        public Email addAttachment(String name, InputStream file) throws IOException {
            this.attachments.put(name, file);
            return this;
        }

        public Map getAttachments() {
            return this.attachments;
        }

        public Email addContentId(String attachmentName, String cid) {
            this.contents.put(attachmentName, cid);
            return this;
        }

        public Map getContentIds() {
            return this.contents;
        }

        public Email addHeader(String key, String val) {
            this.headers.put(key, val);
            return this;
        }

        public Map getHeaders() {
            return this.headers;
        }

        public SMTPAPI getSMTPAPI() {
            return this.smtpapi;
        }
    }
}

class ResponseSendGrid  {
    public interface Listener extends EventListener {
        void onResponse(JSONObject response);
    }

    public interface ErrorListener extends EventListener {
        void onErrorResponse(SendGrid.SendGridException error);
    }
}
