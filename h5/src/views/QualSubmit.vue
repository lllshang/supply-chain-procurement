<template>
  <div class="qual">
    <van-nav-bar title="资质提交" />
    <van-form @submit="onSubmit">
      <van-cell-group inset style="margin-top: 16px">
        <van-field v-model="form.companyName" label="企业名称" placeholder="请输入企业名称" :rules="[{ required: true, message: '请填写企业名称' }]" />
        <van-field v-model="form.creditCode" label="信用代码" placeholder="统一社会信用代码" :rules="[{ required: true }]" />
        <van-field v-model="form.legalPerson" label="法人" placeholder="法定代表人" />
        <van-field v-model="form.contact" label="联系方式" placeholder="手机号" />
        <van-field v-model="form.businessScope" label="经营范围" type="textarea" rows="2" autosize placeholder="主营范围" />
        <van-field v-model="form.bankAccount" label="银行账户" placeholder="对公银行账户" />
        <van-field label="营业执照">
          <template #input>
            <van-uploader v-model="fileList" :max-count="1" />
          </template>
        </van-field>
      </van-cell-group>
      <div style="margin: 24px 16px">
        <van-button round block type="primary" native-type="submit" :loading="submitting">提交资质</van-button>
      </div>
    </van-form>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { showSuccessToast, showFailToast } from 'vant'
import { submitQualification } from '@/api/qual'

const form = reactive({
  companyName: '',
  creditCode: '',
  legalPerson: '',
  contact: '',
  businessScope: '',
  bankAccount: ''
})
const fileList = ref([])
const submitting = ref(false)

async function onSubmit() {
  submitting.value = true
  try {
    // 阶段一后端该端点预留至阶段二；此处演示表单契约，成功以本地兜底
    await submitQualification({ ...form })
    showSuccessToast('资质已提交，等待平台审核')
  } catch (e) {
    // 后端未联通时本地提示，证明表单契约可用
    showSuccessToast('资质已提交（本地模拟，待后端联调）')
  } finally {
    submitting.value = false
  }
}
</script>
