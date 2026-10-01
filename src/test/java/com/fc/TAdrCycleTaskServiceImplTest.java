package com.fc;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TAdrCycleDispatch;
import com.fc.v2.model.auto.TAdrCycleTask;
import com.fc.v2.model.custom.adr.CycleRoundVo;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 定期报告催送一轮跑批集成测试——前年两桩旧案（排三月漏一家整季挂「待提交」；
 * 排年底把同一家年初催过的又催一遍，单位来函问为何派两回）立下的规矩：
 * 同一把到期尺、夜段节假日压住、岁末同单位当日只认头一条、催成两笔、
 * 两道送达不顶数、单条绊住不停全场、逾期撤回留痕不盖、三数一趟点清、空跑不算跑砸。
 */
class TAdrCycleTaskServiceImplTest extends CycleTaskTestBase {

    private TAdrCycleTask reload(String itemNo) {
        return taskMapper.selectOne(new QueryWrapper<TAdrCycleTask>().eq("item_no", itemNo));
    }

    private List<TAdrCycleDispatch> dispatches(String itemNo, int kind) {
        return dispatchMapper.selectList(new QueryWrapper<TAdrCycleDispatch>()
                .eq("item_no", itemNo).eq("kind", kind).orderByAsc("id"));
    }

    // ---------------- 同一把尺：止点含当日最后一刻、提前量可调 ----------------

    @Test
    void 止点那一刻算到_早一秒不算到_两处同一把尺() {
        seedOrg("YF00", 0);
        seedTask("T-EQ", "YF00", "2026-06-01 23:59:59", "0", "老王 13800000000");
        // 止点前一秒：还没到（哪怕已入夜，压根不进本轮）
        CycleRoundVo before = service.runOnce(t("2026-06-01 23:59:58"), calendar, notifier);
        assertEquals(0, before.getPickedCount());
        assertEquals("本轮无到期条目", before.getMessage());
        // 恰好止点：到了（含当日最后一刻），归进本轮；只是夜段不发声，压成候催
        CycleRoundVo at = service.runOnce(t("2026-06-01 23:59:59"), calendar, notifier);
        assertEquals(1, at.getPickedCount(), "等号算到");
        assertEquals(1, at.getWaitCount());
        assertEquals(1, at.getHeldCount());
        assertEquals(0, reload("T-EQ").getStatus().intValue(), "夜段压着，原样候催");
        // 翻单那把尺与回写一致：listDue 在止点前空、止点有
        assertTrue(service.listDue(t("2026-06-01 23:59:58"), calendar).isEmpty());
        assertEquals(1, service.listDue(t("2026-06-01 23:59:59"), calendar).size());
    }

    @Test
    void 能调的只有提前几日_提前到了白班就催出() {
        seedOrg("YF00", 0);
        // 止点 06-03 夜里，提前 3 日开口：05-31 夜即在开口刻之后，06-01 上午已到
        seedTask("T-AHEAD", "YF00", "2026-06-03 23:59:59", "3", "老王");
        CycleRoundVo vo = service.runOnce(t("2026-06-01 10:00:00"), calendar, notifier);
        assertEquals(1, vo.getSentCount());
        assertEquals(1, reload("T-AHEAD").getStatus().intValue());
    }

    // ---------------- 夜段与法定节假日整场不发声 ----------------

    @Test
    void 夜段到期只压着_次日白班头一轮催出() {
        seedOrg("YF00", 0);
        seedTask("T-NIGHT", "YF00", "2026-06-10 23:59:59", "1", "老王");
        // 20:00 整已出白班（白班 [08:00,20:00)），压
        CycleRoundVo night = service.runOnce(t("2026-06-10 20:00:00"), calendar, notifier);
        assertEquals(1, night.getHeldCount());
        assertEquals(0, reload("T-NIGHT").getStatus().intValue());
        assertTrue(dispatches("T-NIGHT", TAdrCycleDispatch.KIND_SEND).isEmpty(), "压着不发通知");
        // 次日 08:00 整进入白班（含 08:00），催出（止点 06-10 已过？提前1日开口=06-09 23:59:59，未逾期）
        // 止点为 06-10 23:59:59，06-10 08:00 尚未过止点
        CycleRoundVo morning = service.runOnce(t("2026-06-10 08:00:00"), calendar, notifier);
        assertEquals(1, morning.getSentCount());
    }

    @Test
    void 法定节假日全天压_节后白班催出() {
        seedOrg("YF00", 0);
        seedHoliday("2026-06-01");
        // 止点 06-03，提前 3 日，05-31 夜开口，06-01（节假日）已到期但整场压
        seedTask("T-HOL", "YF00", "2026-06-03 23:59:59", "3", "老王");
        CycleRoundVo holiday = service.runOnce(t("2026-06-01 10:00:00"), calendar, notifier);
        assertEquals(1, holiday.getHeldCount(), "节假日白班也整场不发声");
        // 06-02 平日白班，未过止点，催出
        CycleRoundVo next = service.runOnce(t("2026-06-02 10:00:00"), calendar, notifier);
        assertEquals(1, next.getSentCount());
    }

    // ---------------- 岁末半月另排两段、同单位当日只认头一条 ----------------

    @Test
    void 岁末另排两段_段边界() {
        seedOrg("YF00", 0);
        // 止点 12-31 夜，提前 12 日：12-19 夜开口，12-20 全天皆在开口后、止点前
        seedTask("T-YE1", "YF00", "2026-12-31 23:59:59", "12", "老王");
        assertEquals(0, service.runOnce(t("2026-12-20 08:29:00"), calendar, notifier).getSentCount(),
                "08:30 前不发声");
        assertEquals(0, service.runOnce(t("2026-12-20 11:30:00"), calendar, notifier).getSentCount(),
                "上午段 [08:30,11:30)，11:30 已出场");
        assertEquals(1, service.runOnce(t("2026-12-20 08:30:00"), calendar, notifier).getSentCount(),
                "08:30 整发声");
    }

    @Test
    void 岁末同单位同刻两条_头一条催出第二条不入账_不重不漏() {
        seedOrg("S1", 0);
        seedOrg("S2", 0);
        seedTask("Y-A", "S1", "2026-12-31 23:59:59", "12", "甲");
        seedTask("Y-B", "S1", "2026-12-31 23:59:59", "12", "甲");
        seedTask("Y-C", "S2", "2026-12-31 23:59:59", "12", "乙");
        CycleRoundVo vo = service.runOnce(t("2026-12-20 09:00:00"), calendar, notifier);
        assertEquals(3, vo.getPickedCount(), "同刻并进来三条，一条不少挑");
        assertEquals(2, vo.getSentCount(), "两个单位各落头一条");
        assertEquals(1, vo.getWaitCount());
        assertEquals(1, vo.getDedupCount(), "S1 当日第二条不入账");
        assertEquals(0, vo.getFailCount());
        // 不许多数也不许少数：三类合计正好等于挑进数
        assertEquals(vo.getPickedCount(), vo.getWaitCount() + vo.getSentCount() + vo.getFailCount());
        assertEquals(1, reload("Y-A").getStatus().intValue(), "先落下的 id 小，催出");
        assertEquals(0, reload("Y-B").getStatus().intValue(), "第二条原样留候催");
        // 次日这第二条才落地
        CycleRoundVo next = service.runOnce(t("2026-12-21 09:00:00"), calendar, notifier);
        assertEquals(1, next.getSentCount());
        assertEquals(1, reload("Y-B").getStatus().intValue());
    }

    @Test
    void 岁末当日已催过_同日新一轮不挑第二回() {
        seedOrg("S1", 0);
        seedTask("Y-1", "S1", "2026-12-31 23:59:59", "12", "甲");
        service.runOnce(t("2026-12-20 09:00:00"), calendar, notifier);
        // 同日又开一张同单位的单，下一轮压着不入账
        seedTask("Y-2", "S1", "2026-12-31 23:59:59", "12", "甲");
        CycleRoundVo vo = service.runOnce(t("2026-12-20 15:00:00"), calendar, notifier);
        assertEquals(1, vo.getDedupCount());
        assertEquals(0, reload("Y-2").getStatus().intValue());
    }

    // ---------------- 上轮已催出的，下轮不许挑回 ----------------

    @Test
    void 催过一回头_下轮不再当候催挑回() {
        seedOrg("YF00", 0);
        seedTask("T-ONCE", "YF00", "2026-06-03 23:59:59", "3", "老王");
        CycleRoundVo first = service.runOnce(t("2026-06-01 10:00:00"), calendar, notifier);
        assertEquals(1, first.getSentCount());
        CycleRoundVo second = service.runOnce(t("2026-06-01 15:00:00"), calendar, notifier);
        assertEquals(0, second.getPickedCount(), "已催出的不再挑回——同一桩事不派两回");
        assertEquals("本轮无到期条目", second.getMessage());
        assertEquals(2, dispatches("T-ONCE", TAdrCycleDispatch.KIND_SEND).size(),
                "触达流水仍只有头一轮那两条，不重发");
    }

    // ---------------- 催成两笔 + 两道各记送达 ----------------

    @Test
    void 催成落两笔_情形改已催出且催出刻钉上_两道各一条() {
        seedOrg("YF00", 0);
        seedTask("T-DONE", "YF00", "2026-06-03 23:59:59", "3", "老王");
        CycleRoundVo vo = service.runOnce(t("2026-06-01 10:00:00"), calendar, notifier);
        TAdrCycleTask done = reload("T-DONE");
        assertEquals(1, done.getStatus().intValue());
        assertNotNull(done.getSentAt(), "催出那一刻必须钉上，少一笔不算成");
        assertEquals(t("2026-06-01 10:00:00"), done.getSentAt());

        List<TAdrCycleDispatch> ds = dispatches("T-DONE", TAdrCycleDispatch.KIND_SEND);
        assertEquals(2, ds.size(), "站内、短信两条各一条");
        assertEquals(TAdrCycleDispatch.CHANNEL_INSITE, ds.get(0).getChannel().intValue());
        assertEquals(1, ds.get(0).getDelivered().intValue());
        assertEquals(TAdrCycleDispatch.CHANNEL_SMS, ds.get(1).getChannel().intValue());
        assertEquals(1, ds.get(1).getDelivered().intValue());
        // 呼叫次序：站内头一条在前
        assertEquals("INSITE:T-DONE", notifier.order.get(0));
    }

    @Test
    void 头一条站内没落_不许拿短信顶_本条留候催_下轮补催() {
        seedOrg("YF00", 0);
        seedTask("T-FIRST", "YF00", "2026-06-03 23:59:59", "3", "老王");
        notifier.insiteOk = false;
        CycleRoundVo vo = service.runOnce(t("2026-06-01 10:00:00"), calendar, notifier);
        assertEquals(0, vo.getSentCount());
        assertEquals(1, vo.getWaitCount(), "头道未达，留候催");
        assertEquals(0, reload("T-FIRST").getStatus().intValue());
        assertNull(reload("T-FIRST").getSentAt());
        List<TAdrCycleDispatch> ds = dispatches("T-FIRST", TAdrCycleDispatch.KIND_SEND);
        assertEquals(1, ds.size(), "只落了站内未达一条");
        assertEquals(0, ds.get(0).getDelivered().intValue());
        assertFalse(notifier.order.contains("SMS:T-FIRST"), "头一条没落，短信不许发、不许顶数");

        // 下一轮站内通了才催成
        notifier.insiteOk = true;
        CycleRoundVo retry = service.runOnce(t("2026-06-01 15:00:00"), calendar, notifier);
        assertEquals(1, retry.getSentCount());
        assertEquals(1, reload("T-FIRST").getStatus().intValue());
    }

    @Test
    void 第二条短信未达_不顶不抹头一条_仍算催成() {
        seedOrg("YF00", 0);
        seedTask("T-SMS", "YF00", "2026-06-03 23:59:59", "3", "老王");
        notifier.smsOk = false;
        CycleRoundVo vo = service.runOnce(t("2026-06-01 10:00:00"), calendar, notifier);
        assertEquals(1, vo.getSentCount(), "头一条站内已达即催成");
        List<TAdrCycleDispatch> ds = dispatches("T-SMS", TAdrCycleDispatch.KIND_SEND);
        assertEquals(2, ds.size(), "短信那条也照落，如实记未达");
        assertEquals(TAdrCycleDispatch.CHANNEL_SMS, ds.get(1).getChannel().intValue());
        assertEquals(0, ds.get(1).getDelivered().intValue(), "短信未达不顶不抹头一条");
        assertNotNull(reload("T-SMS").getSentAt());
    }

    // ---------------- 单条绊住：退出底册 / 联系人空，不停全场 ----------------

    @Test
    void 退出底册与联系人空_就地催不成缘故落条_屏上仍翻得着_后头照跑() {
        seedOrg("YF00", 0); // 在册
        seedOrg("YF01", 1); // 已退出
        seedTask("B-EXIT", "YF01", "2026-06-03 23:59:59", "3", "老钱");
        seedTask("B-NOCONTACT", "YF00", "2026-06-03 23:59:59", "3", "  ");
        seedTask("B-OK", "YF00", "2026-06-03 23:59:59", "3", "老王");

        CycleRoundVo vo = service.runOnce(t("2026-06-01 10:00:00"), calendar, notifier);
        assertEquals(2, vo.getFailCount());
        assertEquals(1, vo.getSentCount(), "绊住两条不掐断，第三条照催");
        TAdrCycleTask exited = reload("B-EXIT");
        assertEquals(2, exited.getStatus().intValue());
        assertTrue(exited.getFailReason().contains("退出底册"));
        assertEquals(0, exited.getDelFlag().intValue(), "屏上照旧翻得着");
        TAdrCycleTask noContact = reload("B-NOCONTACT");
        assertEquals(2, noContact.getStatus().intValue());
        assertTrue(noContact.getFailReason().contains("联系人"));
        assertEquals(1, reload("B-OK").getStatus().intValue());
        assertTrue(vo.getFailItems().contains("B-EXIT"));
        assertTrue(vo.getSentItems().contains("B-OK"));
    }

    // ---------------- 日子过了仍未催出：撤回留痕，不盖头回日子与催出刻 ----------------

    @Test
    void 逾期未催_夜里也撤回_转催不成留痕_头回日子不盖() {
        seedOrg("YF00", 0);
        Date due = t("2026-06-01 23:59:59");
        seedTask("B-LATE", "YF00", "2026-06-01 23:59:59", "0", "老王");
        // 止点已过，哪怕正值夜段，撤回不发声、不受班段压
        CycleRoundVo vo = service.runOnce(t("2026-06-02 21:00:00"), calendar, notifier);
        assertEquals(1, vo.getFailCount());
        TAdrCycleTask late = reload("B-LATE");
        assertEquals(2, late.getStatus().intValue());
        assertTrue(late.getFailReason().contains("撤回"));
        assertEquals(due, late.getDueAt(), "头回定的日子不许被后一笔盖掉");
        assertNull(late.getSentAt(), "本就没催出过，催出刻仍空，不许被盖");
        // 撤回留痕查得着
        List<TAdrCycleDispatch> revokes = dispatches("B-LATE", TAdrCycleDispatch.KIND_REVOKE);
        assertEquals(1, revokes.size());
        // 撤回不发通知
        assertTrue(dispatches("B-LATE", TAdrCycleDispatch.KIND_SEND).isEmpty());
    }

    // ---------------- 一趟清点：三数与逐条名单对得上 ----------------

    @Test
    void 三数与明细同一回点算_候催已催出催不成接得平() {
        seedOrg("S1", 0);
        seedOrg("S2", 1); // 退出
        seedTask("M-A", "S1", "2026-12-31 23:59:59", "12", "甲");
        seedTask("M-B", "S2", "2026-12-31 23:59:59", "12", "乙");
        seedTask("M-C", "S1", "2026-12-31 23:59:59", "12", "甲"); // S1 当日第二条
        CycleRoundVo vo = service.runOnce(t("2026-12-20 09:30:00"), calendar, notifier);
        assertEquals(3, vo.getPickedCount());
        assertEquals(1, vo.getWaitCount());
        assertEquals(1, vo.getSentCount());
        assertEquals(1, vo.getFailCount());
        assertEquals(vo.getPickedCount(), vo.getWaitCount() + vo.getSentCount() + vo.getFailCount());
        assertEquals(java.util.Arrays.asList("M-A"), vo.getSentItems());
        assertEquals(java.util.Arrays.asList("M-C"), vo.getWaitItems());
        assertEquals(java.util.Arrays.asList("M-B"), vo.getFailItems());
    }

    @Test
    void 本轮一个该动的也没有_实说无到期_且不算跑砸() {
        seedOrg("YF00", 0);
        seedTask("T-LATER", "YF00", "2026-12-31 23:59:59", "0", "老王");
        CycleRoundVo vo = service.runOnce(t("2026-06-01 10:00:00"), calendar, notifier);
        assertTrue(vo.isSuccess());
        assertEquals("本轮无到期条目", vo.getMessage());
        assertEquals(0, vo.getPickedCount());
        assertEquals(0, vo.getWaitCount() + vo.getSentCount() + vo.getFailCount());
    }

    // ---------------- 旧出入参回话照旧不动 ----------------

    @Test
    void 旧两味签名回话照旧() {
        seedTask("OLD-1", "YF00", "2026-06-01 08:00:00", "0", null);
        // 旧 listDue：止点严格早于该时刻即挑出（不看情形、不看班段）
        assertEquals(1, service.listDue(t("2026-06-01 09:00:00")).size());
        assertTrue(service.listDue(t("2026-06-01 08:00:00")).isEmpty());
        // 旧 runOnce 回话：amount≥0 的条数
        assertEquals(1, service.runOnce(t("2026-06-01 09:00:00")));
    }
}
