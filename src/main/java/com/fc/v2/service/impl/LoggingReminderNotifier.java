package com.fc.v2.service.impl;

import com.fc.v2.model.auto.TAdrCycleTask;
import com.fc.v2.service.ReminderNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 催送通知缺省走法：站内消息与短信息都只记一笔日志，回当下时刻当作已送达。
 *
 * <p>真接通道时换一个 {@link ReminderNotifier} 实现挂上即可；服务层只认
 * 「头一条回没回送达刻」，不关心底下走哪段线。</p>
 *
 * @author fuce
 * @date 2026-09-30
 */
@Component
public class LoggingReminderNotifier implements ReminderNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingReminderNotifier.class);

    @Override
    public Date sendInsite(TAdrCycleTask task) {
        Date at = new Date();
        log.info("[催送·站内消息] 单号={} 单位={} 事由类={} 送达值守人 @{}",
                task.getItemNo(), task.getSiteNo(), task.getCategory(), at);
        return at;
    }

    @Override
    public Date sendSms(TAdrCycleTask task) {
        Date at = new Date();
        log.info("[催送·短信息] 单号={} 单位={} 联系人={} 已发出 @{}",
                task.getItemNo(), task.getSiteNo(), task.getContact(), at);
        return at;
    }
}
