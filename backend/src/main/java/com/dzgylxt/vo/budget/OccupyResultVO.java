package com.dzgylxt.vo.budget;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 预算占用结果（P3 设计 §3：available=false 时调用方走拦截 + BUDGET 升级审批）。
 */
@Data
public class OccupyResultVO implements Serializable {
    /** 是否占用成功（false=余额不足/无月度行，未产生任何占用） */
    private boolean available;
    /** 缺口金额（available=false 时 &gt;0） */
    private BigDecimal overAmount;
    /** 可用余额（校验时点快照，跨行合计） */
    private BigDecimal balance;
    /** 本次实际占用金额（available=true 时 == 请求额） */
    private BigDecimal occupied;
    /** 触达的预算行ID（占用/释放落点） */
    private List<Long> lineIds = new ArrayList<>();
    private String message;

    public static OccupyResultVO ok(BigDecimal occupied, List<Long> lineIds) {
        OccupyResultVO vo = new OccupyResultVO();
        vo.setAvailable(true);
        vo.setOccupied(occupied);
        vo.setLineIds(lineIds);
        vo.setMessage("预算占用成功");
        return vo;
    }

    public static OccupyResultVO blocked(BigDecimal balance, BigDecimal overAmount, String message) {
        OccupyResultVO vo = new OccupyResultVO();
        vo.setAvailable(false);
        vo.setBalance(balance);
        vo.setOverAmount(overAmount);
        vo.setMessage(message);
        return vo;
    }
}
