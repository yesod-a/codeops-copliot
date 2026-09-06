<script setup>
import { onMounted, ref } from 'vue';
import {
  createManagedAgent, createManagedUser, listManagedUsers, removeManagedProjectMembership,
  setManagedAgentActive, updateManagedProjectMembership, updateManagedUser
} from '../api/reviewApi.js';

const props = defineProps({ projects: { type: Array, default: () => [] } });
const users = ref([]);
const loading = ref(false);
const saving = ref(false);
const error = ref('');
const issuedToken = ref('');
const agentTarget = ref(null);
const agentName = ref('');
const memberProjectId = ref('');
const memberRole = ref('REVIEWER');
const userForm = ref(emptyUserForm());

function emptyUserForm() {
  return { username: '', displayName: '', password: '', role: 'USER' };
}

async function loadUsers() {
  loading.value = true;
  error.value = '';
  try {
    users.value = await listManagedUsers();
  } catch (requestError) {
    error.value = requestError.message || '无法加载用户。';
  } finally {
    loading.value = false;
  }
}

async function createUser() {
  saving.value = true;
  error.value = '';
  try {
    await createManagedUser(userForm.value);
    userForm.value = emptyUserForm();
    await loadUsers();
  } catch (requestError) {
    error.value = requestError.message || '创建用户失败。';
  } finally {
    saving.value = false;
  }
}

async function saveUser(user) {
  saving.value = true;
  error.value = '';
  try {
    await updateManagedUser(user.id, { displayName: user.displayName, role: user.role, active: user.active });
    await loadUsers();
  } catch (requestError) {
    error.value = requestError.message || '保存用户失败。';
  } finally {
    saving.value = false;
  }
}

function openAgentDialog(user) {
  issuedToken.value = '';
  agentTarget.value = user;
  agentName.value = `${user.username}-device`;
}

async function createAgent() {
  if (!agentTarget.value || !agentName.value.trim()) return;
  saving.value = true;
  error.value = '';
  try {
    const agent = await createManagedAgent(agentTarget.value.id, agentName.value.trim());
    issuedToken.value = agent.token;
    await loadUsers();
  } catch (requestError) {
    error.value = requestError.message || '创建设备 Token 失败。';
  } finally {
    saving.value = false;
  }
}

async function toggleAgent(user, agent) {
  saving.value = true;
  error.value = '';
  try {
    await setManagedAgentActive(user.id, agent.id, !agent.active);
    await loadUsers();
  } catch (requestError) {
    error.value = requestError.message || '更新设备状态失败。';
  } finally {
    saving.value = false;
  }
}

async function addMembership(user) {
  if (!memberProjectId.value) return;
  saving.value = true;
  error.value = '';
  try {
    await updateManagedProjectMembership(memberProjectId.value, user.id, memberRole.value);
    memberProjectId.value = '';
    await loadUsers();
  } catch (requestError) {
    error.value = requestError.message || '保存项目授权失败。';
  } finally {
    saving.value = false;
  }
}

async function removeMembership(user, membership) {
  saving.value = true;
  error.value = '';
  try {
    await removeManagedProjectMembership(membership.projectId, user.id);
    await loadUsers();
  } catch (requestError) {
    error.value = requestError.message || '移除项目授权失败。';
  } finally {
    saving.value = false;
  }
}

onMounted(loadUsers);
</script>

<template>
  <section class="user-management">
    <p v-if="error" class="notice-banner"><span>!</span>{{ error }}</p>

    <section class="panel user-create-panel">
      <div class="panel-heading"><div><p class="eyebrow">管理员</p><h2>新建用户</h2></div></div>
      <form class="user-form-grid" @submit.prevent="createUser">
        <label class="field"><span>用户名</span><input v-model.trim="userForm.username" maxlength="120" autocomplete="off" required /></label>
        <label class="field"><span>显示名</span><input v-model.trim="userForm.displayName" maxlength="160" required /></label>
        <label class="field"><span>初始密码</span><input v-model="userForm.password" type="password" minlength="8" maxlength="256" required /></label>
        <label class="field"><span>全局角色</span><select v-model="userForm.role" class="app-select"><option value="USER">普通用户</option><option value="ADMIN">管理员</option></select></label>
        <button class="primary-button" type="submit" :disabled="saving">{{ saving ? '保存中...' : '创建用户' }}</button>
      </form>
    </section>

    <p v-if="loading" class="scan-message">正在加载用户...</p>
    <section v-else class="user-list" aria-label="用户列表">
      <article v-for="user in users" :key="user.id" class="panel user-row" :class="{ inactive: !user.active }">
        <div class="user-row-header"><div><h2>{{ user.displayName }}</h2><p>{{ user.username }} <span class="role-badge">{{ user.role === 'ADMIN' ? '管理员' : '用户' }}</span></p></div><span class="state-badge" :class="{ disabled: !user.active }">{{ user.active ? '已启用' : '已停用' }}</span></div>

        <div class="user-edit-grid">
          <label class="field"><span>显示名</span><input v-model.trim="user.displayName" maxlength="160" required /></label>
          <label class="field"><span>全局角色</span><select v-model="user.role" class="app-select"><option value="USER">普通用户</option><option value="ADMIN">管理员</option></select></label>
          <label class="checkbox-field"><input v-model="user.active" type="checkbox" />启用用户</label>
          <button class="secondary-button" type="button" :disabled="saving" @click="saveUser(user)">保存用户</button>
        </div>

        <div class="user-section"><div class="section-label"><strong>本地 Client 设备</strong><button class="icon-button" :data-test="`new-agent-${user.id}`" type="button" title="创建设备 Token" @click="openAgentDialog(user)">+</button></div>
          <ul v-if="user.agents.length" class="metadata-list"><li v-for="agent in user.agents" :key="agent.id"><span><strong>{{ agent.name }}</strong><small>最后使用：{{ agent.lastSeenAt || '从未使用' }}</small></span><button class="text-button" type="button" :disabled="saving" @click="toggleAgent(user, agent)">{{ agent.active ? '吊销' : '重新启用' }}</button></li></ul>
          <p v-else class="empty-inline">尚未创建设备 Token。</p>
        </div>

        <div class="user-section"><div class="section-label"><strong>项目授权</strong></div>
          <ul v-if="user.memberships.length" class="metadata-list"><li v-for="membership in user.memberships" :key="membership.projectId"><span><strong>{{ membership.projectName }}</strong><small>{{ membership.role }}</small></span><button class="text-button" type="button" :disabled="saving" @click="removeMembership(user, membership)">移除</button></li></ul>
          <div class="membership-form"><select v-model="memberProjectId" class="app-select"><option value="">选择项目</option><option v-for="project in props.projects" :key="project.id" :value="String(project.id)">{{ project.name }}</option></select><select v-model="memberRole" class="app-select"><option value="OWNER">OWNER</option><option value="REVIEWER">REVIEWER</option><option value="VIEWER">VIEWER</option></select><button class="secondary-button" type="button" :disabled="saving || !memberProjectId" @click="addMembership(user)">授权</button></div>
        </div>
      </article>
      <p v-if="!users.length" class="empty-rule-state">尚未创建用户。</p>
    </section>
  </section>

  <Teleport to="body">
    <div v-if="agentTarget" class="user-dialog-backdrop" @click.self="agentTarget = null">
      <section class="user-dialog" role="dialog" aria-modal="true" aria-labelledby="agent-dialog-title">
        <div class="panel-heading"><div><p class="eyebrow">{{ agentTarget.displayName }}</p><h2 id="agent-dialog-title">新建设备 Token</h2></div><button class="icon-button" type="button" title="关闭" @click="agentTarget = null">×</button></div>
        <template v-if="!issuedToken"><label class="field"><span>设备名称</span><input v-model.trim="agentName" data-test="agent-name" maxlength="160" required /></label><button class="primary-button" data-test="create-agent" type="button" :disabled="saving || !agentName" @click="createAgent">创建 Token</button></template>
        <template v-else><p class="notice-banner"><span>!</span>Token 只显示一次。请立即用于此设备的 Local Client 登录。</p><code class="issued-token" data-test="issued-token">{{ issuedToken }}</code><button class="secondary-button" type="button" @click="agentTarget = null">关闭</button></template>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.user-management, .user-list { display: grid; gap: 16px; }
.user-create-panel, .user-row, .user-dialog { padding: 20px; }
.user-form-grid, .user-edit-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; align-items: end; }
.user-row { display: grid; gap: 16px; }
.user-row.inactive { opacity: .68; }
.user-row-header, .section-label, .metadata-list li, .membership-form { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.user-row-header h2 { margin: 0; font-size: 18px; }.user-row-header p { margin: 4px 0 0; color: var(--muted, #637083); }
.role-badge, .state-badge { display: inline-flex; padding: 3px 7px; border: 1px solid #b8c4d2; border-radius: 4px; font-size: 12px; }.state-badge.disabled { color: #a23c32; border-color: #dfaaa5; }
.user-section { border-top: 1px solid #dce3e8; padding-top: 14px; }.metadata-list { display: grid; gap: 8px; margin: 10px 0; padding: 0; list-style: none; }.metadata-list small { display: block; margin-top: 3px; color: #637083; }.empty-inline { margin: 10px 0; color: #637083; }
.membership-form { justify-content: flex-start; }.membership-form .app-select { width: min(220px, 100%); }
.user-dialog-backdrop { position: fixed; inset: 0; z-index: 20; display: grid; place-items: center; padding: 20px; background: rgba(25, 35, 45, .38); }.user-dialog { width: min(480px, 100%); display: grid; gap: 16px; background: #fff; border: 1px solid #cdd6df; border-radius: 6px; box-shadow: 0 18px 45px rgba(25, 35, 45, .24); }.issued-token { display: block; overflow-wrap: anywhere; padding: 12px; background: #edf3f7; border: 1px solid #cdd6df; }
@media (max-width: 800px) { .user-form-grid, .user-edit-grid { grid-template-columns: 1fr; }.membership-form { flex-wrap: wrap; } }
</style>
