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
import java.util.Date;

/**
 * 催送触达与撤回流水对象 t_adr_cycle_dispatch
 *
 * <p>两道（值守人站内消息 / 短信息）各落一条、各记各的送达；头一条没落不许拿第二条顶数。
 * 撤回另落一笔 {@link #KIND_REVOKE} 留痕，不盖催送单头回定的日子与真正催出那一刻。</p>
 *
 * @author fuce
 * @date 2026-09-30
 */
@TableName("t_adr_cycle_dispatch")
@ApiModel(value = "TAdrCycleDispatch", description = "催送触达与撤回流水")
public class TAdrCycleDispatch implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 流水种类：催送触达 */
    public static final int KIND_SEND = 0;
    /** 流水种类：撤回留痕 */
    public static final int KIND_REVOKE = 1;

    /** 触达道次：值守人站内消息（头一条） */
    public static final int CHANNEL_INSITE = 0;
    /** 触达道次：短信息（第二条） */
    public static final int CHANNEL_SMS = 1;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 催送单 */
    @TableField("task_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "催送单")
    private Long taskId;

    /** 催送单号 */
    @TableField("item_no")
    @ApiModelProperty(value = "催送单号")
    private String itemNo;

    /** 流水种类 0催送触达 1撤回留痕 */
    @TableField("kind")
    @ApiModelProperty(value = "流水种类 0催送触达 1撤回留痕")
    private Integer kind;

    /** 触达道次 0站内消息 1短信息（撤回留痕时为空） */
    @TableField("channel")
    @ApiModelProperty(value = "触达道次 0站内消息 1短信息")
    private Integer channel;

    /** 送达情形 0未达 1已达 */
    @TableField("delivered")
    @ApiModelProperty(value = "送达情形 0未达 1已达")
    private Integer delivered;

    /** 实际送达时刻 */
    @TableField("delivered_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "实际送达时刻")
    private Date deliveredAt;

    /** 本轮执行时刻 */
    @TableField("round_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "本轮执行时刻")
    private Date roundAt;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注（撤回缘故等） */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getItemNo() {
        return itemNo;
    }

    public void setItemNo(String itemNo) {
        this.itemNo = itemNo;
    }

    public Integer getKind() {
        return kind;
    }

    public void setKind(Integer kind) {
        this.kind = kind;
    }

    public Integer getChannel() {
        return channel;
    }

    public void setChannel(Integer channel) {
        this.channel = channel;
    }

    public Integer getDelivered() {
        return delivered;
    }

    public void setDelivered(Integer delivered) {
        this.delivered = delivered;
    }

    public Date getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(Date deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public Date getRoundAt() {
        return roundAt;
    }

    public void setRoundAt(Date roundAt) {
        this.roundAt = roundAt;
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
}
