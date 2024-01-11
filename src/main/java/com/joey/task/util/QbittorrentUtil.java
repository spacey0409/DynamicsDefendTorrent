package com.joey.task.util;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
//https://github.com/qbittorrent/qBittorrent/wiki/WebUI-API-(qBittorrent-4.1)#get-torrent-list
public class QbittorrentUtil {


    @Value("${qbittorrent.url}")
    private String qbittorrentUrl;

    @Value("${qbittorrent.username}")
    private String qbittorrentUsername;

    @Value("${qbittorrent.password}")
    private String qbittorrentPassword;

    @Value("${qbittorrent.savePath}")
    private String qbittorrentSavePath;

    public String login() {
        HttpResponse response = HttpUtil.createPost(qbittorrentUrl + "/api/v2/auth/login").form("username", qbittorrentUsername).form("password", qbittorrentPassword).execute();
        if (response.getStatus() == 200) {
            if (response.body().equalsIgnoreCase("ok.")) {
                log.info("qbittorrent登录成功");
                String sid = response.getCookie("SID").toString();
                return sid;
            }
        }
        return null;
    }


    public JSONArray getTorrentList(String sid, String tag) {
        HttpResponse response = HttpUtil.createGet(qbittorrentUrl + "/api/v2/torrents/info?tag=" + tag).cookie(sid).execute();
        JSONArray jsonArray = new JSONArray();
        if (response.getStatus() == 200) {
            String body = response.body();
            System.out.println(body);
            jsonArray = JSONArray.parseArray(body);
            for (int i = 0; i < jsonArray.size(); i++) {
                JSONObject qbJson = jsonArray.getJSONObject(i);
                System.out.println(qbJson);

            }
        }
        return jsonArray;
    }

    public void deleteTorrent(String hash, String sid) {
        HttpResponse response = HttpUtil.createPost(qbittorrentUrl + "/api/v2/torrents/delete").body("hashes=" + hash + "&deleteFiles=true").cookie(sid).execute();
        if (response.getStatus() == 200) {
            System.out.println(response.body());
        }
    }


    public boolean addTorrents(String fileUrl, String sid, String tag) {
        HttpResponse response = HttpUtil.createPost(qbittorrentUrl + "/api/v2/torrents/add").cookie(sid).form("urls", fileUrl).form("autoTMM", false).form("savepath", qbittorrentSavePath).form("paused", false).form("contentLayout", "Original")
                //下载限制
                .form("dlLimit", "NaN")
                //上传限制
                .form("upLimit", "NaN").form("tags", tag).execute();
        if (response.getStatus() == 200) {
            return true;
        }
        return false;
    }

}
