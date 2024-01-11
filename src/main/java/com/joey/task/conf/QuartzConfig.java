package com.joey.task.conf;

import com.joey.task.task.HddolbyJob;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QuartzConfig {

    @Value("${quartz.cron}")
    private String cron; // corn表达式

    @Bean
    public JobDetail dynamicsDefendDolbyTorrent() {
        return JobBuilder.newJob(HddolbyJob.class).withIdentity("HddolbyJob").storeDurably().build();
    }

    @Bean
    public Trigger restartTrigger() {
        CronScheduleBuilder scheduleBuilder = CronScheduleBuilder.cronSchedule(cron);
        return TriggerBuilder.newTrigger().forJob(dynamicsDefendDolbyTorrent()).withIdentity("HddolbyJob").withSchedule(scheduleBuilder).build();
    }

}
