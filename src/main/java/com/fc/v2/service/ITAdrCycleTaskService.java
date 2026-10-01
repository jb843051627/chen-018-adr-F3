package com.fc.v2.service;

import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TAdrCycleTask;
import com.fc.v2.model.custom.adr.CycleRoundVo;
import com.fc.v2.service.impl.DutyCalendar;

/**
 * 定期报告催送单 Service接口（scheduling-job 形状：周期执行，无增删改查入口）
 *
 * <p>旧两味 {@link #listDue(Date)} / {@link #runOnce(Date)} 出入参与回话照旧不动；
 * 今年这一轮由系统照日子跑，另起同名方法挂上：
 * {@link #listDue(Date, DutyCalendar)} 与 {@link #runOnce(Date, DutyCalendar, ReminderNotifier)}，
 * 翻单与回写共用同一把到期尺。</p>
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITAdrCycleTaskService {

    /** 按主键回查条目 */
    TAdrCycleTask selectTAdrCycleTaskById(Long id);

    /** 该时刻可处理的条目（旧走法，回话照旧） */
    List<TAdrCycleTask> listDue(Date at);

    /** 执行一次，返回成功条数（旧走法，回话照旧）；单条失败跳过继续 */
    int runOnce(Date at);

    /**
     * 新走法·翻单：出手那一日已到（开口刻＝止点−提前日数，含止点）且仍是候催的条目，
     * 归进本轮；翻单与回写同一把尺。界外是否压住在 {@link #runOnce} 里按值守班段再分。
     */
    List<TAdrCycleTask> listDue(Date at, DutyCalendar calendar);

    /**
     * 新走法·回写：服务器这一回执行里算定本轮该催哪几桩，再落库。
     * 一趟清点交出候催/已催出/催不成三条数与逐条名单；空跑回「本轮无到期条目」，不算跑砸。
     */
    CycleRoundVo runOnce(Date at, DutyCalendar calendar, ReminderNotifier notifier);

    /** 定时任务入口：以当下时刻、容器里挂着的班次日历与通知网关跑一轮。 */
    CycleRoundVo runRound();
}
