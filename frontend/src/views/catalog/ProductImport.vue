<template>
  <el-card>
    <div class="toolbar">
      <el-radio-group v-model="mode">
        <el-radio-button value="single">单规格导入</el-radio-button>
        <el-radio-button value="multi">多规格导入</el-radio-button>
      </el-radio-group>
      <span class="hint">
        {{ mode === 'single' ? '上传即校验导入（错误清单将在结果中展示）' : '上传后先预览，校验通过再确认导入' }}
      </span>
    </div>

    <!-- 单规格 -->
    <ImportWizard
      v-if="mode === 'single'"
      key="single"
      :submit-api="importSingle"
      accept=".xlsx,.xls"
      tip="列：SPU编码/SPU名称/品类编码/基本单位/采购单位/SKU编码/条码/规格/参考价/标准价/计价方式/商品简介"
    />

    <!-- 多规格 -->
    <ImportWizard
      v-else
      key="multi"
      :preview-api="previewMulti"
      :submit-api="importMulti"
      accept=".xlsx,.xls"
      tip="多规格行以“规格组合”列承载，如 颜色:红;尺寸:S"
    >
      <template #preview="{ preview }">
        <div v-if="preview" class="preview">
          <div class="meta">
            <span>模板版本：{{ preview.templateVersion || '-' }}</span>
            <span>展开行数：{{ (preview.rows || []).length }}</span>
            <span>错误数：{{ (preview.errors || []).length }}</span>
          </div>
          <el-table :data="(preview.rows || []).slice(0, 50)" border stripe size="small" max-height="320">
            <el-table-column prop="spuCode" label="SPU编码" width="120" />
            <el-table-column prop="spuName" label="SPU名称" min-width="140" />
            <el-table-column prop="skuCode" label="SKU编码" width="120" />
            <el-table-column prop="specCombos" label="规格组合" min-width="150" />
            <el-table-column prop="standardPrice" label="标准价" width="100" />
          </el-table>
          <div class="more" v-if="(preview.rows || []).length > 50">仅显示前 50 行……</div>
        </div>
        <el-empty v-else description="无预览内容" :image-size="60" />
      </template>
    </ImportWizard>
  </el-card>
</template>

<script setup>
import { ref } from 'vue'
import ImportWizard from '@/components/ImportWizard.vue'
import { importSingle, importMulti, previewMulti } from '@/api/catalog'

// P-C6 产品导入向导（单规格 / 多规格预览+导入）
const mode = ref('single')
</script>

<style scoped>
.toolbar { margin-bottom: 16px; display: flex; align-items: center; gap: 12px; }
.hint { color: #999; font-size: 12px; }
.preview .meta { display: flex; gap: 20px; margin-bottom: 10px; color: #666; font-size: 13px; }
.more { color: #999; font-size: 12px; margin-top: 6px; }
</style>
