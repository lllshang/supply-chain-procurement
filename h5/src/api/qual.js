import request from '@/utils/request'

// 供应商资质提交（对应报告 H5 范围：资质提交/审核/驳回重提）
// 阶段一后端该端点预留至阶段二，此处调用真实契约地址；未联通时由页面本地兜底。
export function submitQualification(data) {
  return request.post('/h5/api/v1/supplier-qual/submit', data)
}
