<template>
  <el-dialog v-model="visible" :title="form.id ? '编辑产品' : '新建产品'" width="560px">
    <el-form :model="form" label-width="90px">
      <el-form-item label="SPU编码" required>
        <el-input v-model="form.spuCode" placeholder="唯一编码" />
      </el-form-item>
      <el-form-item label="名称" required>
        <el-input v-model="form.name" placeholder="产品名称" />
      </el-form-item>
      <el-form-item label="品类" required>
        <TreeSelect v-model="form.categoryId" :fetcher="getCategoryTree" :leaf-only="true" show-level path-label placeholder="选择三级品类（叶子）" />
      </el-form-item>
      <el-form-item label="采购项类型" required>
        <el-radio-group v-model="form.itemType">
          <el-radio value="MATERIAL">物料类</el-radio>
          <el-radio value="SERVICE">服务类</el-radio>
        </el-radio-group>
        <div class="form-hint" v-if="form.itemType === 'SERVICE'">
          服务类不进入库存，不验收入库，按服务履约与质量考核结算。
        </div>
      </el-form-item>
      <el-form-item label="规格包装">
        <el-radio-group v-model="form.packType">
          <el-radio :value="0">普通</el-radio>
          <el-radio :value="1">固定混色箱</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="基本单位" required>
        <UnitSelect v-model="form.baseUnit" />
      </el-form-item>
      <el-form-item label="规格">
        <el-input v-model="form.spec" placeholder="选填，如 500g/袋" />
      </el-form-item>
      <el-form-item label="主图">
        <FileUpload v-model:value="form.imageFileKey" accept="image/*" biz-type="spu" />
      </el-form-item>
      <el-form-item label="简介">
        <el-input v-model="form.description" type="textarea" :rows="2" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import TreeSelect from '@/components/TreeSelect.vue'
import UnitSelect from '@/components/UnitSelect.vue'
import FileUpload from '@/components/FileUpload.vue'
import { getCategoryTree, createSpu, updateSpu, getSpu } from '@/api/catalog'

// 产品库 · SPU 表单
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  spu: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const saving = ref(false)
const form = reactive({
  id: null,
  spuCode: '',
  name: '',
  categoryId: null,
  itemType: 'MATERIAL',
  packType: 0,
  baseUnit: '',
  spec: '',
  imageFileKey: '',
  description: '',
  remark: ''
})

function reset() {
  Object.assign(form, {
    id: null, spuCode: '', name: '', categoryId: null, itemType: 'MATERIAL', packType: 0, baseUnit: '',
    spec: '', imageFileKey: '', description: '', remark: ''
  })
}

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return
    reset()
    if (props.spu?.id) {
      try {
        const res = await getSpu(props.spu.id)
        const d = res?.data || {}
        Object.assign(form, {
          id: d.id,
          spuCode: d.spuCode || '',
          name: d.name || '',
          categoryId: d.categoryId ?? null,
          itemType: d.itemType || 'MATERIAL',
          packType: d.packType ?? 0,
          baseUnit: d.baseUnit || '',
          spec: d.spec || '',
          imageFileKey: d.imageFileKey || '',
          description: d.description || '',
          remark: d.remark || ''
        })
      } catch (e) {
        // 拉取失败则退回列表行数据
        Object.assign(form, { id: props.spu.id, spuCode: props.spu.spuCode, name: props.spu.name, categoryId: props.spu.categoryId, baseUnit: props.spu.baseUnit })
      }
    }
  }
)

async function onSave() {
  if (!form.spuCode || !form.name || !form.categoryId || !form.baseUnit) {
    ElMessage.warning('SPU编码、名称、品类、基本单位必填')
    return
  }
  saving.value = true
  try {
    const payload = {
      spuCode: form.spuCode,
      name: form.name,
      categoryId: form.categoryId,
      itemType: form.itemType,
      packType: form.packType,
      baseUnit: form.baseUnit,
      spec: form.spec,
      imageFileKey: form.imageFileKey,
      description: form.description,
      remark: form.remark
    }
    if (form.id) {
      await updateSpu(form.id, payload)
    } else {
      await createSpu(payload)
    }
    ElMessage.success('保存成功')
    visible.value = false
    emit('saved')
  } catch (e) {
    // 错误由拦截器提示
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.form-hint { color: #909399; font-size: 12px; line-height: 1.4; margin-top: 4px; }
</style>
