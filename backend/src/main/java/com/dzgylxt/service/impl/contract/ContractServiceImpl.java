package com.dzgylxt.service.impl.contract;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzgylxt.approval.ApprovalGateway;
import com.dzgylxt.approval.ApprovalTaskSpec;
import com.dzgylxt.common.BizException;
import com.dzgylxt.common.BusinessNoGenerator;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.entity.contract.Contract;
import com.dzgylxt.entity.purchase.AwardItem;
import com.dzgylxt.enums.ContractStatus;
import com.dzgylxt.mapper.contract.ContractMapper;
import com.dzgylxt.mapper.purchase.AwardItemMapper;
import com.dzgylxt.security.UserContext;
import com.dzgylxt.service.IContractService;
import com.dzgylxt.service.ISupplierService;
import com.dzgylxt.vo.contract.ContractRenewReqVO;
import com.dzgylxt.vo.contract.ContractSaveReqVO;
import com.dzgylxt.vo.contract.ContractWarnVO;
import com.dzgylxt.vo.supplier.SupplierAdmissionVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 合同服务实现（设计 §2.5 + §7.1）。
 *
 * <p>状态机：DRAFT → PENDING_APPROVAL → EFFECTIVE（available_amount = amount）
 * / 驳回→DRAFT；EFFECTIVE → EXPIRED（到期扫描）/ TERMINATED（终止，额度冻结）。
 * 仅 EFFECTIVE 且 valid_from ≤ today ≤ valid_to 可发起订单（三重校验规则①）。</p>
 */
@Service
public class ContractServiceImpl extends ServiceImpl<ContractMapper, Contract> implements IContractService {

    /** 审批 bizType（设计 §3）。 */
    public static final String BIZ_TYPE = "CONTRACT";

    @Autowired
    private AwardItemMapper awardItemMapper;

    @Autowired
    private ISupplierService supplierService;

    @Autowired
    private ApprovalGateway approvalGateway;

    @Autowired
    private BusinessNoGenerator businessNoGenerator;

    /** 金额审批阈值（元）：超过则升两级审批（本地桩保留节点语义）。 */
    @Value("${app.contract.approve-threshold:500000}")
    private BigDecimal approveThreshold;

    /** 到期预警提前天数（默认 30，对齐 P1 R-SUP-05 口径）。 */
    @Value("${app.contract.expire-warn-days:30}")
    private Integer expireWarnDays;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createContract(ContractSaveReqVO req) {
        if (req.getSupplierId() == null || req.getTitle() == null || req.getTitle().isBlank()
                || req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "供应商/标题/金额均必填且金额为正数");
        }
        // 登记即校验供应商准入（设计 §7.1 合同登记处）
        SupplierAdmissionVO admission = supplierService.getAdmission(req.getSupplierId());
        if (admission == null || !Boolean.TRUE.equals(admission.getQualified())) {
            String reason = admission == null ? "供应商不存在" : String.join("；", admission.getReasons());
            throw new BizException(ResultCode.PARAM_ERROR, "合同登记准入校验未通过：" + reason);
        }
        // 来源定标时校验金额 = Σ 定标明细（基本单位口径；<!-- D2 --> 无来源可手填）
        BigDecimal amount = req.getAmount();
        if (req.getAwardId() != null) {
            BigDecimal awardTotal = awardItemMapper.selectList(Wrappers.<AwardItem>lambdaQuery()
                            .eq(AwardItem::getAwardId, req.getAwardId())).stream()
                    .map(i -> i.getPrice().multiply(i.getQtyInBaseUnit()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
            if (amount.compareTo(awardTotal) != 0) {
                throw new BizException(ResultCode.PARAM_ERROR,
                        "合同金额必须等于 Σ 定标明细金额：" + awardTotal);
            }
        }
        if (req.getValidFrom() == null || req.getValidTo() == null
                || !req.getValidTo().isAfter(req.getValidFrom())) {
            throw new BizException(ResultCode.PARAM_ERROR, "合同有效期非法（valid_to 必须晚于 valid_from）");
        }

        Contract contract = new Contract();
        contract.setSupplierId(req.getSupplierId());
        contract.setAwardId(req.getAwardId());
        contract.setNo(businessNoGenerator.nextNo("HT"));
        contract.setTitle(req.getTitle());
        contract.setContractType(req.getContractType() == null ? 0 : req.getContractType());
        contract.setAmount(amount);
        contract.setValidFrom(req.getValidFrom());
        contract.setValidTo(req.getValidTo());
        contract.setFileKeys(req.getFileKeys() == null ? null : JSONUtil.toJsonStr(req.getFileKeys()));
        contract.setRenewedFromId(null);
        contract.setStatus(ContractStatus.DRAFT);
        contract.setAvailableAmount(BigDecimal.ZERO);
        contract.setVersion(0);
        contract.setRemark(req.getRemark());
        save(contract);
        return contract.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateContract(Long id, ContractSaveReqVO req) {
        Contract contract = getById(id);
        if (contract == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "合同不存在：" + id);
        }
        if (contract.getStatus() != ContractStatus.DRAFT) {
            throw new BizException(ResultCode.STATUS_INVALID, "仅草稿合同可编辑");
        }
        if (req.getTitle() != null) {
            contract.setTitle(req.getTitle());
        }
        if (req.getContractType() != null) {
            contract.setContractType(req.getContractType());
        }
        if (req.getAmount() != null) {
            contract.setAmount(req.getAmount());
        }
        if (req.getValidFrom() != null) {
            contract.setValidFrom(req.getValidFrom());
        }
        if (req.getValidTo() != null) {
            contract.setValidTo(req.getValidTo());
        }
        if (req.getFileKeys() != null) {
            contract.setFileKeys(JSONUtil.toJsonStr(req.getFileKeys()));
        }
        contract.setRemark(req.getRemark());
        updateById(contract);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submit(Long id) {
        Contract contract = getById(id);
        if (contract == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "合同不存在：" + id);
        }
        if (contract.getStatus() != ContractStatus.DRAFT
                && contract.getStatus() != ContractStatus.PENDING_APPROVAL) {
            throw new BizException(ResultCode.STATUS_INVALID, "仅草稿/待审批合同可提交");
        }
        contract.setStatus(ContractStatus.PENDING_APPROVAL);
        updateById(contract);

        ApprovalTaskSpec spec = new ApprovalTaskSpec();
        spec.setBizType(BIZ_TYPE);
        spec.setBizId(contract.getId());
        spec.setTitle("合同-" + contract.getNo());
        spec.setApplicant(UserContext.getCurrentUsername());
        spec.setPayloadJson(JSONUtil.toJsonStr(new Object() {
            public final Long supplierId = contract.getSupplierId();
            public final BigDecimal amount = contract.getAmount();
            public final LocalDate validFrom = contract.getValidFrom();
            public final LocalDate validTo = contract.getValidTo();
            public final Long awardId = contract.getAwardId();
            /** 金额超阈值升两级（阈值配置，<!-- D5 --> 由审批中心驱动节点）。 */
            public final boolean escalated = approveThreshold != null
                    && contract.getAmount().compareTo(approveThreshold) > 0;
        }));
        approvalGateway.create(spec);
        return contract.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void terminate(Long id, String reason) {
        Contract contract = getById(id);
        if (contract == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "合同不存在：" + id);
        }
        if (contract.getStatus() != ContractStatus.EFFECTIVE) {
            throw new BizException(ResultCode.STATUS_INVALID, "仅生效中合同可终止");
        }
        if (reason == null || reason.isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "终止原因必填");
        }
        contract.setStatus(ContractStatus.TERMINATED);
        // 额度冻结：available_amount 清零（本单不可再下单）
        contract.setAvailableAmount(BigDecimal.ZERO);
        contract.setTerminateReason(reason);
        updateById(contract);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long renew(Long id, ContractRenewReqVO req) {
        Contract origin = getById(id);
        if (origin == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "原合同不存在：" + id);
        }
        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0
                || req.getValidFrom() == null || req.getValidTo() == null
                || !req.getValidTo().isAfter(req.getValidFrom())) {
            throw new BizException(ResultCode.PARAM_ERROR, "续签金额/有效期非法");
        }
        Contract renew = new Contract();
        renew.setSupplierId(origin.getSupplierId());
        renew.setAwardId(origin.getAwardId());
        renew.setNo(businessNoGenerator.nextNo("HT"));
        renew.setTitle(origin.getTitle() + "-续签");
        renew.setContractType(origin.getContractType());
        renew.setAmount(req.getAmount());
        renew.setValidFrom(req.getValidFrom());
        renew.setValidTo(req.getValidTo());
        renew.setFileKeys(req.getFileKeys() == null ? null : JSONUtil.toJsonStr(req.getFileKeys()));
        // 框架续签口子（<!-- D1: P2b -->）：仅落追溯字段
        renew.setRenewedFromId(origin.getId());
        renew.setStatus(ContractStatus.DRAFT);
        renew.setAvailableAmount(BigDecimal.ZERO);
        renew.setVersion(0);
        renew.setRemark(req.getRemark());
        save(renew);
        return renew.getId();
    }

    @Override
    public List<ContractWarnVO> expiringList() {
        LocalDate today = LocalDate.now();
        List<Contract> contracts = list(Wrappers.<Contract>lambdaQuery()
                .eq(Contract::getStatus, ContractStatus.EFFECTIVE)
                .orderByAsc(Contract::getValidTo));
        List<ContractWarnVO> warns = new ArrayList<>();
        for (Contract contract : contracts) {
            if (contract.getValidTo() == null) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(today, contract.getValidTo());
            if (days <= expireWarnDays) {
                ContractWarnVO vo = new ContractWarnVO();
                vo.setContract(contract);
                vo.setDaysToExpire(days);
                vo.setMessage(days < 0 ? "已过期" + (-days) + "天，请及时处理"
                        : "将在 " + days + " 天后到期");
                warns.add(vo);
            }
        }
        return warns;
    }

    // ---------------- ApprovalCallbackHandler（bizType=CONTRACT） ----------------

    @Override
    public String bizType() {
        return BIZ_TYPE;
    }

    @Override
    public void onApproved(Long taskId, Long bizId, String comment) {
        Contract contract = getById(bizId);
        if (contract == null || contract.getStatus() != ContractStatus.PENDING_APPROVAL) {
            return;
        }
        contract.setStatus(ContractStatus.EFFECTIVE);
        contract.setAvailableAmount(contract.getAmount());
        updateById(contract);
    }

    @Override
    public void onRejected(Long taskId, Long bizId, String comment) {
        Contract contract = getById(bizId);
        if (contract == null || contract.getStatus() != ContractStatus.PENDING_APPROVAL) {
            return;
        }
        contract.setStatus(ContractStatus.DRAFT);
        updateById(contract);
    }
}
