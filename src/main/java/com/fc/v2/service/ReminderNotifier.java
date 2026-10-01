package com.fc.v2.service;

import com.fc.v2.model.auto.TAdrCycleTask;

import java.util.Date;

/**
 * 催送通知网关：两条道各走各的、各记各的送达。
 *
 * <p>一条到值守人的站内消息，一条发短信息；头一条没落（返回 {@code null}）
 * 不许拿第二条顶数——服务层据此判这一桩没催成。</p>
 *
 * @author fuce
 * @date 2026-09-30
 */
public interface ReminderNotifier {

    /**
     * 头一条：值守人站内消息。
     *
     * @return 实际送达时刻；{@code null} 表示未送达
     */
    Date sendInsite(TAdrCycleTask task);

    /**
     * 第二条：短信息。
     *
     * @return 实际送达时刻；{@code null} 表示未送达
     */
    Date sendSms(TAdrCycleTask task);
}
