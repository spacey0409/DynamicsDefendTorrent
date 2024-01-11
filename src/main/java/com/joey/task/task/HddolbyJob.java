package com.joey.task.task;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.joey.task.util.DateUtils;
import com.joey.task.util.QbittorrentUtil;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@RestController
@RequestMapping
public class HddolbyJob extends QuartzJobBean {

    @Value("${hddolby.url}")
    private String hddolbyUrl;

    @Value("${hddolby.cookie}")
    private String hddolbyCookie;

    @Value("${hddolby.downhash}")
    private String hddolbyDownhash;

    @Value("${hddolby.publishDay}")
    private Integer hddolbyPublishDay;

    @Value("${hddolby.maxSeeder}")
    private Integer hddolbyMaxSeeder;


    @Value("${hddolby.maxLeecher}")
    private Integer hddolbyMaxLeecher;

    @Value("${hddolby.giveupSeeder}")
    private Integer hddolbyGiveupSeeder;

    @Value("${hddolby.tag}")
    private String hddolbyTag;

    @Value("${hddolby.maxDefendSize}")
    private Integer hddolbyMaxDefendSize;


    @Autowired
    private QbittorrentUtil qbittorrentUtil;


    private AtomicBoolean continueFlag = new AtomicBoolean(true);

    private Integer pageNum;


    public void dynamicsDefendDolbyTorrent() {
        continueFlag.set(true);
        pageNum = 0;
        String sid = qbittorrentUtil.login();
        JSONArray torrentArray = qbittorrentUtil.getTorrentList(sid, hddolbyTag);
        BigDecimal currentDefendSize = BigDecimal.ZERO;
        BigDecimal maxDefendSize = new BigDecimal(hddolbyMaxDefendSize).multiply(new BigDecimal("1073741824"));
        List<String> needDeleteTorrents = new ArrayList<>();
        for (int i = 0; i < torrentArray.size(); i++) {
            JSONObject torrentJson = torrentArray.getJSONObject(i);
            Integer numComplete = torrentJson.getInteger("num_complete");
            //保种人数过多 可以放弃保种
            if (numComplete > hddolbyGiveupSeeder) {
                String hash = torrentJson.getString("hash");
                needDeleteTorrents.add(hash);
            }
            //计算当前保种大小
            else {
                BigDecimal size = torrentJson.getBigDecimal("size");
                currentDefendSize = currentDefendSize.add(size);
            }
        }
        //需要保种大小
        BigDecimal needDefendSize = maxDefendSize.subtract(currentDefendSize);

        do {
            HttpResponse response = HttpUtil.createGet(hddolbyUrl + pageNum).header("cookie", hddolbyCookie).header("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36").execute();
            if (response.getStatus() == 200) {
                String html = response.body();
                log.info("请求返回: {}", html);
                List<String> torUrlList = parseHtml(html, needDefendSize);
                for (String torUrl : torUrlList) {
                    try {
                        qbittorrentUtil.addTorrents(torUrl, sid, hddolbyTag);
                    } catch (Exception e) {
                        log.error("添加种子失败: {}", e);
                    }
                    int rdm = new Random().nextInt(500);
                    try {
                        Thread.sleep(500 + rdm);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            pageNum++;
        } while (continueFlag.get());

        //删种
        for (String needDeleteTorrent : needDeleteTorrents) {
            qbittorrentUtil.deleteTorrent(needDeleteTorrent, sid);
        }

        log.info("----动态保种完成----");
    }


    public List<String> parseHtml(String html, BigDecimal needDefendSize) {
        Document parse = Jsoup.parse(html, "utf-8");
        List<String> list = new ArrayList<>();
        Element elementById = parse.getElementById("torrenttable");
        Elements elementsByClass = elementById.getElementsByTag("tr");

        for (Element element : elementsByClass) {
            if (needDefendSize.compareTo(BigDecimal.ZERO) <= 0) {
                continueFlag.set(false);
            }
            Elements tdEls = element.getElementsByTag("td");
            if (tdEls.size() != 14) {
                continue;
            }
            String torrentUrl = "https://www.hddolby.com/" + tdEls.get(5).getElementsByTag("a").get(0).attr("href") + "&downhash=" + hddolbyDownhash;
            String createTimeStr = tdEls.get(7).getElementsByTag("span").get(0).attr("title");
            String torrentSizeStr = tdEls.get(8).text();
            //做种数
            int seeder = Integer.parseInt(tdEls.get(9).text());
            //下载数
            int leecher = Integer.parseInt(tdEls.get(10).text());
            String process = tdEls.get(12).text();
            //正在做种 正在下载
            if (process.endsWith("Seeding") || process.endsWith("Leeching")) {
                continue;
            }
            //未下载
            //发布时间小于[publishDay]天，做种人数小于等于[maxSeeder]人，下载人数小于等于[maxLeecher]人的种子才进项下载
            if ((DateUtils.getNowDate().getTime() - DateUtils.parseDate(createTimeStr).getTime()) > (1000 * 24 * 3600 * hddolbyPublishDay) && seeder <= hddolbyMaxSeeder && leecher <= hddolbyMaxLeecher && seeder > 0) {
                BigDecimal torrentSize = convertKB(torrentSizeStr);
                needDefendSize = needDefendSize.subtract(torrentSize);
                list.add(torrentUrl);
            }
        }
        return list;
    }

    /**
     * 单位转化
     *
     * @param torrentSizeStr
     * @return
     */
    public BigDecimal convertKB(String torrentSizeStr) {
        String[] split = torrentSizeStr.split(" ");
        BigDecimal torrentSize = new BigDecimal(split[0]);
        String unit = split[1];
        if ("TB".equalsIgnoreCase(unit)) {
            torrentSize = torrentSize.multiply(new BigDecimal("1099511627776"));
        } else if ("GB".equalsIgnoreCase(unit)) {
            torrentSize = torrentSize.multiply(new BigDecimal("1073741824"));
        } else if ("MB".equalsIgnoreCase(unit)) {
            torrentSize = torrentSize.multiply(new BigDecimal("1048576"));
        }
        return torrentSize;
    }

    @Override
    protected void executeInternal(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        dynamicsDefendDolbyTorrent();
    }
}
