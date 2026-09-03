# Local Git Review Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 CodeOps Copilot 增加本机 Git 仓库扫描、变更文件选择和基于真实 Git 改动的异步代码评审，同时保留手动粘贴代码模式。

**Architecture:** Java 后端通过 `ProcessBuilder` 执行参数化 Git 命令，`GitRepositoryService` 负责仓库校验、元数据读取和选中文件重新读取，`GitDiffParser` 将 Git 输出归一化为前端可选择的变更文件。Git 评审接口将选中的变更转换为现有 `ReviewRequest`，继续复用当前规则引擎、模拟 AI 评审器、内存任务仓储和状态轮询。Vue 前端在现有工作台中加入 `Local Git`/`Manual paste` 双模式，扫描结果只作为选择依据，提交时后端再次读取仓库。

**Tech Stack:** Java 21, Spring Boot 3.5.5, Spring MVC, Jakarta Validation, JUnit 5, Mockito/Spring MockMvc, Vue 3, Vite, Vitest, Vue Test Utils, 浏览器本地联调。

**Spec:** `docs/superpowers/specs/2026-09-02-local-git-review-design.md`

## Global Constraints

- 前端和 Java 后端都运行在同一台本机；浏览器只提交本地仓库绝对路径，Git 命令由 Java 后端执行。
- 使用 Java 21、Spring Boot 3.5.5、Vue 3 和现有 Vite `/api` 代理；不引入生产 LLM、GitHub API、数据库或消息队列。
- Git 必须通过 `ProcessBuilder` 参数数组执行，禁止拼接 shell 命令字符串；所有命令都使用固定超时并采集 stdout、stderr 和退出码。
- 仓库路径必须存在、是目录并解析为 Git work tree；规范化后的文件路径不得越出仓库根目录。
- `WORKTREE` 使用 `git diff HEAD`、暂存变更和未跟踪文件；`BASE_COMMIT` 使用 `git diff <baseRef>...HEAD`，两者都必须重新校验仓库。
- 扫描和评审都限制最大文件数、单文件大小和 patch 总大小；二进制文件及不支持类型必须明确标记或跳过并返回原因。
- 评审提交必须只包含用户选择的相对路径；后端提交前重新读取 Git，不能信任扫描阶段缓存的 patch 或内容。
- 后端不可用时只保留手动粘贴模式的本地预览；Local Git 模式必须提示 Java 后端需要运行。
- 保留现有异步状态流转、finding 严重程度筛选、风险分数、Markdown 导出和手动模式行为。
- 由于现有 `ReviewRequest` 要求正数 `pullRequestNumber`，Git 评审接口为每次提交生成唯一的本地运行号（使用 `System.currentTimeMillis()`，发生冲突时递增），并在任务结果中保留该编号；前端 Local Git 模式不把它标成真实 Pull Request。
- 每个任务必须先写失败测试，确认 RED 后写最小实现，运行相关测试确认 GREEN，再提交一个可独立审查的 commit。

## 文件映射

### 后端

- Create: `backened/src/main/java/com/codeops/copilot/review/git/GitScope.java` - 定义 `WORKTREE`、`BASE_COMMIT`。
- Create: `backened/src/main/java/com/codeops/copilot/review/git/GitFileStatus.java` - 定义 modified、added、deleted、renamed、untracked、binary 状态。
- Create: `backened/src/main/java/com/codeops/copilot/review/git/GitCommandResult.java` - 封装退出码、stdout、stderr 和超时结果。
- Create: `backened/src/main/java/com/codeops/copilot/review/git/GitCommandRunner.java` - 可替换的 Git 执行边界。
- Create: `backened/src/main/java/com/codeops/copilot/review/git/ProcessBuilderGitCommandRunner.java` - 参数化进程、超时、输出读取和进程销毁。
- Create: `backened/src/main/java/com/codeops/copilot/review/git/GitChangedFile.java` - 统一的路径、状态、增删行数、patch、二进制和跳过原因模型。
- Create: `backened/src/main/java/com/codeops/copilot/review/git/GitDiffParser.java` - 解析 status/diff 输出并生成 `GitChangedFile`。
- Create: `backened/src/main/java/com/codeops/copilot/review/git/GitRepositoryService.java` - 仓库路径、Git 元数据、扫描和选中文件读取。
- Create: `backened/src/main/java/com/codeops/copilot/review/git/GitReviewException.java` - 对外安全的 400 错误信息和内部原因边界。
- Modify: `backened/src/main/java/com/codeops/copilot/review/ReviewController.java` - 增加仓库扫描和 Git 评审请求，并处理 Git 业务错误。
- Modify: `backened/src/main/java/com/codeops/copilot/review/ReviewConfiguration.java` - 注册 Git runner、parser 和 repository service Bean，保留 NIO2 配置。
- Create: `backened/src/test/java/com/codeops/copilot/review/git/GitCommandRunnerTest.java` - runner 参数、超时和退出码测试。
- Create: `backened/src/test/java/com/codeops/copilot/review/git/GitDiffParserTest.java` - 各种 Git 状态和 unified diff 测试。
- Create: `backened/src/test/java/com/codeops/copilot/review/git/GitRepositoryServiceTest.java` - 临时真实仓库、路径校验、限制和重新读取测试。
- Modify: `backened/src/test/java/com/codeops/copilot/review/ReviewControllerTest.java` - 扫描、Git 提交、校验失败和选中文件断言。

### 前端

- Modify: `frontend/src/api/reviewApi.js` - 增加 `scanRepository`、`submitGitReview` 和必要的错误转换。
- Modify: `frontend/src/reviewState.js` - 增加 Git scope、文件状态展示和选择状态纯函数；不改变既有评审统计函数。
- Modify: `frontend/src/components/ReviewForm.vue` - 实现 Local Git 与 Manual paste 双模式、文件勾选和 patch 预览。
- Create: `frontend/src/components/ReviewForm.test.js` - 验证 Git 模式渲染、选择、全选和无选择禁用提交。
- Modify: `frontend/src/App.vue` - 接入扫描、Git 提交、错误状态和现有轮询；Git 模式禁用本地预览降级。
- Modify: `frontend/src/reviewState.test.js` - 增加选择、全选、scope 校验和 API payload 相关测试。
- Create: `frontend/src/api/reviewApi.test.js` - mock `fetch` 验证扫描和 Git 评审请求。
- Create: `scripts/local-git-review-smoke.ps1` - 对运行中的本地服务执行仓库扫描、单文件提交和异步轮询断言。
- Modify: `frontend/src/styles.css` - 增加文件列表、状态徽标、patch 预览和移动端布局，不改变现有工作台视觉基调。
- Modify: `backened/README.md` - 更新本地 Git API、运行依赖和 Windows 示例。

---

### Task 1: 建立 Git 执行边界

**Files:**
- Create: `backened/src/main/java/com/codeops/copilot/review/git/GitCommandResult.java`
- Create: `backened/src/main/java/com/codeops/copilot/review/git/GitCommandRunner.java`
- Create: `backened/src/main/java/com/codeops/copilot/review/git/ProcessBuilderGitCommandRunner.java`
- Create: `backened/src/test/java/com/codeops/copilot/review/git/GitCommandRunnerTest.java`

**Interfaces:**
- Produces `GitCommandRunner.run(Path repository, Duration timeout, List<String> arguments): GitCommandResult`。
- `GitCommandResult` 至少暴露 `exitCode()`, `stdout()`, `stderr()`, `timedOut()`；成功只由 `exitCode == 0 && !timedOut` 判定。
- runner 的命令数组必须以 `git`、固定参数和调用方参数组成，repository 通过 `ProcessBuilder.directory(repository.toFile())` 设置。

- [ ] **Step 1: Write the failing test**

```java
@Test
void passesArgumentsWithoutShellInterpolation() throws Exception {
    Path repo = Files.createTempDirectory("git-runner-");
    GitCommandResult result = new ProcessBuilderGitCommandRunner()
            .run(repo, Duration.ofSeconds(2), List.of("--version"));
    assertThat(result.timedOut()).isFalse();
    assertThat(result.exitCode()).isZero();
    assertThat(result.stdout()).startsWith("git version");
}

@Test
void reportsTimeoutAndStopsTheProcess() {
    Process neverFinishes = new FakeProcessThatStaysAlive();
    GitCommandRunner runner = new ProcessBuilderGitCommandRunner(command -> neverFinishes);
    GitCommandResult result = runner.run(tempDirectory, Duration.ofMillis(1), List.of("status"));
    assertThat(result.timedOut()).isTrue();
}
```

`FakeProcessThatStaysAlive` 在测试中实现 `Process` 的最小抽象：`waitFor(long, TimeUnit)` 返回 `false`，`isAlive()` 返回 `true`，`destroyForcibly()` 将其标记为已销毁；这样超时测试不依赖操作系统命令调度。

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=GitCommandRunnerTest test` from `backened`.

Expected: FAIL because the Git runner types and implementation do not exist yet.

- [ ] **Step 3: Write minimal implementation**

Implement a production runner with `ProcessBuilder(List<String>)`, `redirectErrorStream(false)`, UTF-8 output capture, `process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)`, `destroyForcibly()` on timeout, and a bounded output reader. Do not invoke `cmd.exe`, PowerShell, or a shell. Add a package-private process factory constructor so timeout behavior can be tested without changing the public interface.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=GitCommandRunnerTest test`.

Expected: PASS, including non-zero exit code and timeout assertions.

- [ ] **Step 5: Commit**

```powershell
git add backened/src/main/java/com/codeops/copilot/review/git backened/src/test/java/com/codeops/copilot/review/git/GitCommandRunnerTest.java
git commit -m "feat: add bounded git command runner"
```

### Task 2: 解析 Git 变更模型

**Files:**
- Create: `backened/src/main/java/com/codeops/copilot/review/git/GitScope.java`
- Create: `backened/src/main/java/com/codeops/copilot/review/git/GitFileStatus.java`
- Create: `backened/src/main/java/com/codeops/copilot/review/git/GitChangedFile.java`
- Create: `backened/src/main/java/com/codeops/copilot/review/git/GitDiffParser.java`
- Create: `backened/src/test/java/com/codeops/copilot/review/git/GitDiffParserTest.java`

**Interfaces:**
- `GitDiffParser.parse(String statusPorcelain, String diffText): List<GitChangedFile>`。
- `GitChangedFile` 字段为 `path`, `status`, `additions`, `deletions`, `patch`, `binary`, `supported`, `skipReason`。
- 路径统一为 `/` 分隔的仓库相对路径；patch 保留 unified diff 上下文，未跟踪文本文件生成可供评审的 synthetic patch。

- [ ] **Step 1: Write the failing test**

```java
@Test
void parsesModifiedAddedDeletedRenamedBinaryAndUntrackedFiles() {
    String status = " M src/A.java\0A  src/New.java\0D  src/Old.java\0R  src/OldName.java\0src/NewName.java\0?? src/Notes.txt\0";
    String diff = "diff --git a/src/A.java b/src/A.java\n@@ -1,2 +1,4 @@\n-old\n+new\n+added\n"
            + "diff --git a/src/New.java b/src/New.java\n@@ -0,0 +1,2 @@\n+class New {}\n"
            + "diff --git a/src/Old.java b/src/Old.java\n@@ -1 +0,0 @@\n-old\n"
            + "diff --git a/src/OldName.java b/src/NewName.java\n@@ -1 +1 @@\n-old\n+new\n"
            + "diff --git a/image.png b/image.png\nBinary files differ\n";

    List<GitChangedFile> files = new GitDiffParser().parse(status, diff);

    assertThat(files).extracting(GitChangedFile::path)
            .containsExactly("src/A.java", "src/New.java", "src/Old.java", "src/NewName.java", "src/Notes.txt", "image.png");
    assertThat(files.get(0).additions()).isEqualTo(2);
    assertThat(files.get(0).deletions()).isEqualTo(1);
    assertThat(files.get(4).status()).isEqualTo(GitFileStatus.UNTRACKED);
    assertThat(files.get(5).binary()).isTrue();
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=GitDiffParserTest test`。

Expected: FAIL because parser/model types are absent.

- [ ] **Step 3: Write minimal implementation**

Parse NUL-delimited porcelain status first so filenames containing spaces remain intact; correlate `diff --git` sections by normalized path; count only added/deleted hunk lines, ignoring `+++` and `---` headers. Mark binary files unsupported with `skipReason = "Binary file"`; mark extensions outside the configured source set unsupported without dropping them from the scan response. Preserve rename destination as the selectable path.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=GitDiffParserTest test`。

Expected: PASS for modified, added, deleted, renamed, binary and untracked records, including empty patch sections.

- [ ] **Step 5: Commit**

```powershell
git add backened/src/main/java/com/codeops/copilot/review/git backened/src/test/java/com/codeops/copilot/review/git/GitDiffParserTest.java
git commit -m "feat: parse normalized git changes"
```

### Task 3: 仓库校验、扫描和选中文件读取

**Files:**
- Create: `backened/src/main/java/com/codeops/copilot/review/git/GitRepositoryService.java`
- Create: `backened/src/main/java/com/codeops/copilot/review/git/GitReviewException.java`
- Create: `backened/src/test/java/com/codeops/copilot/review/git/GitRepositoryServiceTest.java`

**Interfaces:**
- `GitRepositoryService.scan(Path repositoryPath, GitScope scope, String baseRef): RepositorySnapshot`。
- `GitRepositoryService.readSelected(Path repositoryPath, GitScope scope, String baseRef, List<String> selectedPaths): List<ChangedFile>`。
- `RepositorySnapshot` 返回规范化 `repositoryPath`, `branch`, `headCommit`, `List<GitChangedFile>`；选中结果的 `ChangedFile.content()` 是重新读取的 patch 或未跟踪文件文本。
- 允许的 source 类型至少包括 `.java`, `.kt`, `.xml`, `.yml`, `.yaml`, `.properties`, `.js`, `.ts`, `.vue`, `.json`, `.md`；限制值集中定义为常量，文件数、单文件字节数、patch 总字节数均可测试。

- [ ] **Step 1: Write the failing test**

```java
@Test
void scansARealRepositoryAndRejectsPathsOutsideIt() throws Exception {
    Path repo = createGitRepository("src/App.java", "class App {}\n");
    Files.writeString(repo.resolve("src/App.java"), "class App { String token = \"secret\"; }\n");

    RepositorySnapshot snapshot = service.scan(repo, GitScope.WORKTREE, null);

    assertThat(snapshot.branch()).isNotBlank();
    assertThat(snapshot.headCommit()).hasSize(40);
    assertThat(snapshot.files()).extracting(GitChangedFile::path).contains("src/App.java");
    assertThatThrownBy(() -> service.readSelected(repo, GitScope.WORKTREE, null,
            List.of("..\\outside.txt")))
            .isInstanceOf(GitReviewException.class)
            .hasMessageContaining("relative");
}

@Test
void requiresBaseRefForBaseCommitScope() {
    assertThatThrownBy(() -> service.scan(repo, GitScope.BASE_COMMIT, null))
            .isInstanceOf(GitReviewException.class)
            .hasMessageContaining("baseRef");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=GitRepositoryServiceTest test`。

Expected: FAIL because the service, snapshot and exception do not exist.

- [ ] **Step 3: Write minimal implementation**

Normalize with `Path.toAbsolutePath().normalize()`, validate `Files.isDirectory`, run `git rev-parse --show-toplevel`, and require the resolved root to equal or contain the requested directory according to Git work-tree semantics. Read branch with `git branch --show-current` and head with `git rev-parse HEAD`. Build scope-specific argument lists without shell interpolation. For `WORKTREE`, combine status, `git diff HEAD --no-ext-diff --unified=80`, and untracked file content; for `BASE_COMMIT`, use `git diff --no-ext-diff --unified=80 baseRef...HEAD`. Before returning selected content, require each requested path to be relative, present in the current scan, source-supported, non-binary and within the root. Convert Git failures and limits to safe `GitReviewException` messages.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=GitRepositoryServiceTest test`。

Expected: PASS for real temporary repositories, invalid directories, non-Git directories, missing base refs, traversal paths and size/file limits.

- [ ] **Step 5: Commit**

```powershell
git add backened/src/main/java/com/codeops/copilot/review/git backened/src/test/java/com/codeops/copilot/review/git/GitRepositoryServiceTest.java
git commit -m "feat: scan and read local git repositories"
```

### Task 4: 暴露仓库扫描与 Git 评审接口

**Files:**
- Modify: `backened/src/main/java/com/codeops/copilot/review/ReviewController.java`
- Modify: `backened/src/main/java/com/codeops/copilot/review/ReviewConfiguration.java`
- Modify: `backened/src/test/java/com/codeops/copilot/review/ReviewControllerTest.java`

**Interfaces:**
- `POST /api/repositories/scan` accepts `{repositoryPath, scope, baseRef}` and returns `{repositoryPath, branch, headCommit, files[]}`。
- `POST /api/reviews/from-git` accepts `{repositoryPath, scope, baseRef, title, files[]}` and returns the existing `ReviewTask` with HTTP `202`。
- `files[]` in the Git request contains only relative `path` strings; title is required and must be nonblank. The endpoint calls `readSelected`, maps each result to `new ChangedFile(path, content)`, then calls the existing `reviewService.submit(...)` and `processAsync(...)` exactly once.
- Before creating `ReviewRequest`, the endpoint assigns a unique positive local run number because Git requests have no Pull Request number; this number must not cause two independent local submissions to reuse a completed task.

- [ ] **Step 1: Write the failing test**

```java
@Test
void scansRepositoryAndReturnsChangedFileMetadata() throws Exception {
    when(repositoryService.scan(any(Path.class), eq(GitScope.WORKTREE), isNull()))
            .thenReturn(snapshot);
    mockMvc.perform(post("/api/repositories/scan")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"repositoryPath\":\"C:\\\\repo\",\"scope\":\"WORKTREE\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.files[0].path").value("src/App.java"));
}

@Test
void submitsOnlySelectedGitFiles() throws Exception {
    when(repositoryService.readSelected(any(Path.class), eq(GitScope.WORKTREE), isNull(), eq(List.of("src/App.java"))))
            .thenReturn(List.of(new ChangedFile("src/App.java", "+class App {}")));
    when(reviewService.submit(any(ReviewRequest.class))).thenReturn(task);

    mockMvc.perform(post("/api/reviews/from-git")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"repositoryPath\":\"C:\\\\repo\",\"scope\":\"WORKTREE\",\"title\":\"Review\",\"files\":[\"src/App.java\"]}"))
            .andExpect(status().isAccepted());
    verify(reviewService).submit(argThat(request -> request.files().size() == 1
            && request.files().get(0).path().equals("src/App.java")));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=ReviewControllerTest test`。

Expected: FAIL because the controller has no repository service or Git endpoints.

- [ ] **Step 3: Write minimal implementation**

Inject `GitRepositoryService` into `ReviewController`; add validated request records and response records. Parse `scope` into `GitScope`, reject `BASE_COMMIT` without `baseRef`, delegate all filesystem/Git work to the service, and map `GitReviewException` to HTTP `400` with only its safe message. Register `ProcessBuilderGitCommandRunner`, `GitDiffParser` and `GitRepositoryService` in `ReviewConfiguration` while preserving the existing review beans and NIO2 customizer.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test`。

Expected: PASS for old manual review tests plus scan, Git submission, invalid payload and Git error tests.

- [ ] **Step 5: Commit**

```powershell
git add backened/src/main/java/com/codeops/copilot/review/ReviewController.java backened/src/main/java/com/codeops/copilot/review/ReviewConfiguration.java backened/src/test/java/com/codeops/copilot/review/ReviewControllerTest.java
git commit -m "feat: expose local git review endpoints"
```

### Task 5: 增加前端 Git API 和状态纯函数

**Files:**
- Modify: `frontend/src/api/reviewApi.js`
- Create: `frontend/src/api/reviewApi.test.js`
- Modify: `frontend/src/reviewState.js`
- Modify: `frontend/src/reviewState.test.js`

**Interfaces:**
- `scanRepository({ repositoryPath, scope, baseRef })` 调用 `POST /api/repositories/scan`。
- `submitGitReview({ repositoryPath, scope, baseRef, title, files })` 调用 `POST /api/reviews/from-git`，其中 `files` 是相对路径字符串数组。
- `getSelectableGitFiles(files)` 过滤 `binary === false && supported !== false`；`toggleAllGitFiles(files, selected)` 和 `toggleGitFile(selected, path)` 返回新数组，不修改输入。

- [ ] **Step 1: Write the failing test**

```js
it('sends local git scan and selected paths with the expected endpoints', async () => {
  global.fetch = vi.fn()
    .mockResolvedValueOnce(new Response(JSON.stringify({ files: [] }), { status: 200 }))
    .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'task-1' }), { status: 202 }));
  await scanRepository({ repositoryPath: 'C:/repo', scope: 'WORKTREE', baseRef: null });
  await submitGitReview({ repositoryPath: 'C:/repo', scope: 'WORKTREE', baseRef: null, title: 'Review', files: ['src/App.java'] });
  expect(fetch.mock.calls[0][0]).toBe('/api/repositories/scan');
  expect(JSON.parse(fetch.mock.calls[1][1].body).files).toEqual(['src/App.java']);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- --run frontend/src/api/reviewApi.test.js` from `frontend`。

Expected: FAIL because the Git API functions and test file are absent.

- [ ] **Step 3: Write minimal implementation**

Reuse the existing `request` helper and its timeout/error handling; add the two functions without duplicating fetch logic. Keep the existing manual `submitReview`, `getReview`, status metadata, counters, risk score and demo task unchanged. Add pure selection helpers with stable path identity and no mutation.

- [ ] **Step 4: Run test to verify it passes**

Run: `npm test` from `frontend`。

Expected: PASS for API payloads and all existing review state tests.

- [ ] **Step 5: Commit**

```powershell
git add frontend/src/api/reviewApi.js frontend/src/api/reviewApi.test.js frontend/src/reviewState.js frontend/src/reviewState.test.js
git commit -m "feat: add local git review client state"
```

### Task 6: 完成本地 Git 前端工作流

**Files:**
- Modify: `frontend/src/components/ReviewForm.vue`
- Create: `frontend/src/components/ReviewForm.test.js`
- Modify: `frontend/src/App.vue`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- `ReviewForm` emits `submit` with either the existing manual payload or `{ mode: 'git', repositoryPath, scope, baseRef, title, files }`。
- `ReviewForm` emits `scan` with `{ repositoryPath, scope, baseRef }` and accepts `scanResult`, `scanning`, `scanError`, `submitting` props.
- App handles `scan` through `scanRepository`, stores the authoritative `scanResult`, and handles Git submit through `submitGitReview`; only manual submit may call `makeLocalPreview` on request failure.

- [ ] **Step 1: Write the failing test**

```js
it('keeps git submission disabled until one supported file is selected', async () => {
  const wrapper = mount(ReviewForm, {
    props: { submitting: false, scanResult: {
      files: [{ path: 'src/App.java', status: 'MODIFIED', additions: 2, deletions: 1, patch: '@@', supported: true }]
    }}
  });
  await wrapper.find('[data-testid="mode-git"]').trigger('click');
  expect(wrapper.find('[data-testid="git-submit"]').element.disabled).toBe(true);
  await wrapper.find('[data-testid="file-checkbox-src-App-java"]').setValue(true);
  expect(wrapper.find('[data-testid="git-submit"]').element.disabled).toBe(false);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- --run frontend/src/components/ReviewForm.test.js` from `frontend`。

Expected: FAIL because the component has no Git mode, scan result props or test selectors yet.

- [ ] **Step 3: Write minimal implementation**

Add a clear two-mode segmented control with `data-testid="mode-git"` and `data-testid="mode-manual"`. Local Git mode contains an absolute path input, `WORKTREE`/`BASE_COMMIT` selector, conditional base ref input, scan button, empty/error/loading states, checkbox rows with status and `+additions/-deletions`, select-all/clear-all controls, and a patch preview for the current selected file. Keep the manual form fields and demo action available. Disable Git submission when no supported file is selected; emit only selected relative paths.

In `App.vue`, add `scanRepository` and `submitGitReview` calls, clear stale selections after a new scan, preserve the path/title on errors, and display a backend-required message for Git mode failures. Reuse current task polling and result components. Update CSS with fixed row spacing, readable monospace patch area, keyboard-visible focus states, and a mobile layout where metadata wraps below the path without overlap.

- [ ] **Step 4: Run test to verify it passes**

Run: `npm test` and `npm run build` from `frontend`。

Expected: PASS and successful Vite production build; manual paste tests and local preview behavior remain green.

- [ ] **Step 5: Commit**

```powershell
git add frontend/src/components/ReviewForm.vue frontend/src/App.vue frontend/src/styles.css frontend/src/components/ReviewForm.test.js
git commit -m "feat: add local git selection workflow"
```

### Task 7: 本地真实仓库联调、回归和文档

**Files:**
- Modify: `backened/README.md`
- Create: `scripts/local-git-review-smoke.ps1`
- Modify: `frontend/src/components/ReviewForm.vue` only if browser verification exposes a layout defect.
- Modify: `frontend/src/App.vue` only if browser verification exposes an integration defect.

**Interfaces:**
- Verification uses the running Java server at `http://127.0.0.1:8080` and Vite server at `http://127.0.0.1:5173`; do not start duplicate processes when those ports are already listening.
- The real repository for the smoke test is `D:/development/project/my_learn`; the test must select at least one Java or frontend source file and observe a task id followed by `COMPLETED` or a reported `FAILED` state.

- [ ] **Step 1: Write the failing smoke test**

Create `scripts/local-git-review-smoke.ps1` with strict error handling. It must call the running API using `Invoke-RestMethod`, select the first returned file whose `supported` is not false and `binary` is not true, submit exactly that path, poll at 200 ms intervals for at most 10 seconds, and throw unless the terminal status is `COMPLETED` or `FAILED`. The script must assert the scan is HTTP-successful, the response contains `files`, the selected path is present in the scan, and the returned task contains exactly one selected file.

```text
1. POST /api/repositories/scan with repositoryPath=D:/development/project/my_learn and scope=WORKTREE.
2. Assert HTTP 200 and inspect files[].path/status/additions/deletions/patch.
3. POST /api/reviews/from-git with one returned supported path.
4. Assert HTTP 202, poll GET /api/reviews/{id}, and assert terminal status.
5. Confirm a second selected path is absent when only one path was submitted.
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`, `npm test`, and `npm run build`; then run `pwsh -File scripts/local-git-review-smoke.ps1` against the local services.

Expected: any remaining compile, payload, path, polling or layout mismatch is visible before documentation is finalized.

- [ ] **Step 3: Write the documented integration result**

Correct only the failing assertion or integration behavior identified by the unit tests, build, smoke script, or browser check. Update `backened/README.md` with Java 21/Maven requirements, the two endpoint request examples, Windows path escaping, the need to run Git on the backend machine, supported scope values, and the fact that the current engine is deterministic rules plus `SimulatedAiReviewer` rather than a production LLM. Do not alter unrelated existing NIO2 changes.

- [ ] **Step 4: Run test to verify it passes**

Run all of the following:

```powershell
Set-Location backened
mvn -q test
Set-Location ..\frontend
npm test
npm run build
```

Run `pwsh -File scripts/local-git-review-smoke.ps1`, then use the browser at `http://127.0.0.1:5173/#review` to verify desktop and narrow mobile widths, scan success, no-change empty state, selection/patch preview, scan error, Git submit/polling, manual paste submit, and Markdown export. Expected: backend and frontend suites pass, the production bundle builds, the smoke script exits successfully, and no text or controls overlap in either viewport.

- [ ] **Step 5: Commit**

```powershell
git add backened/README.md
git commit -m "docs: document local git review workflow"
```

## Self-Review Checklist

- Spec coverage: Tasks 1-3 cover ProcessBuilder safety, timeout, repository validation, path traversal, metadata, diff parsing, untracked/binary files and limits; Task 4 covers both backend endpoints and selected-file re-read; Tasks 5-6 cover payloads, dual-mode UI, selection, preview, error states and manual fallback; Task 7 covers real local integration, browser verification and documentation.
- Placeholder scan: no unresolved design placeholders are left in the plan; every step names a file, command, expected result and concrete implementation behavior. Task 7 uses an explicit PowerShell smoke script rather than an informal checklist.
- Type consistency: `GitScope`, `GitChangedFile`, `GitCommandRunner`, `RepositorySnapshot`, `scanRepository`, `submitGitReview`, and the `ReviewForm` event payloads are introduced before they are consumed by later tasks.
- Existing changes: the current `ReviewConfiguration.java` NIO2 customizer and `ReviewConfigurationTest.java` remain part of the baseline and must not be reverted.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-09-02-local-git-review.md`. Two execution options:

1. **Subagent-Driven (recommended)** - dispatch a fresh subagent per task and review between tasks。
2. **Inline Execution** - execute the tasks in this session with executing-plans checkpoints。

选择一种执行方式后，再开始真正修改 Java 后端和 Vue 前端代码。
