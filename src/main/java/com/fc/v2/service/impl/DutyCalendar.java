package com.fc.v2.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TAdrDutyHolidayMapper;
import com.fc.v2.model.auto.TAdrDutyHoliday;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * 值守班次日历：一天照班切成段，只有落在发声段里的到期条目才许出手。
 *
 * <ul>
 *   <li>常规日：白班一段 08:00（含）—20:00（不含）发声；夜段整场不发声。</li>
 *   <li>法定节假日（{@code t_adr_duty_holiday} 在册日子）：整场不发声，
 *       段外就算有到期也只压着，候下一轮开口。</li>
 *   <li>岁末那半个月（12-16 至 12-31）不走上面切法，另排两段：
 *       上午 08:30（含）—11:30（不含）、下午 13:30（含）—17:30（不含）；
 *       节假日册在岁末不另压（那半个月只认这两段）。</li>
 * </ul>
 *
 * 翻单与回写共用这一把尺：同刻问两遍，答案必须一致。
 *
 * @author fuce
 * @date 2026-09-30
 */
@Component
public class DutyCalendar {

    /** 岁末半月起点日（含） */
    private static final int YEAR_END_FROM_DAY = 16;
    /** 岁末半月止点月 */
    private static final int YEAR_END_MONTH = Calendar.DECEMBER;

    /** 常规白班起/止（分钟，自零点起） */
    private static final int NORMAL_FROM = 8 * 60;
    private static final int NORMAL_TO = 20 * 60;

    /** 岁末上午段 */
    private static final int YE_AM_FROM = 8 * 60 + 30;
    private static final int YE_AM_TO = 11 * 60 + 30;
    /** 岁末下午段 */
    private static final int YE_PM_FROM = 13 * 60 + 30;
    private static final int YE_PM_TO = 17 * 60 + 30;

    @Resource
    private TAdrDutyHolidayMapper dutyHolidayMapper;

    /** 值守班次按中国本地时刻切，不跟部署机器的默认时区走 */
    private static final TimeZone DUTY_ZONE = TimeZone.getTimeZone("GMT+8");

    private static Calendar calendarOf(Date at) {
        Calendar c = Calendar.getInstance(DUTY_ZONE);
        c.setTime(at);
        return c;
    }

    /**
     * 这一刻在不在发声段里。夜段 / 法定节假日整场不发声；岁末只在另排的两段内发声。
     */
    public boolean isSpeakingTime(Date at) {
        if (at == null) {
            return false;
        }
        Calendar c = calendarOf(at);
        int minute = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);

        if (isYearEndHalfMonth(c)) {
            return (minute >= YE_AM_FROM && minute < YE_AM_TO)
                    || (minute >= YE_PM_FROM && minute < YE_PM_TO);
        }
        // 法定节假日整场压着
        if (isHoliday(at)) {
            return false;
        }
        return minute >= NORMAL_FROM && minute < NORMAL_TO;
    }

    /** 是否落在岁末那半个月（12-16 至 12-31，含首尾） */
    public boolean isYearEndHalfMonth(Date at) {
        if (at == null) {
            return false;
        }
        return isYearEndHalfMonth(calendarOf(at));
    }

    private boolean isYearEndHalfMonth(Calendar c) {
        return c.get(Calendar.MONTH) == YEAR_END_MONTH
                && c.get(Calendar.DAY_OF_MONTH) >= YEAR_END_FROM_DAY
                && c.get(Calendar.DAY_OF_MONTH) <= 31;
    }

    /**
     * 法定节假日在册判定：按日认，查 {@code del_flag=0 且 status=0} 的当日记录。
     */
    public boolean isHoliday(Date at) {
        if (at == null || this.dutyHolidayMapper == null) {
            return false;
        }
        Calendar c = calendarOf(at);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        Date dayStart = c.getTime();
        c.add(Calendar.DAY_OF_MONTH, 1);
        Date nextStart = c.getTime();

        Integer cnt = this.dutyHolidayMapper.selectCount(new QueryWrapper<TAdrDutyHoliday>()
                .eq("del_flag", 0)
                .eq("status", TAdrDutyHoliday.STATUS_ACTIVE)
                .ge("holiday_date", dayStart)
                .lt("holiday_date", nextStart));
        return cnt != null && cnt > 0;
    }
}
