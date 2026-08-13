package com.local.tgbreview;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final String USER_ID = "8358215";
    private static final String USER_NAME = "\u65b0\u751f\u5f00\u59cb";
    private static final String SINCE_DATE = "2026-08-01";
    private static final String WWW = "https://www.tgb.cn";
    private static final String M = "https://m.tgb.cn";
    private static final String SHUO = "https://shuo.tgb.cn";
    private static final String MORE_REPLY = WWW + "/user/blog/moreReplyMod?userID=" + USER_ID + "&pageNo=";

    private WebView webView;
    private SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("tgb_review", MODE_PRIVATE);
        CookieManager.getInstance().setAcceptCookie(true);

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(false);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new Bridge(), "TGB");
        setContentView(webView);
        webView.loadUrl("file:///android_asset/app.html");
    }

    @Override
    public void onBackPressed() {
        String url = webView.getUrl();
        if (url != null && url.startsWith("http")) {
            webView.loadUrl("file:///android_asset/app.html");
            return;
        }
        if (webView.canGoBack()) {
            webView.goBack();
            return;
        }
        super.onBackPressed();
    }

    private class Bridge {
        @JavascriptInterface
        public void getData(String payload, String callback) {
            try {
                JSONObject out = new JSONObject();
                out.put("rows", new JSONArray(prefs.getString("rows", "[]")));
                out.put("notes", new JSONObject(prefs.getString("notes", "{}")));
                out.put("generatedAt", prefs.getString("generatedAt", ""));
                callback(callback, out);
            } catch (Exception e) {
                callback(callback, error(e));
            }
        }

        @JavascriptInterface
        public void saveNotes(String payload, String callback) {
            try {
                JSONObject input = new JSONObject(payload == null || payload.isEmpty() ? "{}" : payload);
                JSONObject notes = input.optJSONObject("notes");
                if (notes == null) notes = new JSONObject();
                prefs.edit().putString("notes", notes.toString()).apply();
                JSONObject out = new JSONObject();
                out.put("ok", true);
                callback(callback, out);
            } catch (Exception e) {
                callback(callback, error(e));
            }
        }

        @JavascriptInterface
        public void openLogin(String payload, String callback) {
            runOnUiThread(() -> webView.loadUrl(WWW + "/blog/" + USER_ID));
            try {
                JSONObject out = new JSONObject();
                out.put("ok", true);
                callback(callback, out);
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public void refresh(String payload, String callback) {
            executor.execute(() -> {
                try {
                    JSONArray rows = collectAll();
                    String now = now();
                    prefs.edit()
                            .putString("rows", rows.toString())
                            .putString("generatedAt", now)
                            .apply();
                    JSONObject out = new JSONObject();
                    out.put("ok", true);
                    out.put("rows", rows);
                    out.put("notes", new JSONObject(prefs.getString("notes", "{}")));
                    out.put("generatedAt", now);
                    callback(callback, out);
                } catch (Exception e) {
                    callback(callback, error(e));
                }
            });
        }
    }

    private JSONArray collectAll() throws Exception {
        LinkedHashMap<String, JSONObject> merged = new LinkedHashMap<>();
        JSONArray oldRows = new JSONArray(prefs.getString("rows", "[]"));
        for (int i = 0; i < oldRows.length(); i++) {
            JSONObject row = oldRows.getJSONObject(i);
            putMerged(merged, row);
        }

        JSONArray topics = collectTopics();
        for (int i = 0; i < topics.length(); i++) {
            JSONObject row = topics.getJSONObject(i);
            if (onOrAfter(row.optString("\u53d1\u5e03\u65f6\u95f4"))) {
                putMerged(merged, row);
            }
        }

        JSONArray shorts = collectShorts();
        for (int i = 0; i < shorts.length(); i++) {
            JSONObject row = shorts.getJSONObject(i);
            if (onOrAfter(row.optString("\u53d1\u5e03\u65f6\u95f4"))) {
                putMerged(merged, row);
            }
        }

        JSONArray latestReplies = collectLatestReplies();
        for (int i = 0; i < latestReplies.length(); i++) {
            JSONObject row = latestReplies.getJSONObject(i);
            if (onOrAfter(row.optString("\u53d1\u5e03\u65f6\u95f4"))) {
                putMerged(merged, row);
            }
        }

        JSONArray replies = collectReplies(topics, oldRows);
        for (int i = 0; i < replies.length(); i++) {
            JSONObject row = replies.getJSONObject(i);
            if (onOrAfter(row.optString("\u53d1\u5e03\u65f6\u95f4"))) {
                putMerged(merged, row);
            }
        }

        List<JSONObject> sorted = new ArrayList<>(merged.values());
        sorted.sort((a, b) -> b.optString("\u53d1\u5e03\u65f6\u95f4").compareTo(a.optString("\u53d1\u5e03\u65f6\u95f4")));
        JSONArray out = new JSONArray();
        for (JSONObject row : sorted) out.put(row);
        return out;
    }

    private void putMerged(LinkedHashMap<String, JSONObject> merged, JSONObject row) {
        String replyId = row.optString("replyID", "");
        if (!replyId.isEmpty()) {
            for (Map.Entry<String, JSONObject> entry : merged.entrySet()) {
                JSONObject existing = entry.getValue();
                if (replyId.equals(existing.optString("replyID", ""))) {
                    if (row.optString("\u6b63\u6587\u5185\u5bb9", "").length() > existing.optString("\u6b63\u6587\u5185\u5bb9", "").length()) {
                        entry.setValue(row);
                    }
                    return;
                }
            }
        }
        merged.put(row.optString("\u552f\u4e00ID"), row);
    }

    private JSONArray collectTopics() throws Exception {
        JSONArray out = new JSONArray();
        int maxPages = 3;
        for (int page = 1; page <= maxPages; page++) {
            String url = M + "/mBlogTopicAjax?userID=" + USER_ID + "&sortFlag=W&pageNo=" + page;
            JSONObject data = new JSONObject(get(url, M + "/blog/" + USER_ID));
            if (!data.optBoolean("status")) break;
            JSONObject dto = data.optJSONObject("dto");
            if (dto == null) break;
            JSONArray list = dto.optJSONArray("listTopic");
            if (list == null || list.length() == 0) break;
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.getJSONObject(i);
                JSONObject row = baseRow("tgb-topic-" + item.optString("topicID"), "\u4e3b\u5e16");
                row.put("\u53d1\u5e03\u65f6\u95f4", dt(item.optString("postDate")));
                row.put("\u6807\u9898", clean(item.optString("subject")));
                row.put("\u6b63\u6587\u5185\u5bb9", clean(item.optString("content")));
                row.put("\u539f\u6587\u94fe\u63a5", WWW + "/a/" + item.optString("newTopicID"));
                row.put("\u70b9\u8d5e\u6570", item.opt("usefulNum"));
                row.put("\u6d4f\u89c8\u6570", item.opt("totalViewNum"));
                row.put("\u8bc4\u8bba\u6570", item.opt("totalReplyNum"));
                row.put("topicID", item.optString("topicID"));
                row.put("newTopicID", item.optString("newTopicID"));
                out.put(row);
            }
        }
        return out;
    }

    private JSONArray collectShorts() {
        JSONArray out = new JSONArray();
        try {
            for (int page = 1; page <= 3; page++) {
                String url = SHUO + "/shuo/getUserBlogShuo?userID=" + USER_ID + "&pageNo=" + page + "&seq=0&type=LS";
                JSONObject data = new JSONObject(get(url, WWW + "/blog/" + USER_ID));
                if (!data.optBoolean("status")) break;
                JSONObject dto = data.optJSONObject("dto");
                JSONArray list = dto == null ? null : dto.optJSONArray("list");
                if (list == null || list.length() == 0) break;
                for (int i = 0; i < list.length(); i++) {
                    JSONObject item = list.getJSONObject(i);
                    String shuoId = item.optString("shuoID");
                    JSONObject row = baseRow("tgb-short-" + shuoId, "\u8bf4\u8bf4");
                    row.put("\u53d1\u5e03\u65f6\u95f4", dt(item.optString("postDate", item.optString("createDate"))));
                    row.put("\u6807\u9898", clean(item.optString("subject", "\u8bf4\u8bf4")));
                    row.put("\u6b63\u6587\u5185\u5bb9", clean(first(item, "summary", "body", "content")));
                    row.put("\u539f\u6587\u94fe\u63a5", WWW + "/shuo/toViewShuo?shuoID=" + shuoId);
                    row.put("\u70b9\u8d5e\u6570", item.opt("usefulnum"));
                    row.put("\u8bc4\u8bba\u6570", item.opt("replynum"));
                    row.put("shuoID", shuoId);
                    out.put(row);
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private JSONArray collectReplies(JSONArray topics, JSONArray oldRows) throws Exception {
        LinkedHashMap<String, JSONObject> topicRows = new LinkedHashMap<>();
        for (int i = 0; i < oldRows.length(); i++) {
            JSONObject r = oldRows.getJSONObject(i);
            if ("\u4e3b\u5e16".equals(r.optString("\u7c7b\u578b"))) topicRows.put(r.optString("topicID"), r);
        }
        for (int i = 0; i < topics.length(); i++) {
            JSONObject r = topics.getJSONObject(i);
            topicRows.put(r.optString("topicID"), r);
        }

        JSONArray out = new JSONArray();
        for (JSONObject topic : topicRows.values()) {
            String topicId = topic.optString("topicID");
            String newTopicId = topic.optString("newTopicID");
            if (topicId.isEmpty()) continue;
            for (int page = 1; page <= 3; page++) {
                String topicPath = page > 1 ? newTopicId + "-" + page : newTopicId;
                String url = WWW + "/a/" + topicPath + "?type=Z";
                String html = get(url, WWW + "/a/" + newTopicId);
                JSONArray rows = parseReplyRows(html, topicId, newTopicId, topic.optString("\u6807\u9898"));
                if (rows.length() == 0) break;
                for (int i = 0; i < rows.length(); i++) out.put(rows.getJSONObject(i));
            }
        }
        return out;
    }

    private JSONArray collectLatestReplies() throws Exception {
        JSONArray out = new JSONArray();
        for (int page = 1; page <= 3; page++) {
            String html = get(MORE_REPLY + page, WWW + "/blog/" + USER_ID);
            JSONArray rows = parseLatestReplyRows(html);
            if (rows.length() == 0) break;
            for (int i = 0; i < rows.length(); i++) out.put(rows.getJSONObject(i));
        }
        return out;
    }

    private JSONArray parseLatestReplyRows(String html) throws Exception {
        JSONArray out = new JSONArray();
        Pattern blockPattern = Pattern.compile("<div class=\"blogReply\">.*?(?=\\n\\s*<div class=\"blogReply\">|\\n\\s*</div>\\s*<div class=\"page|$)", Pattern.DOTALL);
        Matcher matcher = blockPattern.matcher(html);
        while (matcher.find()) {
            String block = matcher.group();
            String postDate = htmlText(find(block, "<span class=\"blogReply-date\">(.*?)</span>"));
            String topicHref = find(block, "<div class=\"blogReply-from\">.*?<a href=\"([^\"]+)\"");
            String topicTitle = htmlText(find(block, "<div class=\"blogReply-from\">.*?<a href=\"[^\"]+\" title=\"([^\"]*)\""));
            String replyHref = find(block, "<a href=\"([^\"]+)\"[^>]*class=\"blogReply-subinfo[^\"]*\"[^>]*>");
            String summary = htmlText(find(block, "<a href=\"[^\"]+\"[^>]*class=\"blogReply-subinfo[^\"]*\"[^>]*>(.*?)</a>"));
            summary = summary.replaceAll("\\(\\d+\\)\\s*$", "").trim();
            String useful = htmlText(find(block, "<span class=\"zanNums\">(.*?)</span>"));
            String replyId = find(replyHref, "/(\\d+)#\\d+");
            String newTopicId = find(replyHref, "/a/([^/?#]+)");
            if (newTopicId.isEmpty()) newTopicId = find(topicHref, "/a/([^/?#]+)");
            if (replyId.isEmpty() || summary.isEmpty()) continue;
            String link = WWW + replyHref;
            JSONObject row = baseRow("tgb-reply-latest-" + replyId, "\u8ddf\u5e16");
            row.put("\u53d1\u5e03\u65f6\u95f4", dt(postDate));
            row.put("\u6807\u9898", topicTitle);
            row.put("\u6b63\u6587\u5185\u5bb9", summary);
            row.put("\u539f\u6587\u94fe\u63a5", link);
            row.put("\u70b9\u8d5e\u6570", useful);
            row.put("newTopicID", newTopicId);
            row.put("replyID", replyId);
            row.put("\u6240\u5c5e\u4e3b\u5e16\u6807\u9898", topicTitle);
            String full = fetchFullReplyContent(link, replyId);
            if (!full.isEmpty()) row.put("\u6b63\u6587\u5185\u5bb9", full);
            out.put(row);
        }
        return out;
    }

    private String fetchFullReplyContent(String link, String replyId) {
        try {
            String html = get(link, MORE_REPLY + "1");
            String subject = find(html, "<div id=\"gioMsg_R_" + Pattern.quote(replyId) + "\"[^>]*\\ssubject=\"([^\"]*)\"");
            if (!subject.isEmpty()) return attrHtmlText(subject);
            String block = find(html, "<div class=\"comment-data[^\"]*user_" + USER_ID + "[^\"]*\".*?(?:data-reply-id=\"" + Pattern.quote(replyId) + "\"|id=\"reply" + Pattern.quote(replyId) + "\").*?(?=\\n\\s*<div class=\"comment-data |\\n\\s*<div class=\"no-reply\")");
            String body = htmlText(find(block, "<div class=\"comment-data-text\" id=\"reply\\d+\">(.*?)</div>"));
            return clean(body);
        } catch (Exception ignored) {
            return "";
        }
    }

    private JSONArray parseReplyRows(String html, String topicId, String newTopicId, String topicTitle) throws Exception {
        JSONArray out = new JSONArray();
        Pattern blockPattern = Pattern.compile("<div class=\"comment-data user_" + USER_ID + "\".*?(?=\\n\\s*<div class=\"comment-data |\\n\\s*<div class=\"no-reply\")", Pattern.DOTALL);
        Matcher matcher = blockPattern.matcher(html);
        while (matcher.find()) {
            String block = matcher.group();
            String replyId = firstNonEmpty(
                    find(block, "data-reply-id=\"(\\d+)\""),
                    find(block, "data-original=\"(\\d+)\"")
            );
            String postDate = firstNonEmpty(
                    find(block, "data-post-date=\"([^\"]+)\""),
                    htmlText(find(block, "<span class=\"pcyclspan[^\"]*\"[^>]*>(.*?)</span>"))
            );
            String useful = firstNonEmpty(
                    find(block, "data-useful-num=\"([^\"]*)\""),
                    find(block, "打赏总积分:<span style=\"color:red;\">(\\d+)</span>")
            );
            String body = htmlText(find(block, "<div class=\"comment-data-text\" id=\"reply\\d+\">(.*?)</div>"));
            if (replyId.isEmpty() && body.isEmpty()) continue;
            JSONObject row = baseRow("tgb-reply-" + topicId + "-" + replyId, "\u8ddf\u5e16");
            row.put("\u53d1\u5e03\u65f6\u95f4", dt(postDate));
            row.put("\u6807\u9898", topicTitle);
            row.put("\u6b63\u6587\u5185\u5bb9", clean(body));
            row.put("\u539f\u6587\u94fe\u63a5", WWW + "/a/" + newTopicId + "/" + replyId + "#" + replyId);
            row.put("\u70b9\u8d5e\u6570", useful);
            row.put("topicID", topicId);
            row.put("newTopicID", newTopicId);
            row.put("replyID", replyId);
            row.put("\u6240\u5c5e\u4e3b\u5e16\u6807\u9898", topicTitle);
            out.put(row);
        }
        return out;
    }

    private JSONObject baseRow(String id, String type) throws Exception {
        JSONObject row = new JSONObject();
        row.put("\u552f\u4e00ID", id);
        row.put("\u6293\u53d6\u65f6\u95f4", now());
        row.put("\u5e73\u53f0", "\u6dd8\u80a1\u5427");
        row.put("\u4f5c\u8005", USER_NAME);
        row.put("\u7c7b\u578b", type);
        row.put("\u70b9\u8d5e\u6570", "");
        row.put("\u6d4f\u89c8\u6570", "");
        row.put("\u8bc4\u8bba\u6570", "");
        row.put("topicID", "");
        row.put("newTopicID", "");
        row.put("shuoID", "");
        row.put("replyID", "");
        row.put("\u6240\u5c5e\u4e3b\u5e16\u6807\u9898", "");
        return row;
    }

    private String get(String url, String referer) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(20000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 Android TGBReview");
        conn.setRequestProperty("Accept", "application/json,text/html;q=0.9,*/*;q=0.8");
        conn.setRequestProperty("Referer", referer);
        String cookie = cookies();
        if (!cookie.isEmpty()) conn.setRequestProperty("Cookie", cookie);
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line).append("\n");
        reader.close();
        return sb.toString();
    }

    private String cookies() {
        StringBuilder sb = new StringBuilder();
        CookieManager cm = CookieManager.getInstance();
        for (String url : new String[]{WWW, M, SHUO}) {
            String cookie = cm.getCookie(url);
            if (cookie != null && !cookie.isEmpty()) {
                if (sb.length() > 0) sb.append("; ");
                sb.append(cookie);
            }
        }
        return sb.toString();
    }

    private void callback(String name, JSONObject payload) {
        runOnUiThread(() -> webView.evaluateJavascript(name + "(" + JSONObject.quote(payload.toString()) + ")", null));
    }

    private JSONObject error(Exception e) {
        JSONObject out = new JSONObject();
        try {
            out.put("ok", false);
            out.put("error", e.getMessage() == null ? e.toString() : e.getMessage());
        } catch (Exception ignored) {
        }
        return out;
    }

    private String first(JSONObject obj, String... keys) {
        for (String key : keys) {
            String v = obj.optString(key, "");
            if (!v.isEmpty() && !"null".equals(v)) return v;
        }
        return "";
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    private String find(String text, String regex) {
        Matcher m = Pattern.compile(regex, Pattern.DOTALL).matcher(text == null ? "" : text);
        return m.find() ? m.group(1) : "";
    }

    private String htmlText(String value) {
        return (value == null ? "" : value)
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&#39;", "'")
                .replace("&quot;", "\"");
    }

    private String attrHtmlText(String value) {
        return htmlText(htmlText(value));
    }

    private String clean(String value) {
        return htmlText(value).replaceAll("[ \\t\\r\\f\\x0B]+", " ").replaceAll("\\n\\s+", "\n").trim();
    }

    private String dt(String value) {
        String v = clean(value);
        return v.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}") ? v + ":00" : v;
    }

    private boolean onOrAfter(String time) {
        return time != null && time.length() >= 10 && time.substring(0, 10).compareTo(SINCE_DATE) >= 0;
    }

    private String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date());
    }
}
