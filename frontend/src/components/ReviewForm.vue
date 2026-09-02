<script setup>
import { reactive, ref } from 'vue';

const emit = defineEmits(['submit', 'load-demo']);
const props = defineProps({ submitting: Boolean });

const demoForm = {
  repository: 'acme/order-service',
  pullRequestNumber: 42,
  title: 'Add payment endpoint',
  path: 'src/main/java/com/acme/order/PaymentController.java',
  content: 'String token = request.getHeader("Authorization");\n// TODO add idempotency validation'
};
const form = reactive({ ...demoForm });
const validationMessage = ref('');

function submit() {
  if (!form.repository.trim() || !form.title.trim() || !form.path.trim() || !form.content.trim() || !Number.isInteger(Number(form.pullRequestNumber)) || Number(form.pullRequestNumber) < 1) {
    validationMessage.value = '请填写完整的评审信息';
    return;
  }
  validationMessage.value = '';
  emit('submit', {
    repository: form.repository.trim(),
    pullRequestNumber: Number(form.pullRequestNumber),
    title: form.title.trim(),
    files: [{ path: form.path.trim(), content: form.content }]
  });
}

function loadDemo() {
  Object.assign(form, demoForm);
  validationMessage.value = '';
  emit('load-demo');
}
</script>

<template>
  <section class="panel form-panel">
    <div class="panel-heading">
      <div>
        <p class="eyebrow">START A REVIEW</p>
        <h2>创建评审任务</h2>
      </div>
      <button class="icon-button subtle" type="button" title="载入示例" aria-label="载入示例" @click="loadDemo">↗</button>
    </div>

    <form class="review-form" novalidate @submit.prevent="submit">
      <div class="form-grid">
        <label class="field field-wide">
          <span>代码仓库</span>
          <input v-model="form.repository" type="text" placeholder="owner/repository" />
        </label>
        <label class="field">
          <span>Pull Request</span>
          <input v-model.number="form.pullRequestNumber" type="number" min="1" />
        </label>
        <label class="field field-wide">
          <span>变更标题</span>
          <input v-model="form.title" type="text" placeholder="Describe this change" />
        </label>
        <label class="field field-wide">
          <span>文件路径</span>
          <input v-model="form.path" type="text" placeholder="src/main/java/..." />
        </label>
      </div>

      <label class="field">
        <span>变更内容</span>
        <textarea v-model="form.content" rows="7" spellcheck="false" placeholder="Paste changed Java code here"></textarea>
      </label>

      <p v-if="validationMessage" class="form-error">{{ validationMessage }}</p>
      <button class="primary-button submit-button" type="submit" :disabled="props.submitting">
        <span class="button-dot"></span>
        {{ props.submitting ? '正在提交...' : '提交代码评审' }}
      </button>
    </form>
  </section>
</template>
