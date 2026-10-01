package com.fc;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fc.v2.mapper.auto.TAdrCycleDispatchMapper;
import com.fc.v2.mapper.auto.TAdrCycleTaskMapper;
import com.fc.v2.mapper.auto.TAdrDutyHolidayMapper;
import com.fc.v2.mapper.auto.TAdrOrgArchMapper;
import com.fc.v2.model.auto.TAdrCycleTask;
import com.fc.v2.model.auto.TAdrDutyHoliday;
import com.fc.v2.model.auto.TAdrOrgArch;
import com.fc.v2.service.ReminderNotifier;
import com.fc.v2.service.impl.DutyCalendar;
import com.fc.v2.service.impl.TAdrCycleTaskServiceImpl;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.BeforeEach;

import javax.sql.DataSource;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 定期报告催送单集成测试基台：H2(MODE=MySQL) + MyBatis-Plus，不起 Spring、不连 MySQL/Redis。
 * 值守班段按 GMT+8 切，造时刻也一律走 GMT+8 墙钟串，两边同一把尺。
 */
public abstract class CycleTaskTestBase {

    protected TAdrCycleTaskServiceImpl service;
    protected DutyCalendar calendar;
    protected TAdrCycleTaskMapper taskMapper;
    protected TAdrCycleDispatchMapper dispatchMapper;
    protected TAdrDutyHolidayMapper holidayMapper;
    protected TAdrOrgArchMapper orgMapper;
    protected FakeNotifier notifier;

    private final AtomicLong idSeq = new AtomicLong(5000);

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:cycle_" + getClass().getSimpleName() + "_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        ds.setUser("sa");

        try (Connection conn = ds.getConnection();
             Reader reader = new InputStreamReader(
                     getClass().getResourceAsStream("/schema-h2.sql"), StandardCharsets.UTF_8)) {
            RunScript.execute(conn, reader);
        }

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource((DataSource) ds);
        factoryBean.setConfiguration(configuration);
        SqlSessionFactory factory = factoryBean.getObject();
        factory.getConfiguration().addMapper(TAdrCycleTaskMapper.class);
        factory.getConfiguration().addMapper(TAdrCycleDispatchMapper.class);
        factory.getConfiguration().addMapper(TAdrDutyHolidayMapper.class);
        factory.getConfiguration().addMapper(TAdrOrgArchMapper.class);

        SqlSession session = factory.openSession(true);
        this.taskMapper = session.getMapper(TAdrCycleTaskMapper.class);
        this.dispatchMapper = session.getMapper(TAdrCycleDispatchMapper.class);
        this.holidayMapper = session.getMapper(TAdrDutyHolidayMapper.class);
        this.orgMapper = session.getMapper(TAdrOrgArchMapper.class);

        this.calendar = new DutyCalendar();
        inject(this.calendar, "dutyHolidayMapper", this.holidayMapper);

        this.notifier = new FakeNotifier();

        this.service = new TAdrCycleTaskServiceImpl();
        inject(this.service, "adrCycleTaskMapper", this.taskMapper);
        inject(this.service, "adrOrgArchMapper", this.orgMapper);
        inject(this.service, "adrCycleDispatchMapper", this.dispatchMapper);
        inject(this.service, "dutyCalendar", this.calendar);
        inject(this.service, "reminderNotifier", this.notifier);
    }

    protected static void inject(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    /** GMT+8 墙钟串转 Date */
    protected static Date t(String s) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            f.setTimeZone(TimeZone.getTimeZone("GMT+8"));
            return f.parse(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected long nextId() {
        return idSeq.incrementAndGet();
    }

    /** 落一家在册/退出底册单位 */
    protected void seedOrg(String siteNo, int status) {
        TAdrOrgArch org = new TAdrOrgArch();
        org.setId(nextId());
        org.setSiteNo(siteNo);
        org.setSiteName(siteNo + "底册");
        org.setStatus(status);
        org.setDelFlag(0);
        this.orgMapper.insert(org);
    }

    /** 落一个法定节假日（按当日认） */
    protected void seedHoliday(String day) {
        TAdrDutyHoliday h = new TAdrDutyHoliday();
        h.setId(nextId());
        h.setHolidayDate(t(day + " 00:00:00"));
        h.setHolidayName(day);
        h.setStatus(TAdrDutyHoliday.STATUS_ACTIVE);
        h.setDelFlag(0);
        this.holidayMapper.insert(h);
    }

    /**
     * 落一条候催单。amount 天提前量；due 为止点；site/contact 决定绊不绊。
     */
    protected TAdrCycleTask seedTask(String itemNo, String siteNo, String due, String amount,
                                     String contact) {
        TAdrCycleTask r = new TAdrCycleTask();
        r.setId(nextId());
        r.setItemNo(itemNo);
        r.setSiteNo(siteNo);
        r.setCategory("PSUR");
        r.setDueAt(t(due));
        r.setAmount(amount == null ? null : new BigDecimal(amount));
        r.setContent("照年度安全更新办法");
        r.setContact(contact);
        r.setStatus(0);
        r.setDelFlag(0);
        this.taskMapper.insert(r);
        return r;
    }

    /** 通知道假件：各道可否送达可配；呼叫次序与送达刻都记着 */
    protected static class FakeNotifier implements ReminderNotifier {
        boolean insiteOk = true;
        boolean smsOk = true;
        final List<String> order = new ArrayList<>();

        @Override
        public Date sendInsite(TAdrCycleTask task) {
            order.add("INSITE:" + task.getItemNo());
            return insiteOk ? t("2026-06-01 10:00:00") : null;
        }

        @Override
        public Date sendSms(TAdrCycleTask task) {
            order.add("SMS:" + task.getItemNo());
            return smsOk ? t("2026-06-01 10:00:30") : null;
        }
    }
}
