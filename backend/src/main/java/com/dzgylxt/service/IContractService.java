package com.dzgylxt.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.approval.ApprovalCallbackHandler;
import com.dzgylxt.entity.contract.Contract;
import com.dzgylxt.vo.contract.ContractRenewReqVO;
import com.dzgylxt.vo.contract.ContractSaveReqVO;
import com.dzgylxt.vo.contract.ContractWarnVO;

import java.util.List;

/** 合同服务（设计 §2.5：登记/审批/终止/续签/到期预警）。 */
public interface IContractService extends IService<Contract>, ApprovalCallbackHandler {

    /** 登记即校验供应商准入；来源定标时校验金额 = Σ 定标明细；生成合同号。 */
    Long createContract(ContractSaveReqVO req);

    /** 仅 DRAFT 可编辑。 */
    void updateContract(Long id, ContractSaveReqVO req);

    /** 发起 ApprovalGateway(CONTRACT)（金额超阈值升两级，阈值配置）。 */
    Long submit(Long id);

    /** 仅 EFFECTIVE；置 TERMINATED、available_amount 冻结（本单不可下单）。 */
    void terminate(Long id, String reason);

    /** 续签：新合同 renewed_from_id = 原 id，独立走审批。 */
    Long renew(Long id, ContractRenewReqVO req);

    /**
     * 补充签订（P4 R3a）：新合同 source_contract_id = 原 id、relation_type = SUPPLEMENT，
     * 继承供应商/type_id，独立走 CONTRACT 审批（无新 bizType，PRD L1063）。
     */
    Long supplement(Long id, com.dzgylxt.vo.contract.ContractSupplementReqVO req);

    /** SKU 白名单查看（P4 D15）。 */
    java.util.List<com.dzgylxt.entity.contract.ContractSkuWhitelist> listSkuWhitelist(Long contractId);

    /** SKU 白名单覆盖式维护（P4 D15：空列表=清空白名单，恢复现网额度闸兜底行为）。 */
    boolean replaceSkuWhitelist(Long contractId, java.util.List<com.dzgylxt.vo.contract.SkuWhitelistReqVO> items);

    /** 到期预警派生（valid_to − 提前天数配置，默认 30）。 */
    List<ContractWarnVO> expiringList();
}
