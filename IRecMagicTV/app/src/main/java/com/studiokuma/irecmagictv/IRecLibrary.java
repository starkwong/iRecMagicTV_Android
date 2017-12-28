package com.studiokuma.irecmagictv;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;

import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGetHC4;
import org.apache.http.impl.client.BasicCookieStoreHC4;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.impl.cookie.BasicClientCookieHC4;
import org.apache.http.util.EntityUtilsHC4;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.SocketException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Created by starkwong on 6/11/14.
 */
public class IRecLibrary {
    public interface OnIRecListResult {
        public void onIRecListResult(Class objectType, Object[] result);
    }

    public static class HTTPException extends SocketException {
        private int status;
        private String statusMessage;
        private String extra;

        public HTTPException(int status, String statusMessage, String extra) {
            this.status=status;
            this.statusMessage=statusMessage;
            this.extra=extra;
        }

        public int getStatus() {
            return status;
        }

        public String getStatusMessage() {
            return statusMessage;
        }

        public String getExtra() {
            return extra;
        }

        public boolean isRedirection() {
            return this.status==301 || this.status==302;
        }

        public boolean isRedirectToLogin() {
            return this.extra.contains("login.php");
        }

        @Override
        public String getMessage() {
            return statusMessage;
        }
    }

    public class Channel {
        /*
<a href=programme-list.php?channelsel=11>
<span class="chnumber">011</span>
<span class="chlogo"><img src="images/channelListLogo_atv.png" alt="ATV"></span>
<span class="chname">本港台</span>
</a>
         */
        public String href;
        public String chnumber;
        public String chlogo;
        public String chlogo_alt;
        public String chname;
        public String channelsel;

        public Channel(String name, String href) {
            this.chname=name;
            this.href=href;
            channelsel=href.substring(href.indexOf('=') + 1);
            this.chlogo_alt="";
        }

        public Channel(Element element) {
            Element a=element.select("a").first();
            href=a.attr("href");
            channelsel=href.substring(href.indexOf('=') + 1);

            for (Element span: a.children()) {
                String cssClass=span.attr("class");

                if (cssClass.equals("chnumber")) {
                    chnumber=span.text();
                } else if (cssClass.equals("chlogo")) {
                    Element img=span.select("img").first();
                    chlogo=img.attr("src");
                    chlogo_alt=img.attr("alt");
                } else if (cssClass.equals("chname")) {
                    chname=span.text();
                }

            }
        }

        @Override
        public String toString() {
            return "NW="+chlogo_alt+" CH="+chnumber+" CN="+chname;
        }
    }

    public class Device {
        /*
<a href=set_regname.php?mtvsel=%7EMTV7000D&mid=97de68b97d62a30641de796c298dff4e>
~MTV7000D</a>
         */
        public String href;
        public String name;

        public Device(String name, String href) {
            this.name=name;
            this.href=href;
        }

        public Device(Element element) {
            Element a=element.select("a").first();
            href=a.attr("href");
            name=a.text().trim();
        }

        @Override
        public String toString() {
            return name; // "Device="+name+" href="+href;
        }
    }

    private static String _programme_lastPD=null;

    public static class Programme implements Comparable<Programme> {
        /*
<a name=Thu href="programme-info.php?channelsel=11&datetimesel=2014-12-31 17:35:00">
<span class="progtime" >01:35</span>
<span class="progday"  >週四 1</span>
<span class="progname" >恩雨之聲 (粵/國)(S)</span>
</a>
         */
        public String name;
        public String href;
        public String progtime;
        public String progday;
        public String progname;
        public long timestamp;
        public long duration;
        public boolean multi;
        public static Programme lastProgramme=null;

        public Programme(Element element) {
            Element a=element.select("a").first();
            href=a.attr("href");
            name=a.attr("name");

            if (_programme_lastPD==null) {
                // First item and no weekday specified
                _programme_lastPD=String.format("%ta",new Date(Calendar.getInstance().getTimeInMillis()));
            }
            if (name==null || name.length()==0 || name.equals("jump"))
                name=_programme_lastPD;
            else
                _programme_lastPD=name;

            for (Element span: a.children()) {
                String cssClass=span.attr("class");

                if (cssClass.equals("progtime")) {
                    progtime=span.text();
                } else if (cssClass.equals("progday")) {
                    progday=span.text();
                } else if (cssClass.equals("progname")) {
                    progname=span.text();
                }
            }

            generateTimestamp();
        }

        public Programme(JSONObject jsonObject) {
            try {
                href = jsonObject.getString("href");
                name = jsonObject.getString("name");
                progtime = jsonObject.getString("progtime");
                progday = jsonObject.getString("progday");
                progname = jsonObject.getString("progname");
                multi = jsonObject.has("single")?!jsonObject.getBoolean("single"):false;

                generateTimestamp();

                duration = jsonObject.optLong("duration", 0);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        private void generateTimestamp() {
            SimpleDateFormat simpleDateFormat=new SimpleDateFormat("y-M-d H:m:s");
            try {
                Date date=simpleDateFormat.parse(href.substring(href.indexOf("&datetimesel=")+13));
                Calendar calendar= GregorianCalendar.getInstance(TimeZone.getTimeZone("UTC"));
                calendar.setTime(date);
                calendar.add(Calendar.HOUR_OF_DAY, 8);
                //calendar.setTimeZone(TimeZone.getTimeZone("GMT+8"));
                //calendar.add(Calendar.HOUR_OF_DAY,8); // timestamp should be always in UTC
                timestamp=calendar.getTimeInMillis();

                if (lastProgramme!=null) {
                    lastProgramme.duration=timestamp-lastProgramme.timestamp;
                }
                duration=0;
                lastProgramme=null;
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }

        @Override
        public String toString() {
            return "PD="+name+" PT="+progtime+" PN="+progname;
        }

        public JSONObject toJSONObject() {
            JSONObject jsonObject=new JSONObject();
            try {
                jsonObject.put("href", href);
                jsonObject.put("name", name);
                jsonObject.put("progtime", progtime);
                jsonObject.put("progday", progday);
                jsonObject.put("progname", progname);
                jsonObject.put("single", !multi);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            return jsonObject;
        }

        @Override
        public int compareTo(Programme programme) {
            return timestamp<programme.timestamp?-1:timestamp==programme.timestamp?0:1;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Programme)) return false;
            Programme po= (Programme) o;

            return po.href.equals(this.href);
        }
    }

    public static class Href {
        public String title;
        public String href;
        public Href(Element element, String currentUrl) {
            // <li><a href=presingleRec.php class="singlerec">單一錄影</a></li>
            Element li=element.select("li").first();
            title=li.text();
            href=/*currentUrl.substring(currentUrl.lastIndexOf('/')+1)+*/li.child(0).attr("href");
        }

        @Override
        public String toString() {
            return title;
        }
    }

    private static boolean MOCK=false;

    private static final String LOGTAG=IRecLibrary.class.getSimpleName();
    private static IRecLibrary sInstance;
    private static final String USERAGENT="Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/38.0.2125.111 Safari/537.36";
    private static final String HOST="http://irec.magictv.com";
    private static final boolean LOG=true;
    private byte[] buffer=new byte[65536];
    private static Boolean lock=new Boolean(false);
    private BasicCookieStoreHC4 cookieStore;
    private CloseableHttpClient httpClient;
    private Context context;
    private boolean loggedIn=false;
    private String lang="hk";

    public static IRecLibrary getsInstance(Context context) {
        if (sInstance==null) sInstance=new IRecLibrary(context);

        return sInstance;
    }

    private IRecLibrary(Context context) {
        /*
        cookieStore=new BasicCookieStoreHC4();
        httpClient= HttpClients.custom().setDefaultCookieStore(cookieStore).setConnectionManager(new BasicHttpClientConnectionManager()).build();
        */
        this.resetLogin();
        this.context=context;
    }

    private void log(String string) {
        if (LOG) System.out.println(string);
    }

    public void resetLogin() {
        loggedIn=false;
        cookieStore=new BasicCookieStoreHC4();
        httpClient= HttpClients.custom().setDefaultCookieStore(cookieStore).setConnectionManager(new BasicHttpClientConnectionManager()).build();
    }

    public byte[] fetchPage(String url, String referer) throws IOException {
        return fetchPage(url, referer, false);
    }

    public byte[] fetchPage(String url, String referer, boolean allowRedirect) throws IOException {
        synchronized (lock) {
            HttpGetHC4 httpGet=new HttpGetHC4(HOST+url.replace(' ','+'));
            httpGet.setConfig(RequestConfig.custom().setRedirectsEnabled(allowRedirect).build());
            httpGet.setHeader("User-Agent",USERAGENT);
            if (referer!=null) httpGet.setHeader("Referer",referer);

            HttpResponse response=httpClient.execute(httpGet);

            int responseCode = response.getStatusLine().getStatusCode();

            if (responseCode == 200) {
                byte[] payload=EntityUtilsHC4.toByteArray(response.getEntity());
                httpGet.releaseConnection();
                return payload;
            } else if (responseCode == 301 || responseCode == 302) {
                httpGet.releaseConnection();
                throw new HTTPException(responseCode, response.getStatusLine().getReasonPhrase(), response.getFirstHeader("Location").getValue());
            }

            return null;
        }
    }

    public String fetchPageAsString(String url, String referer) throws IOException {
        return fetchPageAsString(url, referer, false);
    }

    public String fetchPageAsString(String url, String referer, boolean allowRedirection) throws IOException {
        byte[] payload=fetchPage(url, referer, allowRedirection);

        return new String(payload,"UTF-8");
    }

    private boolean fetchRoot() {
        // This is using html for redirection to login.php

        try {
            String string=fetchPageAsString("/", null);
            if (string.contains("login.php")) {
                // string=fetchPageAsString("/"+lang+"/login.php", null);
                string=fetchPageAsString("/"+lang+"/setlang"+ (Locale.getDefault().equals(Locale.TRADITIONAL_CHINESE)?"CHI":"ENG") +".php", null, true);
                return string.contains("checklogin.php");
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        return false;
    }

    private String hashcode(String password)
    {
        String  salt = "1fd49003";

        for (int i = password.length(); i < 8; i++)
        {
            password += Character.toString((char)1); // String.fromCharCode(1);
        }
        String input = salt + password;

        for (int i = input.length(); i < 63; i++)
        {
            input += Character.toString((char)1); // String.fromCharCode(1);
        }
//    if (graphic_auth == "false")
//    {
//       input += (document.forms.loginform.myusername.value == 'user') ? 'U' : String.fromCharCode(1);
//    }
//    else
//    {
//       input += (document.forms.loginform.myusername.value == 'user') ? 'U' : String.fromCharCode(1);
//    }

        try {
            MessageDigest messageDigest=MessageDigest.getInstance("MD5");
            byte[] md5bin=messageDigest.digest(input.getBytes("UTF-8"));

            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < md5bin.length; i++)
                sb.append(Integer.toString((md5bin[i] & 0xff) + 0x100, 16).substring(1));

            sb.insert(0, salt);
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
        } catch (UnsupportedEncodingException e) {
        }

        return null;
    }

    private boolean login() {
        SharedPreferences sharedPreferences=context.getSharedPreferences(this.getClass().getSimpleName(),Context.MODE_PRIVATE);
        String username=sharedPreferences.getString("username","");
        String password=sharedPreferences.getString("password","");

        loggedIn=false;

        if (username.isEmpty() || password.isEmpty()) return false;

        try {
            password=new String(Base64.decode(password.substring(1),0),"UTF-8");
        } catch (Exception ex) {
            return false;
        }

        if (!fetchRoot()) return false;

        try {
            String hash=hashcode(password);
            BasicClientCookieHC4 cookies[]=new BasicClientCookieHC4[]{
                    new BasicClientCookieHC4("rememberdetails", "false"),
                    new BasicClientCookieHC4("cookname", ""),
                    new BasicClientCookieHC4("cookpass", "")
            };

            for (BasicClientCookieHC4 cookie: cookies) {
                cookie.setDomain("irec.magictv.com");
                cookie.setPath("/");
            }

            cookieStore.addCookies(cookies);

            fetchPage("/"+lang+"/checklogin.php?myusername=" + username + "&hash_code=" + hash, "http://irec.magictv.com/"+lang+"/login.php");
        } catch (HTTPException ex) {
            if (ex.isRedirection()) {
                log("Redirection="+ex.getExtra());
                loggedIn=!ex.isRedirectToLogin();
                return loggedIn;
            } else {
                throw new RuntimeException(ex);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        return false; // Should not run to this line
    }

    public void test(Context context) {

    }

    public void getChannelList(OnIRecListResult callback) {
        new AsyncTask<OnIRecListResult, Void, Channel[]>() {
            OnIRecListResult callback;
            Class returnClass=null;

            @Override
            protected Channel[] doInBackground(OnIRecListResult... callbacks) {
                callback=callbacks[0];

                if (MOCK) {
                    try {
                        Document document = Jsoup.parse(context.getAssets().open("channel-list.php#jump"), "UTF-8", "http://127.0.0.1");
                        Channel[] channels = parseChannelList(document);

                        returnClass = Channel.class;
                        return channels;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    if (this != null) return null;
                }

                for (int c=0; c<2; c++) {
                    try {
                        if (!loggedIn) throw new HTTPException(302,"Found","login.php");

                        String payload=fetchPageAsString("/"+lang+"/channel-list.php#jump", "http://irec.magictv.com/"+lang+"/login.php");
                        Document document = Jsoup.parse(payload, "http://127.0.0.1");
                        Channel[] channels=parseChannelList(document);

                        returnClass=Channel.class;
                        return channels;
                    } catch (HTTPException ex) {
                        if (ex.isRedirectToLogin()) {
                            if (c==1 || !login()) {
                                returnClass=IllegalAccessException.class;
                                return null;
                            }
                        }
                    } catch (IOException ex) {
                    }
                }


                return null;
            }

            @Override
            protected void onPostExecute(Channel[] channels) {
                callback.onIRecListResult(returnClass, channels);
            }

            private Channel[] parseChannelList(Document document) {
                Element element=document.select("div#toplinks > ul > li > a").first();
                String unit=element.text();

                SharedPreferences sharedPreferences=context.getSharedPreferences(IRecLibrary.class.getSimpleName(),Context.MODE_PRIVATE);
                sharedPreferences.edit().putString(context.getString(R.string.pref_device),unit).commit();

                element=document.select("ul#channellist").first();
                ArrayList<Channel> channels=new ArrayList<Channel>();
                Channel channel;

                for (Element li: element.children()) {
                    channels.add(channel=new Channel(li));
                    Log.i(LOGTAG,channel.toString());
                }

                return channels.toArray(new Channel[channels.size()]);
            }
        }.execute(callback);

    }

    public void getUnitList(OnIRecListResult callback) {
        new AsyncTask<OnIRecListResult, Void, Device[]>() {
            OnIRecListResult callback;
            Class returnClass=null;

            @Override
            protected Device[] doInBackground(OnIRecListResult... callbacks) {
                callback=callbacks[0];

                if (MOCK) {
                    try {
                        Document document = Jsoup.parse(context.getAssets().open("unit-list.php"), "UTF-8", "http://127.0.0.1");
                        Device[] devices = parseDeviceList(document);

                        returnClass = Device.class;
                        return devices;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    if (this != null) return null;
                }

                for (int c=0; c<2; c++) {
                    try {
                        if (!loggedIn) throw new HTTPException(302,"Found","login.php");

                        String payload=fetchPageAsString("/"+lang+"/unit-list.php", "http://irec.magictv.com/"+lang+"/channel-list.php#jump");
                        Document document = Jsoup.parse(payload, "http://127.0.0.1");
                        Device[] devices=parseDeviceList(document);

                        returnClass=Device.class;
                        return devices;
                    } catch (HTTPException ex) {
                        if (ex.isRedirectToLogin()) {
                            if (c==1 || !login()) {
                                returnClass=IllegalAccessException.class;
                                return null;
                            }
                        }
                    } catch (IOException ex) {
                    }
                }


                return null;
            }

            @Override
            protected void onPostExecute(Device[] devices) {
                callback.onIRecListResult(returnClass, devices);
            }

            private Device[] parseDeviceList(Document document) {
                Element element=document.select("ul#channellist").first();
                ArrayList<Device> devices=new ArrayList<Device>();
                Device device;

                for (Element li: element.children()) {
                    devices.add(device=new Device(li));
                    Log.i(LOGTAG,device.toString());
                }

                return devices.toArray(new Device[devices.size()]);
            }
        }.execute(callback);

    }

    public void getProgrammeList(String url, OnIRecListResult callback) {
        new AsyncTask<OnIRecListResult, Void, Programme[]>() {
            OnIRecListResult callback;
            Class returnClass=null;
            String url;

            @Override
            protected Programme[] doInBackground(OnIRecListResult... callbacks) {
                callback=callbacks[0];

                if (MOCK) {
                    try {
                        Document document = Jsoup.parse(context.getAssets().open("programme-list.php#jump"), "UTF-8", "http://127.0.0.1");
                        Programme[] programmes = parseProgrammeList(document);

                        return programmes;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    if (this != null) return null;
                }

                for (int c=0; c<2; c++) {
                    try {
                        if (!loggedIn) throw new HTTPException(302,"Found","login.php");

                        String payload=fetchPageAsString("/"+lang+"/" + url, "http://irec.magictv.com/"+lang+"/login.php");
                        Document document = Jsoup.parse(payload, "http://127.0.0.1");
                        Programme[] programmes=parseProgrammeList(document);

                        returnClass=Programme.class;
                        return programmes;
                    } catch (HTTPException ex) {
                        if (ex.isRedirectToLogin()) {
                            if (c==1 || !login()) {
                                returnClass=IllegalAccessException.class;
                                return null;
                            }
                        }
                    } catch (IOException ex) {
                    }
                }


                return null;
            }

            @Override
            protected void onPostExecute(Programme[] channels) {
                callback.onIRecListResult(Programme.class, channels);
            }

            public AsyncTask<OnIRecListResult, Void, Programme[]> setParameters(String url) {
                this.url=url;
                return this;
            }

            private Programme[] parseProgrammeList(Document document) {
                Element element = document.select("ul#proglist").first();
                ArrayList<Programme> programmes = new ArrayList<Programme>();
                Programme programme;

                for (Element li : element.children()) {
                    programmes.add(programme = new Programme(li));
                    Programme.lastProgramme=programme;
                    Log.i(LOGTAG, programme.toString());
                }
                Programme.lastProgramme=null;

                return programmes.toArray(new Programme[programmes.size()]);
            }
        }.setParameters(url).execute(callback);

    }

    public void performGenericURL(String url, OnIRecListResult callback) {
        new AsyncTask<OnIRecListResult, Void, Object[]>() {
            OnIRecListResult callback;
            Class returnClass=null;
            String url;

            @Override
            protected Object[] doInBackground(OnIRecListResult... callbacks) {
                callback=callbacks[0];

                for (int c=0; c<2; c++) {
                    Document document=null;

                    if (MOCK) {
                        try {
                            String filename=url.substring(url.lastIndexOf('/') + 1);
                            if (filename.contains("?")) filename=filename.substring(0,filename.indexOf('?'));

                            document = Jsoup.parse(context.getAssets().open(filename), "UTF-8", "http://127.0.0.1");
                            // Href[] links = parseHrefList(document, url);

                            // return links;
                        } catch (IOException e) {
                            e.printStackTrace();
                            throw new RuntimeException(e);
                        }

                        // if (this != null) return null;
                    }

                    try {
                        if (!loggedIn) throw new HTTPException(302,"Found","login.php");

                        if (document==null) {
                            String payload = fetchPageAsString("/"+lang+"/" + url, "http://irec.magictv.com/"+lang+"/login.php", true);
                            document = Jsoup.parse(payload, "http://127.0.0.1");
                        }
                        Href[] links = parseHrefList(document, url);

                        if (links.length>0) {
                            if (links[0].href.contains("programme-list.php?channelsel=")) {
                                // Success
                                returnClass=null;
                                return null; //new Object[]{url};
                            } else if (links.length==1) {
                                c = -1;
                                url = links[0].href;
                                continue;
                            }
                        } else /*if (links.length==0)*/ {
                            throw new RuntimeException("links.length==0!");
                        }

                        String[] info=parseProgrammeInfo(document);

                        returnClass=Href.class;
                        return new Object[] {info, links};
                    } catch (HTTPException ex) {
                        if (ex.isRedirectToLogin()) {
                            if (c==1 || !login()) {
                                returnClass=IllegalAccessException.class;
                                return null;
                            }
                        }
                    } catch (IOException ex) {
                    }
                }


                return null;
            }

            @Override
            protected void onPostExecute(Object[] links) {
                callback.onIRecListResult(returnClass, links);
            }

            public AsyncTask<OnIRecListResult, Void, Object[]> setParameters(String url) {
                this.url=url;
                return this;
            }

            private Href[] parseHrefList(Document document, String currentUrl) {
                Elements elements = document.select("div");

                ArrayList<Href> links = new ArrayList<Href>();
                Href href;

                for (Element element: elements) {
                    if (element.id() != null && element.id().endsWith("options") && element.nodeName().toLowerCase().equals("div")) {
                        Elements elements2 = element.select("ul > li");
                        for (Element element2: elements2) {
                            links.add(href = new Href(element2, currentUrl));
                            Log.i(LOGTAG, href.toString()+": "+href.href);
                        }
                        /*
                        element = element.select("ul > li").first();
                        links.add(href = new Href(element, currentUrl));
                        Log.i(LOGTAG, href.toString());*/
                    }
                }

                return links.toArray(new Href[links.size()]);
            }
        }.setParameters(url).execute(callback);

    }

    public void prepareForRecording(String url, OnIRecListResult callback) {
        new AsyncTask<OnIRecListResult, Void, String[]>() {
            OnIRecListResult callback;
            Class returnClass=null;
            String url;

            @Override
            protected String[] doInBackground(OnIRecListResult... callbacks) {
                callback=callbacks[0];

                if (MOCK) {
                    try {
                        Document document = Jsoup.parse(context.getAssets().open("programme-info.php_success"), "UTF-8", "http://127.0.0.1");
                        String[] info = parseProgrammeInfo(document);

                        return info;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    if (this != null) return null;
                }

                for (int c=0; c<2; c++) {
                    try {
                        if (!loggedIn) throw new HTTPException(302,"Found","login.php");

                        String payload=fetchPageAsString("/"+lang+"/"+url, "http://irec.magictv.com/"+lang+"/programme-list.php#jump");
                        Document document = Jsoup.parse(payload, "http://127.0.0.1");
                        String[] info=parseProgrammeInfo(document);

                        returnClass=String.class;
                        return info;
                    } catch (HTTPException ex) {
                        if (ex.isRedirectToLogin()) {
                            if (c==1 || !login()) {
                                returnClass=IllegalAccessException.class;
                                return null;
                            }
                        }
                    } catch (IOException ex) {
                    }
                }


                return null;
            }

            @Override
            protected void onPostExecute(String[] channels) {
                callback.onIRecListResult(String.class, channels);
            }

            public AsyncTask<OnIRecListResult, Void, String[]> setParameters(String url) {
                this.url=url;
                return this;
            }

        }.setParameters(url).execute(callback);
    }

    public static String[] parseProgrammeInfo(Document document) {
        Element element=document.select("div#proghead").first();
        if (element==null) return null;

        ArrayList<String> info=new ArrayList<String>();
        for (int c=0; c<3; c++) {
            info.add(element.child(c).text());
        }

        element=document.select("div#proginfo").first();
        if (element==null) {
            info.add("");
        } else {
            info.add(element.text());
        }

        return info.toArray(new String[info.size()]);
    }

    public void performSingleRecording(OnIRecListResult callback) {
        new AsyncTask<OnIRecListResult, Void, String[]>() {
            OnIRecListResult callback;
            Class returnClass=null;
            String url;

            @Override
            protected String[] doInBackground(OnIRecListResult... callbacks) {
                callback=callbacks[0];

                if (MOCK) {
                    returnClass=null;
                    return null;

                    /*
                    try {
                        String payload;
                        Document dom=Jsoup.parse(context.getAssets().open("record-error.php_fail"), "UTF-8", "http://127.0.0.1");
                        Element p=dom.select("p.confirmation").first();
                        if (p==null) {
                            payload="(Unknown reason)";
                        } else {
                            payload=p.text();
                        }
                        returnClass=Exception.class;
                        return new String[]{payload};
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    if (this != null) return null;*/
                }

                String payload=null;

                for (int c=0; c<2; c++) {
                    try {
                        if (!loggedIn) throw new HTTPException(302,"Found","login.php");

                        // String payload=fetchPageAsString("/hk/presingleRec.php", "http://irec.magictv.com/hk/programme-info.php");
                        payload=fetchPageAsString("/"+lang+"/singleRec.php", "http://irec.magictv.com/"+lang+"/presingleRec.php");
                        throw new RuntimeException("Should not happen!");
                    } catch (HTTPException ex) {
                        if (ex.isRedirectToLogin()) {
                            if (c==1 || !login()) {
                                returnClass=IllegalAccessException.class;
                                return null;
                            }
                        } else {
                            while (ex!=null) {
                                try {
                                    payload = fetchPageAsString("/"+lang+"/" + ex.getExtra(), "http://irec.magictv.com/"+lang+"/programme-info.php");

                                    if (ex.getExtra().contains("record-confirm.php")) {
                                        returnClass=null;
                                        return null;
                                    }

                                    Document dom = Jsoup.parse(payload);
                                    Element p = dom.select("p.confirmation").first();
                                    if (p == null) {
                                        payload = "(Unknown reason)";
                                    } else {
                                        payload = p.text();
                                    }
                                    returnClass = Exception.class;
                                    return new String[]{payload};
                                } catch (HTTPException e) {
                                    ex=e;
                                } catch (IOException e) {
                                    e.printStackTrace();
                                    returnClass = Exception.class;
                                    return new String[]{e.getMessage()};
                                }
                            }
                        }
                    } catch (IOException ex) {
                    }
                }


                return null;
            }

            @Override
            protected void onPostExecute(String[] channels) {
                callback.onIRecListResult(returnClass, channels);
            }

            public AsyncTask<OnIRecListResult, Void, String[]> setParameters(String url) {
                this.url=url;
                return this;
            }
        }.execute(callback);
    }

    public void changeDevice(String href, OnIRecListResult callback) {
        new AsyncTask<OnIRecListResult, Void, String[]>() {
            OnIRecListResult callback;
            Class returnClass=null;
            String url;

            @Override
            protected String[] doInBackground(OnIRecListResult... callbacks) {
                callback=callbacks[0];

                if (MOCK) {
                    returnClass=null;
                    return null;
                }

                String payload=null;

                for (int c=0; c<2; c++) {
                    try {
                        if (!loggedIn) throw new HTTPException(302,"Found","login.php");

                        payload=fetchPageAsString("/"+lang+"/"+url, "http://irec.magictv.com/"+lang+"/unit-list.php");
                        throw new RuntimeException("Should not happen!");
                    } catch (HTTPException ex) {
                        if (ex.isRedirectToLogin()) {
                            if (c==1 || !login()) {
                                returnClass=IllegalAccessException.class;
                                return null;
                            }
                        } else {
                            if (ex.getExtra().contains("channel-list.php")) {
                                returnClass=null;
                                return null;
                            } else {
                                returnClass = Exception.class;
                                return new String[]{ex.getMessage()};
                            }
                        }
                    } catch (IOException ex) {
                    }
                }


                return null;
            }

            @Override
            protected void onPostExecute(String[] channels) {
                callback.onIRecListResult(returnClass, channels);
            }

            public AsyncTask<OnIRecListResult, Void, String[]> setParameters(String url) {
                this.url=url;
                return this;
            }
        }.setParameters(href).execute(callback);
    }

    public void setLoginDetails(String username, String password) {
        SharedPreferences sharedPreferences=context.getSharedPreferences(this.getClass().getSimpleName(),Context.MODE_PRIVATE);
        SharedPreferences.Editor editor=sharedPreferences.edit();
        editor.putString("username",username);
        editor.putString("password", "S"+Base64.encodeToString(password.getBytes(),0));
        editor.commit();
    }

    public Channel getPlaceholderChannel(String name, String href) {
        return new Channel(name, href);
    }

    public static void clearSession() {
        sInstance=null;
    }
}
