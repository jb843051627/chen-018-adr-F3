package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 定期报告催送单对象 t_adr_cycle_task
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_adr_cycle_task")
@ApiModel(value = "TAdrCycleTask", description = "定期报告催送单")
public class TAdrCycleTask implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 定期报告催送单号 */
    @TableField("item_no")
    @ApiModelProperty(value = "定期报告催送单号")
    private String itemNo;

    /** 被催报告单位底册代号 */
    @TableField("site_no")
    @ApiModelProperty(value = "被催报告单位底册代号")
    private String siteNo;

    /** 事由属于哪一类 */
    @TableField("category")
    @ApiModelProperty(value = "事由属于哪一类")
    private String category;

    /** 应当出手那一日的止点时刻（含当日最后一刻，钉死不许后挪） */
    @TableField("due_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "应当出手那一日的止点时刻")
    private Date dueAt;

    /** 可提前几日开口催送（唯一能调的一栏） */
    @TableField("amount")
    @ApiModelProperty(value = "可提前几日开口催送")
    private BigDecimal amount;

    /** 事由与办法照哪一份写 */
    @TableField("content")
    @ApiModelProperty(value = "事由与办法照哪一份写")
    private String content;

    /** 联系人/联系方式（整季空着即转催不成） */
    @TableField("contact")
    @ApiModelProperty(value = "联系人/联系方式")
    private String contact;

    /** 真正催出那一刻（催成钉上，撤回不许盖） */
    @TableField("sent_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "真正催出那一刻")
    private Date sentAt;

    /** 催不成缘故（就地落在本条） */
    @TableField("fail_reason")
    @ApiModelProperty(value = "催不成缘故")
    private String failReason;

    /** 条目情形 0候催 1已催出 2催不成 */
    @TableField("status")
    @ApiModelProperty(value = "条目情形 0候催 1已催出 2催不成")
    private Integer status;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    /** 翻单时一回算定：此刻是否落在发声段（非持久化，翻单与回写同一把尺） */
    @TableField(exist = false)
    @ApiModelProperty(value = "此刻是否落在发声段(翻单时算定,非持久化)")
    private transient Boolean speaking;

    /** 翻单时一回算定：本轮是否处在岁末半月（非持久化） */
    @TableField(exist = false)
    @ApiModelProperty(value = "本轮是否岁末半月(翻单时算定,非持久化)")
    private transient Boolean yearEndRound;

    /** 翻单时一回算定：止点是否已过（非持久化） */
    @TableField(exist = false)
    @ApiModelProperty(value = "止点是否已过(翻单时算定,非持久化)")
    private transient Boolean overdueRound;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getItemNo() {
        return itemNo;
    }

    public void setItemNo(String itemNo) {
        this.itemNo = itemNo;
    }

    public String getSiteNo() {
        return siteNo;
    }

    public void setSiteNo(String siteNo) {
        this.siteNo = siteNo;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Date getDueAt() {
        return dueAt;
    }

    public void setDueAt(Date dueAt) {
        this.dueAt = dueAt;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public Date getSentAt() {
        return sentAt;
    }

    public void setSentAt(Date sentAt) {
        this.sentAt = sentAt;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Boolean getSpeaking() {
        return speaking;
    }

    public void setSpeaking(Boolean speaking) {
        this.speaking = speaking;
    }

    public Boolean getYearEndRound() {
        return yearEndRound;
    }

    public void setYearEndRound(Boolean yearEndRound) {
        this.yearEndRound = yearEndRound;
    }

    public Boolean getOverdueRound() {
        return overdueRound;
    }

    public void setOverdueRound(Boolean overdueRound) {
        this.overdueRound = overdueRound;
    }
}
