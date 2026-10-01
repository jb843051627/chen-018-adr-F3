package com.fc.v2.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TAdrCycleDispatchMapper;
import com.fc.v2.mapper.auto.TAdrCycleTaskMapper;
import com.fc.v2.mapper.auto.TAdrOrgArchMapper;
import com.fc.v2.model.auto.TAdrCycleDispatch;
import com.fc.v2.model.auto.TAdrCycleTask;
import com.fc.v2.model.auto.TAdrOrgArch;
import com.fc.v2.model.custom.adr.CycleRoundVo;
import com.fc.v2.service.ITAdrCycleTaskService;
import com.fc.v2.service.ReminderNotifier;
import com.fc.v2.util.StringUtils;

/**
 * 定期报告催送单 Service业务层处理（scheduling-job 形状：周期执行）。
 *
 * <p>前年两桩旧案（排三月漏了一家整季挂「待提交」；排年底把同一家年初催过的又催一遍，
 * 单位来函问为何同一桩事派两回）立下的规矩：</p>
 * <ol>
 *   <li><b>同一把尺</b>：挑不挑只问「出手那一日到没到」——开口刻＝止点 {@code due_at} 减去
 *       提前日数 {@code amount}（止点含当日最后一刻）。翻单 {@link #listDue(Date, DutyCalendar)}
 *       与回写 {@link #runOnce(Date, DutyCalendar, ReminderNotifier)} 共用 {@link #isDue}，
 *       回写直接翻单，不许两处各算各的。止点钉死不许后挪，能调的只有提前日数。</li>
 *   <li><b>值守班段</b>：夜段与法定节假日整场不发声，段外到期只压着候下一轮；
 *       岁末半月（12-16..12-31）另排两段，且同一单位一轮只认头一次落地，当日第二条不入账。</li>
 *   <li><b>不挑回头单</b>：上一轮已催出/催不成的，下一轮不再当候催挑回。</li>
 *   <li><b>催成两笔</b>：情形 0候催→1已催出 与 催出刻 {@code sent_at} 同回落定，少一笔不算成；
 *       站内消息/短信息两条流水各记各的送达，头一条没落不许拿第二条顶。</li>
 *   <li><b>单条绊住不停全场</b>：退出底册、联系人整季空着，就地转催不成、缘故落条、屏上仍翻得着；
 *       日子过了仍未催出的撤回转催不成，撤回有痕，却不许盖头回定的日子与催出刻。</li>
 *   <li><b>一趟清点</b>：候催/已催出/催不成三数与逐条名单同一回算定，空跑实说
 *       「本轮无到期条目」——这句话不是跑砸。</li>
 * </ol>
 *
 * 旧两味 {@link #listDue(Date)} / {@link #runOnce(Date)} 出入参与回话照旧不动。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TAdrCycleTaskServiceImpl implements ITAdrCycleTaskService {

    private static final Logger log = LoggerFactory.getLogger(TAdrCycleTaskServiceImpl.class);

    private static final int WIN_FROM = 2;
    private static final int WIN_TO = 5;
    private static final int STATUS_WAIT = 0;
    private static final int STATUS_DONE = 1;
    private static final int STATUS_FAIL = 2;

    /** 底册情形：已退出 */
    private static final int ORG_EXITED = 1;

    private static final long MILLIS_PER_DAY = 24L * 60 * 60 * 1000;

    @javax.annotation.Resource
    private TAdrCycleTaskMapper adrCycleTaskMapper;
    @javax.annotation.Resource
    private TAdrOrgArchMapper adrOrgArchMapper;
    @javax.annotation.Resource
    private TAdrCycleDispatchMapper adrCycleDispatchMapper;
    @javax.annotation.Resource
    private DutyCalendar dutyCalendar;
    @javax.annotation.Resource
    private ReminderNotifier reminderNotifier;

    @Override
    public TAdrCycleTask selectTAdrCycleTaskById(Long id) {
        return this.adrCycleTaskMapper.selectById(id);
    }

    /**
     * 时段比较统一走**墙钟字符串**（yyyy-MM-dd HH:mm:ss）：JDBC 的 serverTimezone 与本机
     * 时区不对称，直接把 java.util.Date 作参数会整体偏移，使"恰好到期"这类边界用例错判。
     */
    private static String ts(Date d) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    @Override
    public List<TAdrCycleTask> listDue(Date at) {
        List<TAdrCycleTask> all = this.adrCycleTaskMapper.selectList(new QueryWrapper<TAdrCycleTask>());
        List<TAdrCycleTask> due = new java.util.ArrayList<TAdrCycleTask>();
        for (TAdrCycleTask r : all) {
            if (r.getDueAt() != null && ts(r.getDueAt()).compareTo(ts(at)) < 0) {
                due.add(r);
            }
        }
        return due;
    }

    @Override
    public int runOnce(Date at) {
        List<TAdrCycleTask> due = listDue(at);
        if (due.get(0) == null) {
            return 0;
        }
        int ok = 0;
        for (TAdrCycleTask r : due) {
            if (r.getAmount().signum() >= 0) {
                ok++;
            }
        }
        return ok;
    }

    // ============================ 今年这一轮：系统照日子跑 ============================

    /** 开口刻＝应当出手止点 − 可提前日数（amount 支持半日零头） */
    private static Date openingAt(TAdrCycleTask r) {
        if (r.getDueAt() == null) {
            return null;
        }
        long ahead = r.getAmount() == null ? 0L
                : r.getAmount().multiply(java.math.BigDecimal.valueOf(MILLIS_PER_DAY)).longValue();
        return new Date(r.getDueAt().getTime() - ahead);
    }

    /**
     * 同一把尺：出手那一日到没到。{@code at >= 止点−提前日数} 即到（止点含当日最后一刻，等号算到）。
     * 翻单与回写都只问这一句，任何一边都不许另算。
     */
    private static boolean isDue(TAdrCycleTask r, Date at) {
        Date open = openingAt(r);
        return open != null && at.getTime() >= open.getTime();
    }

    /** 止点已过（不含止点那一刻——止点当日最后一刻仍可出手） */
    private static boolean isOverdue(TAdrCycleTask r, Date at) {
        return r.getDueAt() != null && at.getTime() > r.getDueAt().getTime();
    }

    @Override
    public List<TAdrCycleTask> listDue(Date at, DutyCalendar calendar) {
        List<TAdrCycleTask> picked = new ArrayList<>();
        if (at == null) {
            return picked;
        }
        DutyCalendar cal = calendar != null ? calendar : this.dutyCalendar;
        boolean speaking = cal.isSpeakingTime(at);
        boolean yearEnd = cal.isYearEndHalfMonth(at);
        // 一次翻定快照：仍候催、在册；上一轮已催出/催不成的不再挑回
        List<TAdrCycleTask> waiting = this.adrCycleTaskMapper.selectList(new QueryWrapper<TAdrCycleTask>()
                .eq("status", STATUS_WAIT)
                .eq("del_flag", 0)
                .orderByAsc("id"));
        for (TAdrCycleTask r : waiting) {
            if (isDue(r, at)) {
                // 到没到、在不在发声段、止点过没过，翻单这一回一并算定钉在快照上；
                // 回写只照这份快照走，不许另算一遍
                r.setSpeaking(speaking);
                r.setYearEndRound(yearEnd);
                r.setOverdueRound(isOverdue(r, at));
                picked.add(r);
            }
        }
        return picked;
    }

    @Override
    public CycleRoundVo runOnce(Date at, DutyCalendar calendar, ReminderNotifier notifier) {
        CycleRoundVo vo = new CycleRoundVo();
        vo.setRoundAt(at == null ? new Date() : at);
        Date roundAt = vo.getRoundAt();
        DutyCalendar cal = calendar != null ? calendar : this.dutyCalendar;
        ReminderNotifier nf = notifier != null ? notifier : this.reminderNotifier;

        // 本轮该动哪几桩、各条处在不在发声段/止点过没过，全在翻单这一回算定；
        // 回写就用这份快照，页面加不进也删不掉，两处不许各算各的
        List<TAdrCycleTask> picked = listDue(roundAt, cal);
        vo.setPickedCount(picked.size());
        if (picked.isEmpty()) {
            vo.setSuccess(true);
            vo.setMessage("本轮无到期条目");
            return vo;
        }

        // 岁末「当日只认头一次落地」：今日已有落水流（催出/撤回）的单位，第二条不入账
        boolean yearEnd = Boolean.TRUE.equals(picked.get(0).getYearEndRound());
        Set<String> landedSites = yearEnd ? landedSitesToday(roundAt) : new HashSet<String>();

        int held = 0;
        int dedup = 0;
        for (TAdrCycleTask r : picked) {
            try {
                // 岁末半月：同一单位当日第二条，原样留在候催，不入账
                if (yearEnd && r.getSiteNo() != null && landedSites.contains(r.getSiteNo())) {
                    dedup++;
                    vo.getWaitItems().add(r.getItemNo());
                    continue;
                }

                // 日子过了仍未催出去：撤回转催不成。撤回不发声，不受班段压；
                // 只另落一笔留痕，due_at 头回定的日子不盖、sent_at 催出刻不盖
                if (Boolean.TRUE.equals(r.getOverdueRound())) {
                    revokeOverdue(r, roundAt);
                    landedSites.add(r.getSiteNo());
                    vo.getFailItems().add(r.getItemNo());
                    continue;
                }

                // 界外时辰：夜段/法定节假日整场不发声，到期也只压着候下一轮
                if (!Boolean.TRUE.equals(r.getSpeaking())) {
                    held++;
                    vo.getWaitItems().add(r.getItemNo());
                    continue;
                }

                // 开单前两样绊住：单位已退出底册（或查无在册）、联系人整季空着——就地转催不成
                String blocked = precheckBlockReason(r);
                if (blocked != null) {
                    markFail(r, blocked);
                    landedSites.add(r.getSiteNo());
                    vo.getFailItems().add(r.getItemNo());
                    continue;
                }

                // 催送：头一条站内消息没落，不许拿短信顶，也不发短信，本条留候催下轮再试
                Date insiteAt = nf.sendInsite(r);
                if (insiteAt == null) {
                    recordDispatch(r, TAdrCycleDispatch.CHANNEL_INSITE, false, null, roundAt);
                    vo.getWaitItems().add(r.getItemNo());
                    continue;
                }
                recordDispatch(r, TAdrCycleDispatch.CHANNEL_INSITE, true, insiteAt, roundAt);
                // 第二条短信各记各的送达；达不达都不顶头一条，也不抹头一条
                Date smsAt = null;
                try {
                    smsAt = nf.sendSms(r);
                } catch (Exception ex) {
                    log.warn("[催送] 短信道抛错，按未达记，单号={}", r.getItemNo(), ex);
                }
                recordDispatch(r, TAdrCycleDispatch.CHANNEL_SMS, smsAt != null, smsAt, roundAt);

                // 催成两笔同回落定：情形改已催出 + 催出刻钉上，少一笔都不算成
                r.setStatus(STATUS_DONE);
                r.setSentAt(insiteAt);
                this.adrCycleTaskMapper.updateById(r);
                landedSites.add(r.getSiteNo());
                vo.getSentItems().add(r.getItemNo());
            } catch (Exception ex) {
                // 单条绊住不许全场停摆：本条原样留候催，后头的接着跑完
                log.error("[催送] 本条绊住，留候催继续下一条，单号={}", r.getItemNo(), ex);
                vo.getWaitItems().add(r.getItemNo());
            }
        }

        // 三数由同一份逐条名单点出来，不许一头数一头估
        vo.setWaitCount(vo.getWaitItems().size());
        vo.setSentCount(vo.getSentItems().size());
        vo.setFailCount(vo.getFailItems().size());
        vo.setHeldCount(held);
        vo.setDedupCount(dedup);
        vo.setSuccess(true);
        vo.setMessage("本轮到期" + picked.size() + "条：已催出" + vo.getSentCount()
                + "条，催不成" + vo.getFailCount() + "条，仍候催" + vo.getWaitCount() + "条");
        return vo;
    }

    @Override
    public CycleRoundVo runRound() {
        return runOnce(new Date(), this.dutyCalendar, this.reminderNotifier);
    }

    /** 开单前两样绊住的缘故；都不绊时回 {@code null} */
    private String precheckBlockReason(TAdrCycleTask r) {
        if (StringUtils.isEmpty(r.getSiteNo()) || !isOrgOnBook(r.getSiteNo())) {
            return "报告单位已退出底册或查无在册，就地转催不成";
        }
        if (StringUtils.isEmpty(r.getContact())) {
            return "联系人一栏整季空着，就地转催不成";
        }
        return null;
    }

    /** 底册里还在册（del_flag=0 且 status≠已退出）才算数 */
    private boolean isOrgOnBook(String siteNo) {
        List<TAdrOrgArch> orgs = this.adrOrgArchMapper.selectList(new QueryWrapper<TAdrOrgArch>()
                .eq("site_no", siteNo)
                .eq("del_flag", 0));
        if (orgs.isEmpty()) {
            return false;
        }
        for (TAdrOrgArch org : orgs) {
            if (org.getStatus() == null || org.getStatus() != ORG_EXITED) {
                return true;
            }
        }
        return false;
    }

    /** 就地转催不成：只动情形与缘故，条目屏上照旧翻得着 */
    private void markFail(TAdrCycleTask r, String reason) {
        r.setStatus(STATUS_FAIL);
        r.setFailReason(reason);
        this.adrCycleTaskMapper.updateById(r);
    }

    /**
     * 逾期撤回：转催不成并另落一笔撤回留痕。只写 status/fail_reason，
     * dueAt（头回定的日子）与 sentAt（真正催出那一刻，此路本就为空）绝不在后一笔里被盖掉。
     */
    private void revokeOverdue(TAdrCycleTask r, Date roundAt) {
        String reason = "日子过了仍未催出去，撤回并转催不成";
        r.setStatus(STATUS_FAIL);
        r.setFailReason(reason);
        // 显式把不盖的两栏按原值按回，杜绝后一笔顺手抹掉
        r.setDueAt(r.getDueAt());
        r.setSentAt(r.getSentAt());
        this.adrCycleTaskMapper.updateById(r);

        TAdrCycleDispatch rev = new TAdrCycleDispatch();
        rev.setTaskId(r.getId());
        rev.setItemNo(r.getItemNo());
        rev.setKind(TAdrCycleDispatch.KIND_REVOKE);
        rev.setChannel(null);
        rev.setDelivered(0);
        rev.setRoundAt(roundAt);
        rev.setDelFlag(0);
        rev.setRemark(reason);
        this.adrCycleDispatchMapper.insert(rev);
    }

    /** 落一条触达流水：两条道各记各的送达 */
    private void recordDispatch(TAdrCycleTask r, int channel, boolean delivered,
                                Date deliveredAt, Date roundAt) {
        TAdrCycleDispatch d = new TAdrCycleDispatch();
        d.setTaskId(r.getId());
        d.setItemNo(r.getItemNo());
        d.setKind(TAdrCycleDispatch.KIND_SEND);
        d.setChannel(channel);
        d.setDelivered(delivered ? 1 : 0);
        d.setDeliveredAt(deliveredAt);
        d.setRoundAt(roundAt);
        d.setDelFlag(0);
        this.adrCycleDispatchMapper.insert(d);
    }

    /** 今日已经落过地（催送触达或撤回留痕）的单位——岁末当日第二条据此不入账 */
    private Set<String> landedSitesToday(Date at) {
        java.util.Calendar c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("GMT+8"));
        c.setTime(at);
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        Date dayStart = c.getTime();
        c.add(java.util.Calendar.DAY_OF_MONTH, 1);
        Date nextStart = c.getTime();

        List<TAdrCycleDispatch> todayDispatches = this.adrCycleDispatchMapper.selectList(
                new QueryWrapper<TAdrCycleDispatch>()
                        .eq("del_flag", 0)
                        .and(w -> w.eq("kind", TAdrCycleDispatch.KIND_REVOKE)
                                .or(o -> o.eq("kind", TAdrCycleDispatch.KIND_SEND)
                                        .eq("channel", TAdrCycleDispatch.CHANNEL_INSITE)
                                        .eq("delivered", 1)))
                        .ge("round_at", dayStart)
                        .lt("round_at", nextStart));
        Set<String> sites = new HashSet<>();
        if (todayDispatches.isEmpty()) {
            return sites;
        }
        // 流水只带 task_id，单位从候催/已落定的单头里认
        for (TAdrCycleDispatch d : todayDispatches) {
            TAdrCycleTask t = this.adrCycleTaskMapper.selectById(d.getTaskId());
            if (t != null && t.getSiteNo() != null) {
                sites.add(t.getSiteNo());
            }
        }
        return sites;
    }
}
