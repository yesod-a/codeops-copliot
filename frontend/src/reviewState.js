export function getStatusMeta(status) {
  const metadata = {
    PENDING: { label: '等待处理', tone: 'neutral' },
    PROCESSING: { label: '分析中', tone: 'info' },
    COMPLETED: { label: '已完成', tone: 'success' },
    FAILED: { label: '失败', tone: 'danger' }
  };

  return metadata[status] ?? { label: '未知状态', tone: 'neutral' };
}

export function getFindingCounts(findings = []) {
  return findings.reduce((counts, finding) => {
    counts.total += 1;
    if (finding.severity === 'CRITICAL') counts.critical += 1;
    if (finding.severity === 'HIGH') counts.high += 1;
    if (finding.severity === 'MEDIUM') counts.medium += 1;
    if (finding.severity === 'LOW') counts.low += 1;
    return counts;
  }, { total: 0, critical: 0, high: 0, medium: 0, low: 0 });
}

export function filterFindings(findings = [], severity = 'ALL') {
  if (severity === 'ALL') return findings;
  return findings.filter((finding) => finding.severity === severity);
}

export function getSelectableGitFiles(files = []) {
  return files.filter((file) => file.binary !== true && file.supported !== false);
}

export function toggleGitFile(selected = [], path) {
  return selected.includes(path)
    ? selected.filter((item) => item !== path)
    : [...selected, path];
}

export function toggleAllGitFiles(files = [], selected = []) {
  const selectablePaths = getSelectableGitFiles(files).map((file) => file.path);
  const hasAll = selectablePaths.length > 0 && selectablePaths.every((path) => selected.includes(path));
  return hasAll ? [] : selectablePaths;
}

export function calculateRiskScore(findings = []) {
  const penalty = findings.reduce((total, finding) => {
    const values = { CRITICAL: 26, HIGH: 15, MEDIUM: 8, LOW: 3 };
    return total + (values[finding.severity] ?? 0);
  }, 0);
  return Math.max(0, Math.min(100, 100 - penalty));
}

const categoryLabels = {
  SECURITY: '安全性',
  ARCHITECTURE: '架构设计',
  MAINTAINABILITY: '可维护性',
  PERFORMANCE: '性能',
  CORRECTNESS: '正确性'
};

export function getCategoryLabel(category) {
  return categoryLabels[category] ?? '其他';
}

const gitStatusLabels = {
  MODIFIED: '已修改',
  ADDED: '已新增',
  DELETED: '已删除',
  RENAMED: '已重命名',
  UNTRACKED: '未跟踪'
};

export function getGitStatusLabel(status) {
  return gitStatusLabels[status] ?? '其他';
}

const findingTextLabels = {
  'Sensitive data may be read or assigned directly in application code.': '代码中可能直接读取或赋值敏感数据。',
  'Move secrets to a managed secret store and avoid logging or copying credentials.': '请将密钥交给专用密钥管理服务，避免记录或复制凭据。',
  'The change contains an unresolved TODO or FIXME marker.': '变更中包含未完成的 TODO 或 FIXME 标记。',
  'Create a tracked issue or finish the implementation before merging.': '请在合并前完成实现，或创建可追踪的问题单。',
  'The controller appears to depend directly on a repository.': '控制器似乎直接依赖数据仓库。',
  'Move data access behind an application service to keep the HTTP layer focused on transport concerns.': '请将数据访问放到应用服务之后，让 HTTP 层专注于传输职责。',
  'The controller reads infrastructure headers directly instead of using a dedicated authentication abstraction.': '控制器直接读取基础设施请求头，未使用专用的认证抽象。',
  'Resolve the authenticated principal through Spring Security and keep request parsing separate from business logic.': '请通过 Spring Security 获取认证主体，并将请求解析与业务逻辑分离。'
};

export function getFindingText(text) {
  return findingTextLabels[text] ?? text;
}

export const severityMeta = {
  CRITICAL: { label: '严重', className: 'severity-critical' },
  HIGH: { label: '高风险', className: 'severity-high' },
  MEDIUM: { label: '中风险', className: 'severity-medium' },
  LOW: { label: '低风险', className: 'severity-low' }
};

export const demoTask = {
  id: 'demo-review-42',
  repository: 'acme/order-service',
  pullRequestNumber: 42,
  title: 'Add payment endpoint',
  status: 'COMPLETED',
  createdAt: '2026-09-02T10:20:00Z',
  updatedAt: '2026-09-02T10:21:14Z',
  findings: [
    {
      category: 'SECURITY',
      severity: 'HIGH',
      file: 'src/main/java/com/acme/order/PaymentController.java',
      line: 28,
      message: 'Sensitive authorization data is read directly in the controller.',
      suggestion: 'Resolve the authenticated principal through Spring Security and keep raw credentials out of application logic.',
      evidence: 'String token = request.getHeader("Authorization");',
      confidence: 0.98
    },
    {
      category: 'ARCHITECTURE',
      severity: 'MEDIUM',
      file: 'src/main/java/com/acme/order/PaymentController.java',
      line: 41,
      message: 'The controller appears to depend directly on a repository.',
      suggestion: 'Move data access behind an application service to keep the HTTP layer focused on transport concerns.',
      evidence: 'private final PaymentRepository paymentRepository;',
      confidence: 0.84
    },
    {
      category: 'MAINTAINABILITY',
      severity: 'LOW',
      file: 'src/main/java/com/acme/order/PaymentService.java',
      line: 67,
      message: 'The change contains an unresolved TODO marker.',
      suggestion: 'Create a tracked issue or finish the implementation before merging.',
      evidence: '// TODO add idempotency validation',
      confidence: 0.95
    }
  ]
};
