<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="1180px"
    class="product-edit-dialog"
    destroy-on-close
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="104px">
      <el-row :gutter="18">
        <!-- 产品编号 / SPU 编号 -->
        <el-col :span="12">
          <el-form-item :label="isMultiple ? 'SPU 编号' : '产品编号'" prop="spuCode">
            <el-input
              v-model="form.spuCode"
              :readonly="!isEdit || isMultiple"
              placeholder="系统自动生成"
            />
          </el-form-item>
        </el-col>
        <!-- 条码（非服务 & 非多规格） -->
        <el-col v-if="!isServiceItem && !isMultiple" :span="12">
          <el-form-item label="条码" prop="barcode">
            <el-input v-model="form.barcode" placeholder="请输入产品条码" />
          </el-form-item>
        </el-col>
        <!-- 产品名称 -->
        <el-col :span="isServiceItem || isMultiple ? 12 : 24">
          <el-form-item label="产品名称" prop="name">
            <el-input v-model="form.name" placeholder="请输入产品名称" />
          </el-form-item>
        </el-col>
        <!-- 采购项类型 -->
        <el-col :span="12">
          <el-form-item label="采购项类型">
            <el-radio-group v-model="form.itemType" :disabled="isEdit" @change="handleItemTypeChange">
              <el-radio-button label="物料类" value="MATERIAL" />
              <el-radio-button label="服务类" value="SERVICE" />
            </el-radio-group>
            <div class="field-hint">
              {{
                isServiceItem
                  ? '服务类无库存，不进入验收入库，按服务履约和质量考核结算。'
                  : '物料类进入采购、到货和验收入库流程。'
              }}
            </div>
          </el-form-item>
        </el-col>
        <!-- 规格模式（非服务） -->
        <el-col v-if="!isServiceItem" :span="24">
          <el-form-item label="规格模式">
            <div class="specification-mode-field">
              <el-radio-group v-model="form.specificationMode" @change="handleModeChange">
                <el-radio-button label="单规格" value="single" />
                <el-radio-button label="多规格" value="multiple" />
                <el-radio-button v-if="mixedPackFeatureEnabled" label="固定混色箱" value="mixed" />
              </el-radio-group>
              <span class="field-hint">
                {{
                  isMixed
                    ? '采购侧维护一个父 SKU，箱内颜色和数量通过子件明细固定。'
                    : '先维护规格类型和参数值，系统自动生成规格组合并分别定价。'
                }}
              </span>
            </div>
          </el-form-item>
        </el-col>
        <!-- 三级品类 -->
        <el-col :span="12">
          <el-form-item label="三级品类" prop="categoryId">
            <TreeSelect
              v-model="form.categoryId"
              :fetcher="getCategoryTree"
              :leaf-only="true"
              show-level
              path-label
              placeholder="选择三级品类（叶子）"
            />
          </el-form-item>
        </el-col>
        <!-- 产品图片 -->
        <el-col :span="24">
          <el-form-item label="产品图片">
            <div class="image-field">
              <el-upload
                action="#"
                :auto-upload="false"
                :show-file-list="false"
                accept="image/*"
                :on-change="handleImageChange"
              >
                <img v-if="form.imageUrl" :src="form.imageUrl" class="product-image-preview" />
                <div v-else class="upload-placeholder">
                  <el-icon><Plus /></el-icon>
                </div>
              </el-upload>
              <div class="field-hint">上传产品图片，便于采购选品识别。</div>
            </div>
          </el-form-item>
        </el-col>
        <!-- 产品简介 / 服务说明 -->
        <el-col :span="24">
          <el-form-item :label="isServiceItem ? '服务说明' : '产品简介'">
            <el-input
              v-model="form.description"
              type="textarea"
              :rows="2"
              maxlength="200"
              show-word-limit
              placeholder="简要说明产品用途、特点或采购注意事项"
            />
          </el-form-item>
        </el-col>
        <!-- 计量方式（非服务） -->
        <el-col v-if="!isServiceItem" :span="12">
          <el-form-item label="计量方式">
            <el-select v-model="form.measurementType" placeholder="请选择计量方式" style="width:100%">
              <el-option
                v-for="item in measurementTypeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
            <div class="field-hint">计件按数量管理；计重按实际重量管理。</div>
          </el-form-item>
        </el-col>
        <!-- 基本单位（非服务） -->
        <el-col v-if="!isServiceItem" :span="12">
          <el-form-item label="基本单位">
            <UnitSelect v-model="form.baseUnit" @change="handleBaseUnitChange" />
            <div class="field-hint">产品最小的计量单位；采购单位默认与其一致。</div>
          </el-form-item>
        </el-col>
        <!-- 采购单位 / 计价单位 -->
        <el-col :span="12">
          <el-form-item :label="isServiceItem ? '计价单位' : '采购单位'">
            <UnitSelect v-model="form.purchaseUnit" />
            <div class="field-hint">
              {{ isServiceItem ? '服务结算使用的单位，例如项、次、月。' : '默认同基本单位；按箱、包、盒采购时再修改。' }}
            </div>
          </el-form-item>
        </el-col>
        <!-- 规格（单规格） -->
        <el-col v-if="isSingle" :span="24">
          <el-form-item label="规格">
            <el-input v-model="form.spec" placeholder="如 500g/袋" />
          </el-form-item>
        </el-col>
        <!-- 单位换算（物料类：采购单位≠基本单位时维护；多规格为产品级默认值，逐行继承；混色箱由子件数自动汇总故禁用） -->
        <el-col
          v-if="!isServiceItem && form.purchaseUnit && form.baseUnit"
          :span="24"
        >
          <el-form-item label="单位换算">
            <div class="single-conversion-field">
              <span class="conversion-fixed">1</span>
              <span class="conversion-unit">{{ form.purchaseUnit }}</span>
              <span class="conversion-symbol">=</span>
              <el-input-number
                v-model="form.unitConversionFactor"
                :min="1"
                :precision="0"
                :step="1"
                :disabled="isMixed || !form.purchaseUnit || !form.baseUnit || form.purchaseUnit === form.baseUnit"
                controls-position="right"
                style="width:120px"
              />
              <span class="conversion-unit">{{ form.baseUnit }}</span>
            </div>
            <div class="field-hint">
              <template v-if="isMixed">固定混色箱的箱规由子件数量自动汇总，系统按“1 箱 = 合计子件数量”换算采购数量。</template>
              <template v-else>每个产品只维护一条采购单位到基本单位的换算关系。系统按“1 个采购单位 = N 个基本单位”折算采购数量，用于比价、下单和到货核对。</template>
            </div>
          </el-form-item>
        </el-col>
        <!-- 税率 -->
        <el-col :span="12">
          <el-form-item label="税率" prop="taxRate">
            <el-select v-model="form.taxRate" placeholder="请选择税率" style="width:100%">
              <el-option v-for="rate in taxRateOptions" :key="rate" :label="`${rate}%`" :value="rate" />
            </el-select>
          </el-form-item>
        </el-col>
        <!-- 标准价（单/混色箱） -->
        <el-col v-if="isSingle || isMixed" :span="12">
          <el-form-item label="标准价">
            <el-input-number
              v-model="form.standardPrice"
              :min="0"
              :precision="2"
              :step="1"
              controls-position="right"
              style="width:100%"
            />
            <div class="field-hint">元/{{ isMixed ? '箱' : form.purchaseUnit || '采购单位' }}。用于采购执行和订单默认带价，留空时使用参考价。</div>
          </el-form-item>
        </el-col>
        <!-- 参考价（单/混色箱） -->
        <el-col v-if="isSingle || isMixed" :span="12">
          <el-form-item label="参考价">
            <el-input-number
              v-model="form.referencePrice"
              :min="0"
              :precision="2"
              :step="1"
              controls-position="right"
              style="width:100%"
            />
            <div class="field-hint">元/{{ isMixed ? '箱' : form.purchaseUnit || '采购单位' }}。询价、比价和预算参考。</div>
          </el-form-item>
        </el-col>

        <!-- 固定混色箱子件 -->
        <el-col v-if="isMixed" :span="24">
          <div class="specification-block mixed-pack-block">
            <div class="conversion-header">
              <div>
                <span>固定混色箱明细</span>
                <div class="field-hint">采购侧按一个父 SKU 下单，箱内子件名称、规格和数量保持固定。</div>
              </div>
              <el-button size="small" :icon="Plus" @click="addMixedComponent">添加子件</el-button>
            </div>
            <el-table :data="form.mixedPackComponents" border size="small" empty-text="请添加混色箱子件">
              <el-table-column label="子件名称" min-width="150">
                <template #default="{ row }"><el-input v-model="row.name" placeholder="如 藏蓝玩偶" /></template>
              </el-table-column>
              <el-table-column label="子件 SKU 编号" min-width="170">
                <template #default="{ row }"><el-input v-model="row.skuCode" placeholder="选填，用于到货拆分" /></template>
              </el-table-column>
              <el-table-column label="子件规格" min-width="190">
                <template #default="{ row }"><el-input v-model="row.specification" placeholder="如 35cm / 藏蓝" /></template>
              </el-table-column>
              <el-table-column label="每箱数量" width="140">
                <template #default="{ row }">
                  <el-input-number v-model="row.quantity" :min="1" :precision="0" :step="1" controls-position="right" />
                </template>
              </el-table-column>
              <el-table-column label="基本单位" width="100" align="center">
                <template #default><span>{{ form.baseUnit || '件' }}</span></template>
              </el-table-column>
              <el-table-column label="操作" width="80" align="center">
                <template #default="{ $index }">
                  <el-button link type="danger" @click="removeMixedComponent($index)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <div class="conversion-tip">
              固定混色箱只维护一个采购 SKU，标准价按整箱维护。箱内子件仅用于到货拆分和下游颜色级库存；如果颜色比例会随批次变化，应改为“随机混色箱”。
            </div>
          </div>
        </el-col>

        <!-- 多规格：规格类型 + SKU 矩阵 -->
        <el-col v-if="isMultiple" :span="24">
          <div class="specification-block">
            <div class="conversion-header">
              <div>
                <span>规格类型</span>
                <div class="field-hint">定义产品的差异维度及可选参数值，例如大小、颜色。</div>
              </div>
              <el-button size="small" :icon="Plus" @click="addSpecType">添加规格类型</el-button>
            </div>
            <el-table
              :data="form.specAttributes"
              border
              size="small"
              empty-text="请添加规格类型"
            >
              <el-table-column label="规格类型" min-width="170">
                <template #default="{ $index }">
                  <el-select
                    :model-value="form.specAttributes[$index]?.name"
                    placeholder="选择规格类型"
                    class="full-width"
                    @change="(val) => handleSpecTypeSelection($index, val)"
                  >
                    <el-option
                      v-for="opt in availableSpecOptions($index)"
                      :key="opt.id"
                      :label="opt.name"
                      :value="opt.id"
                    />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="参数值" min-width="300">
                <template #default="{ $index }">
                  <el-select
                    :model-value="form.specAttributes[$index]?.values || []"
                    multiple
                    filterable
                    default-first-option
                    placeholder="请选择配置中的参数值"
                    class="full-width"
                    @change="(vals) => onSpecValuesChange($index, vals)"
                  >
                    <el-option
                      v-for="value in getSpecValueOptions(form.specAttributes[$index]?.name)"
                      :key="value"
                      :label="value"
                      :value="value"
                    />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="70" align="center">
                <template #default="{ $index }">
                  <el-button link type="danger" @click="removeSpecType($index)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>

            <div class="specification-combination-header">
              <div>
                <span>规格组合与价格</span>
                <div class="field-hint">系统根据规格类型的参数值自动生成组合，单位换算默认继承产品级参数并可逐行覆盖。</div>
              </div>
            </div>
            <el-table :data="skuMatrix" border size="small" empty-text="维护规格类型和参数值后自动生成">
              <el-table-column label="SKU 编号" width="170">
                <template #default="{ row, $index }">
                  <el-input :model-value="row.skuCode || `${form.spuCode}-${$index + 1}`" placeholder="自动生成" readonly />
                </template>
              </el-table-column>
              <el-table-column label="SKU 条码" width="150">
                <template #default="{ row }">
                  <el-input v-model="row.barcode" placeholder="选填" />
                </template>
              </el-table-column>
              <el-table-column label="规格组合" min-width="170" show-overflow-tooltip>
                <template #default="{ row }"><span>{{ row.combinationText }}</span></template>
              </el-table-column>
              <el-table-column label="单位换算" width="205">
                <template #default="{ row }">
                  <div class="sku-conversion-field">
                    <span>1{{ form.purchaseUnit || '采购单位' }} =</span>
                    <el-input-number
                      v-model="row.unitConversionFactor"
                      :min="1"
                      :precision="0"
                      :step="1"
                      :disabled="!form.purchaseUnit || !form.baseUnit || form.purchaseUnit === form.baseUnit"
                      controls-position="right"
                      style="width:90px"
                    />
                    <span>{{ form.baseUnit || '基本单位' }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column :label="`标准价（元/${form.purchaseUnit || '采购单位'}，选填）`" width="154">
                <template #default="{ row }">
                  <el-input-number v-model="row.standardPrice" :min="0" :precision="2" controls-position="right" style="width:100%" />
                </template>
              </el-table-column>
              <el-table-column :label="`参考价（元/${form.purchaseUnit || '采购单位'}，必填）`" width="154">
                <template #default="{ row }">
                  <el-input-number v-model="row.referencePrice" :min="0" :precision="2" controls-position="right" style="width:100%" />
                </template>
              </el-table-column>
            </el-table>
            <div class="conversion-tip">
              参考价必填；标准价选填，未填写时采购流程使用参考价。开启“采购最低价同步标准价”后，标准价按历史最低成交价自动更新。
            </div>
          </div>
        </el-col>
      </el-row>
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
import { Plus } from '@element-plus/icons-vue'
import TreeSelect from '@/components/TreeSelect.vue'
import UnitSelect from '@/components/UnitSelect.vue'
import {
  getCategoryTree,
  createSpu,
  updateSpu,
  getSpu,
  listSkusBySpu,
  createSku,
  updateSku,
  deleteSku,
  listSpecGrouped,
  uploadFile
} from '@/api/catalog'
import { taxRateOptions, measurementTypeOptions, mixedPackFeatureEnabled } from '@/constants/enums'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  spu: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const formRef = ref()
const saving = ref(false)

const form = reactive({
  id: null,
  spuCode: '',
  name: '',
  categoryId: null,
  itemType: 'MATERIAL',
  specificationMode: 'single',
  barcode: '',
  measurementType: 'piece',
  baseUnit: '',
  purchaseUnit: '',
  spec: '',
  unitConversionFactor: 1,
  taxRate: 13,
  standardPrice: null,
  referencePrice: null,
  imageFileKey: '',
  imageUrl: '',
  description: '',
  remark: '',
  mixedPackComponents: [],
  specAttributes: []
})

// 多规格 SKU 矩阵（由 specAttributes 笛卡尔积生成，可编辑价格/换算）
const skuMatrix = ref([])
const originalSkuIds = ref([])
const singleSkuId = ref(null)

// 规格配置主数据（分组：specName -> [specValue...]），用于多规格「规格类型」下拉
const specGroupedMap = ref({})
const specOptions = computed(() =>
  Object.entries(specGroupedMap.value).map(([name, values]) => ({ id: name, name, values }))
)
function getSpecValueOptions(name) {
  return specGroupedMap.value[name] || []
}
function availableSpecOptions(index) {
  const current = form.specAttributes[index]?.name
  const used = form.specAttributes
    .map((a, i) => (i !== index ? a.name : ''))
    .filter(Boolean)
  return specOptions.value.filter((opt) => opt.name === current || !used.includes(opt.name))
}

const isEdit = computed(() => !!form.id)
const isServiceItem = computed(() => form.itemType === 'SERVICE')
const isSingle = computed(() => form.specificationMode === 'single')
const isMultiple = computed(() => form.specificationMode === 'multiple')
const isMixed = computed(() => form.specificationMode === 'mixed')

const title = computed(() => {
  if (isServiceItem.value) return isEdit.value ? '编辑服务类采购项' : '新增服务类采购项'
  return isEdit.value ? '编辑产品' : '新增产品'
})

const mixedBoxTotal = computed(() =>
  (form.mixedPackComponents || []).reduce((t, c) => t + (Number(c.quantity) || 0), 0)
)

const rules = {
  name: [{ required: true, message: '请输入产品名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择三级品类', trigger: 'change' }],
  taxRate: [{ required: true, message: '请选择税率', trigger: 'change' }]
}

// ===== 解析后端返回的 JSON 文本字段 =====
function parseJson(str, fallback) {
  if (!str) return fallback
  try {
    const v = JSON.parse(str)
    return Array.isArray(v) ? v : fallback
  } catch (e) {
    return fallback
  }
}

function genSpuCode() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  const s = `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}`
  return `PRD${s}${Math.floor(Math.random() * 9000 + 1000)}`
}

function reset() {
  Object.assign(form, {
    id: null, spuCode: '', name: '', categoryId: null, itemType: 'MATERIAL',
    specificationMode: 'single', barcode: '', measurementType: 'piece', baseUnit: '',
    purchaseUnit: '', spec: '', unitConversionFactor: 1, taxRate: 13, standardPrice: null,
    referencePrice: null, imageFileKey: '', imageUrl: '', description: '', remark: '',
    mixedPackComponents: [], specAttributes: []
  })
  skuMatrix.value = []
  originalSkuIds.value = []
  singleSkuId.value = null
}

function valuationType() {
  return form.measurementType === 'weight' ? 'BY_WEIGHT' : 'BY_PIECE'
}

// ===== 多规格：规格类型维护（来源：规格配置主数据） =====
function addSpecType() {
  const used = form.specAttributes.map((a) => a.name)
  const available = specOptions.value.find((opt) => !used.includes(opt.name))
  form.specAttributes.push(
    available
      ? { id: available.id, name: available.name, values: [] }
      : { id: '', name: '', values: [] }
  )
}
function removeSpecType(i) {
  form.specAttributes.splice(i, 1)
  regenMatrix()
}
function handleSpecTypeSelection(index, specId) {
  const opt = specOptions.value.find((o) => o.id === specId)
  if (!opt) return
  form.specAttributes[index] = { id: opt.id, name: opt.name, values: [] }
  regenMatrix()
}
// 显式按索引写入参数值再重算矩阵（el-table 行内 v-model 在多行时写回不稳定）
function onSpecValuesChange(index, vals) {
  if (form.specAttributes[index]) form.specAttributes[index].values = vals
  regenMatrix()
}

// 笛卡尔积生成 SKU 矩阵，按 signature 保留已录入的价格/换算
function regenMatrix() {
  const attrs = form.specAttributes.filter((a) => a.name && a.values && a.values.length)
  let combos = [[]]
  for (const a of attrs) {
    const next = []
    for (const c of combos) for (const v of a.values) next.push([...c, { attributeId: a.id, name: a.name, value: v }])
    combos = next
  }
  const prev = {}
  for (const r of skuMatrix.value) if (r.signature) prev[r.signature] = r
  skuMatrix.value = combos.map((combo) => {
    const signature = combo.map((x) => `${x.attributeId}:${x.value}`).join('|')
    const combinationText = combo.map((x) => x.value).join(' / ')
    const specValues = combo.map((x) => ({ attributeId: x.attributeId, value: x.value }))
    const old = prev[signature] || {}
    return {
      id: old.id || null,
      skuCode: old.skuCode || '',
      signature,
      combinationText,
      specValues,
      barcode: old.barcode || '',
      unitConversionFactor: old.unitConversionFactor || form.unitConversionFactor || 1,
      standardPrice: old.standardPrice != null ? old.standardPrice : null,
      referencePrice: old.referencePrice != null ? old.referencePrice : null
    }
  })
}

// ===== 固定混色箱 =====
function addMixedComponent() {
  form.mixedPackComponents.push({ name: '', skuCode: '', specification: '', quantity: 1, baseUnit: form.baseUnit || '件' })
}
function removeMixedComponent(i) {
  form.mixedPackComponents.splice(i, 1)
}

// ===== 事件 =====
function handleItemTypeChange(val) {
  if (val === 'SERVICE') {
    form.specificationMode = 'single'
    form.measurementType = 'piece'
    form.barcode = ''
    form.mixedPackComponents = []
    form.specAttributes = []
    skuMatrix.value = []
    if (!form.purchaseUnit) form.purchaseUnit = form.baseUnit
    form.baseUnit = form.purchaseUnit
  }
}
// 基本单位变更：采购单位默认与其一致（贴合原型 handleBaseUnitChange，L368-372）
function handleBaseUnitChange(value) {
  if (!value) return
  if (!form.purchaseUnit) form.purchaseUnit = value
}
function handleModeChange(val) {
  if (val === 'multiple') regenMatrix()
  else skuMatrix.value = []
}

// ===== 图片上传（带预览） =====
async function handleImageChange(uploadFileObj) {
  const raw = uploadFileObj?.raw
  if (!raw) return
  try {
    const res = await uploadFile(raw)
    const data = res?.data || {}
    if (data.fileKey) {
      form.imageFileKey = data.fileKey
      form.imageUrl = data.url || URL.createObjectURL(raw)
      ElMessage.success('上传成功')
    }
  } catch (e) {
    ElMessage.warning('图片上传失败，可稍后重试')
  }
}

// ===== 载入 =====
watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return
    reset()
    try {
      const groupedRes = await listSpecGrouped()
      specGroupedMap.value = groupedRes?.data || {}
    } catch (e) {
      specGroupedMap.value = {}
    }
    if (!props.spu?.id) {
      form.spuCode = genSpuCode()
      return
    }
    try {
      const res = await getSpu(props.spu.id)
      const d = res?.data || {}
      Object.assign(form, {
        id: d.id,
        spuCode: d.spuCode || '',
        name: d.name || '',
        categoryId: d.categoryId ?? null,
        itemType: d.itemType || 'MATERIAL',
        specificationMode: d.specificationMode || 'single',
        barcode: d.barcode || '',
        measurementType: d.measurementType || 'piece',
        baseUnit: d.baseUnit || '',
        purchaseUnit: d.purchaseUnit || '',
        spec: d.spec || '',
        unitConversionFactor: d.unitConversionFactor != null ? d.unitConversionFactor : 1,
        taxRate: d.taxRate != null ? Number(d.taxRate) : 13,
        standardPrice: d.standardPrice != null ? Number(d.standardPrice) : null,
        referencePrice: d.referencePrice != null ? Number(d.referencePrice) : null,
        imageFileKey: d.imageFileKey || '',
        imageUrl: '',
        description: d.description || '',
        remark: d.remark || '',
        mixedPackComponents: parseJson(d.mixedPackComponents, []),
        specAttributes: parseJson(d.specAttributes, [])
      })
      if (isServiceItem.value) return
      const skuRes = await listSkusBySpu(props.spu.id)
      const list = skuRes?.data || []
      originalSkuIds.value = list.map((s) => s.id)
      if (isMultiple.value) {
        skuMatrix.value = list.map((s) => ({
          id: s.id,
          skuCode: s.skuCode || '',
          signature: '',
          combinationText: s.spec || '',
          specValues: parseJson(s.specValues, []),
          barcode: s.barcode || '',
          unitConversionFactor: s.unitConversionFactor != null ? s.unitConversionFactor : 1,
          standardPrice: s.standardPrice != null ? Number(s.standardPrice) : null,
          referencePrice: s.referencePrice != null ? Number(s.referencePrice) : null
        }))
      } else {
        const sk = list[0]
        if (sk) {
          singleSkuId.value = sk.id
          form.standardPrice = sk.standardPrice != null ? Number(sk.standardPrice) : form.standardPrice
          form.referencePrice = sk.referencePrice != null ? Number(sk.referencePrice) : form.referencePrice
          form.unitConversionFactor = sk.unitConversionFactor != null ? sk.unitConversionFactor : form.unitConversionFactor
        }
      }
    } catch (e) {
      Object.assign(form, {
        id: props.spu.id, spuCode: props.spu.spuCode, name: props.spu.name,
        categoryId: props.spu.categoryId, baseUnit: props.spu.baseUnit
      })
    }
  }
)

// ===== SKU 对账 =====
async function reconcileSkus(spuId, payloads) {
  const currentIds = new Set(payloads.filter((p) => p.id).map((p) => p.id))
  for (const id of originalSkuIds.value) {
    if (!currentIds.has(id)) await deleteSku(id)
  }
  for (const p of payloads) {
    if (p.id) await updateSku(p.id, p)
    else {
      const newId = await createSku(p)
      p.id = newId
    }
  }
}

// ===== 保存 =====
async function onSave() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  if (!isServiceItem.value) {
    if (!form.baseUnit) { ElMessage.warning('请选择基本单位'); return }
    if (!form.purchaseUnit) { ElMessage.warning('请选择采购单位'); return }
    if (!form.measurementType) { ElMessage.warning('请选择计量方式'); return }
  }
  if (form.taxRate == null) { ElMessage.warning('请选择税率'); return }

  let skuPayloads = []
  if (!isServiceItem.value) {
    if (isMultiple.value) {
      if (skuMatrix.value.length === 0) { ElMessage.warning('请先维护规格类型和参数值'); return }
      for (const row of skuMatrix.value) {
        if (row.referencePrice == null) { ElMessage.warning(`规格「${row.combinationText || '未命名'}」的参考价必填`); return }
      }
      skuPayloads = skuMatrix.value.map((row, i) => ({
        id: row.id || null,
        spuId: null,
        skuCode: row.skuCode || `${form.spuCode}-${i + 1}`,
        barcode: row.barcode || null,
        spec: row.combinationText,
        baseUnit: form.baseUnit,
        purchaseUnit: form.purchaseUnit,
        referencePrice: row.referencePrice,
        standardPrice: row.standardPrice || null,
        valuationType: valuationType(),
        specValues: row.specValues,
        unitConversionFactor: row.unitConversionFactor || 1,
        status: 'NORMAL'
      }))
    } else {
      // single / mixed：隐式 1 个 SKU
      if (isSingle.value && form.referencePrice == null) { ElMessage.warning('参考价必填'); return }
      const specText = isMixed.value
        ? `${form.name}固定混色箱（${(form.mixedPackComponents || []).map((c) => `${c.name}${c.quantity}${c.baseUnit || form.baseUnit}`).join('+')}）`
        : form.spec
      skuPayloads = [{
        id: singleSkuId.value || null,
        spuId: null,
        skuCode: form.spuCode,
        barcode: form.barcode || null,
        spec: specText,
        baseUnit: form.baseUnit,
        purchaseUnit: form.purchaseUnit,
        referencePrice: isSingle.value ? form.referencePrice : (form.referencePrice || null),
        standardPrice: isSingle.value ? form.standardPrice : (form.standardPrice || null),
        valuationType: valuationType(),
        unitConversionFactor: isMixed.value ? (mixedBoxTotal.value || 1) : (form.unitConversionFactor || 1),
        status: 'NORMAL'
      }]
    }
  }

  const payload = {
    spuCode: form.spuCode,
    name: form.name,
    categoryId: form.categoryId,
    itemType: form.itemType,
    specificationMode: form.specificationMode,
    packType: isMixed.value ? 1 : 0,
    spec: form.spec,
    baseUnit: form.baseUnit,
    purchaseUnit: form.purchaseUnit,
    measurementType: form.measurementType,
    barcode: form.barcode,
    taxRate: form.taxRate,
    standardPrice: form.standardPrice,
    referencePrice: form.referencePrice,
    unitConversionFactor: isMultiple.value ? null : (isMixed.value ? mixedBoxTotal.value || 1 : form.unitConversionFactor),
    imageFileKey: form.imageFileKey,
    description: form.description,
    remark: form.remark,
    mixedPackComponents: isMixed.value ? form.mixedPackComponents : null,
    specAttributes: isMultiple.value ? form.specAttributes.map((a) => ({ id: a.id, name: a.name, values: a.values })) : null
  }

  saving.value = true
  try {
    let spuId = form.id
    if (form.id) await updateSpu(form.id, payload)
    else spuId = await createSpu(payload)

    if (isServiceItem.value) {
      for (const id of originalSkuIds.value) await deleteSku(id)
    } else {
      await reconcileSkus(spuId, skuPayloads.map((p) => ({ ...p, spuId })))
    }

    ElMessage.success('保存成功')
    visible.value = false
    emit('saved')
  } catch (e) {
    // 业务错误由拦截器提示
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.field-hint { color: #909399; font-size: 12px; line-height: 1.4; margin-top: 4px; }
.specification-mode-field { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.image-field { display: flex; align-items: center; gap: 14px; }
.product-image-preview { width: 96px; height: 96px; object-fit: cover; border-radius: 6px; border: 1px solid #ebeef5; }
.upload-placeholder {
  width: 96px; height: 96px; border: 1px dashed #c0c4cc; border-radius: 6px;
  display: flex; align-items: center; justify-content: center; font-size: 28px; color: #c0c4cc; cursor: pointer;
}
.upload-placeholder:hover { border-color: #409eff; color: #409eff; }
.single-conversion-field { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; width: 100%; padding: 12px 14px; border: 1px solid #dfe5ee; border-radius: 6px; background: #f8fafc; }
.single-conversion-field .el-input-number { width: 120px; }
.conversion-fixed, .conversion-symbol { color: #4b5563; font-weight: 600; }
.conversion-unit { min-width: 64px; padding: 0 10px; color: #1f2937; font-weight: 600; line-height: 32px; text-align: center; background: #eef2f7; border-radius: 4px; }
.conversion-tip { margin-top: 10px; color: #6b7280; font-size: 12px; line-height: 1.6; }
.specification-block { border: 1px solid #ebeef5; border-radius: 8px; padding: 14px; margin-bottom: 8px; }
.conversion-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.conversion-header > div > span { font-weight: 600; }
.field-hint { color: #909399; font-size: 12px; line-height: 1.4; margin-top: 4px; }
.full-width { width: 100%; }
.sku-conversion-field { display: flex; align-items: center; gap: 4px; }
.specification-combination-header { margin: 14px 0 8px; }
.specification-combination-header > div > span { font-weight: 600; }
</style>
