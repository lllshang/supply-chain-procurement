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

    /** 到期预警派生（valid_to − 提前天数配置，默认 30）。 */
    List<ContractWarnVO> expiringList();
}
