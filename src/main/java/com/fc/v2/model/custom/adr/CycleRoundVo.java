package com.fc.v2.model.custom.adr;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 一轮催送的回话：候催 / 已催出 / 催不成三条数，连同逐条点出来的明细，
 * 由服务器这一回执行一趟清点一并交出——数与名单同一次算定，不许一头数一头估。
 *
 * <p>三条守恒：{@code waitCount + sentCount + failCount == pickedCount}。
 * 本轮一个该动的也没挑着时，{@code message} 实打实是「本轮无到期条目」，
 * 这不是跑砸（{@code success} 仍为真）。</p>
 *
 * @author fuce
 * @date 2026-09-30
 */
@ApiModel(value = "CycleRoundVo", description = "一轮催送回话")
public class CycleRoundVo {

    @ApiModelProperty(value = "本轮执行时刻")
    private Date roundAt;

    @ApiModelProperty(value = "是否跑毕（空跑也算跑毕，不等于跑砸）")
    private boolean success = true;

    @ApiModelProperty(value = "归进本轮的条数（出手那一日已到）")
    private int pickedCount;

    @ApiModelProperty(value = "本轮后仍是候催的条数（界外压着 / 岁末第二条没入账 / 头道未达待下轮）")
    private int waitCount;

    @ApiModelProperty(value = "本轮真正催出去的条数")
    private int sentCount;

    @ApiModelProperty(value = "本轮转催不成的条数（退出底册 / 联系人空 / 逾期撤回）")
    private int failCount;

    @ApiModelProperty(value = "其中：落在发声段外被压着的条数")
    private int heldCount;

    @ApiModelProperty(value = "其中：岁末同单位当日第二条、没入账的条数")
    private int dedupCount;

    @ApiModelProperty(value = "一句话回话；空跑时为「本轮无到期条目」")
    private String message;

    @ApiModelProperty(value = "仍候催的逐条单号")
    private List<String> waitItems = new ArrayList<>();

    @ApiModelProperty(value = "已催出的逐条单号")
    private List<String> sentItems = new ArrayList<>();

    @ApiModelProperty(value = "催不成的逐条单号")
    private List<String> failItems = new ArrayList<>();

    public Date getRoundAt() {
        return roundAt;
    }

    public void setRoundAt(Date roundAt) {
        this.roundAt = roundAt;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public int getPickedCount() {
        return pickedCount;
    }

    public void setPickedCount(int pickedCount) {
        this.pickedCount = pickedCount;
    }

    public int getWaitCount() {
        return waitCount;
    }

    public void setWaitCount(int waitCount) {
        this.waitCount = waitCount;
    }

    public int getSentCount() {
        return sentCount;
    }

    public void setSentCount(int sentCount) {
        this.sentCount = sentCount;
    }

    public int getFailCount() {
        return failCount;
    }

    public void setFailCount(int failCount) {
        this.failCount = failCount;
    }

    public int getHeldCount() {
        return heldCount;
    }

    public void setHeldCount(int heldCount) {
        this.heldCount = heldCount;
    }

    public int getDedupCount() {
        return dedupCount;
    }

    public void setDedupCount(int dedupCount) {
        this.dedupCount = dedupCount;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getWaitItems() {
        return waitItems;
    }

    public void setWaitItems(List<String> waitItems) {
        this.waitItems = waitItems;
    }

    public List<String> getSentItems() {
        return sentItems;
    }

    public void setSentItems(List<String> sentItems) {
        this.sentItems = sentItems;
    }

    public List<String> getFailItems() {
        return failItems;
    }

    public void setFailItems(List<String> failItems) {
        this.failItems = failItems;
    }
}
