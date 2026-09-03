import { describe, expect, it } from 'vitest';
import { filterFindings, getFindingCounts, getSelectableGitFiles, getStatusMeta, toggleAllGitFiles, toggleGitFile } from './reviewState.js';

describe('review state helpers', () => {
  it('maps review statuses to user-facing metadata', () => {
    expect(getStatusMeta('PENDING')).toEqual({ label: '等待处理', tone: 'neutral' });
    expect(getStatusMeta('PROCESSING')).toEqual({ label: '分析中', tone: 'info' });
    expect(getStatusMeta('COMPLETED')).toEqual({ label: '已完成', tone: 'success' });
    expect(getStatusMeta('FAILED')).toEqual({ label: '失败', tone: 'danger' });
  });

  it('counts findings by severity and filters the selected severity', () => {
    const findings = [
      { severity: 'HIGH', category: 'SECURITY' },
      { severity: 'MEDIUM', category: 'ARCHITECTURE' },
      { severity: 'HIGH', category: 'SECURITY' },
      { severity: 'LOW', category: 'MAINTAINABILITY' }
    ];

    expect(getFindingCounts(findings)).toEqual({ total: 4, critical: 0, high: 2, medium: 1, low: 1 });
    expect(filterFindings(findings, 'HIGH')).toHaveLength(2);
    expect(filterFindings(findings, 'ALL')).toHaveLength(4);
  });

  it('selects only supported non-binary Git files without mutating selections', () => {
    const files = [
      { path: 'src/App.java', supported: true, binary: false },
      { path: 'image.png', supported: false, binary: true },
      { path: 'README.md', supported: true, binary: false }
    ];

    expect(getSelectableGitFiles(files).map((file) => file.path)).toEqual(['src/App.java', 'README.md']);
    expect(toggleGitFile([], 'src/App.java')).toEqual(['src/App.java']);
    expect(toggleGitFile(['src/App.java'], 'src/App.java')).toEqual([]);
    expect(toggleAllGitFiles(files, [])).toEqual(['src/App.java', 'README.md']);
    expect(toggleAllGitFiles(files, ['src/App.java', 'README.md'])).toEqual([]);
  });
});
