package me.iacn.biliroaming.hook;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import me.iacn.biliroaming.XposedInit;
import me.iacn.biliroaming.network.BiliRoamingApi;
import me.iacn.biliroaming.network.StreamUtils;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static me.iacn.biliroaming.Constant.TAG;

/**
 * Created by iAcn on 2019/3/29
 * Email i@iacn.me
 */
public class BangumiPlayUrlHook extends BaseHook {

    public BangumiPlayUrlHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public void startHook() {
        if (!XposedInit.sPrefs.getBoolean("main_func", false)) return;
        Log.d(TAG, "startHook: BangumiPlayUrl");

        findAndHookMethod("com.bilibili.nativelibrary.LibBili", mClassLoader, "a",
                Map.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Map params = (Map)param.args[0];
                if(XposedInit.sPrefs.getBoolean("allow_download", false) &&
                        params.containsKey("ep_id")) {
                    params.remove("dl");
                }
                if(XposedInit.sPrefs.getBoolean("simulate", false)) {
                    params.put("appkey", "1d8b6e7d45233436");
                    params.put("platform", "android");
                    params.put("mobi_app", "android");
                }
            }
        });


        findAndHookMethod("java.net.InetAddress",mClassLoader,
                "getAllByName", String.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String host = (String)param.args[0];
                        Log.d(TAG, "Host: " + host);
                        String cdn = getCDN();
                        if (!cdn.isEmpty() && host.equals("upos-hz-mirrorakam.akamaized.net")) {
                            InetAddress[] result = {InetAddress.getByName(cdn)};
                            param.setResult(result);
                            Log.d(TAG, "Replace by CDN: " + cdn);
                        }
                    }
                });
        findAndHookMethod("java.net.InetAddress",mClassLoader,
                "getByName", String.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String host = (String)param.args[0];
                        String cdn = getCDN();
                        if (!cdn.isEmpty() && host.equals("upos-hz-mirrorakam.akamaized.net")) {
                            InetAddress result = InetAddress.getByName(cdn);
                            param.setResult(result);
                            Log.d(TAG, "Replace by CDN: " + cdn);
                        }
                    }
                });

        findAndHookMethod("com.bilibili.lib.okhttp.huc.OkHttpURLConnection", mClassLoader,
                "getInputStream", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        // Found from "b.ecy" in version 5.39.1
                        HttpURLConnection connection = (HttpURLConnection) param.thisObject;
                        String urlString = connection.getURL().toString();

                        if (urlString.startsWith("https://api.bilibili.com/pgc/player/api/playurl")) {
                            String queryString = urlString.substring(urlString.indexOf("?") + 1);
                            if (queryString.contains("ep_id=") || queryString.contains("module=bangumi")) {
                                InputStream inputStream = (InputStream) param.getResult();
                                String encoding = connection.getContentEncoding();
                                String content = StreamUtils.getContent(inputStream, encoding);

                                if (isLimitWatchingArea(content)) {
                                    if (XposedInit.sPrefs.getBoolean("use_biliplus", false))
                                        content = BiliRoamingApi.getPlayUrl_BP(queryString);
                                    else
                                        content = BiliRoamingApi.getPlayUrl(queryString);
                                    Log.d(TAG, "Has replaced play url with proxy server");
                                }

                                param.setResult(new ByteArrayInputStream(content.getBytes()));
                            }
                        }
                    }
                });
    }

    private boolean isLimitWatchingArea(String jsonText) {
        try {
            JSONObject json = new JSONObject(jsonText);
            int code = json.optInt("code");
            Log.d(TAG, "PlayUrlInformation: code = " + code);

            return code == -10403;
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }

    private String getCDN() {
        boolean use_cdn = XposedInit.sPrefs.getBoolean("use_cdn", false);
        if(!use_cdn) return "";
        String cdn = XposedInit.sPrefs.getString("cdn", "");
        if(cdn.isEmpty()) cdn = XposedInit.sPrefs.getString("custom_cdn", "");
        return cdn;
    }
}
