# Java Local Client Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a Java 21, localhost-only CodeOps Local Client that reads Git state safely, resolves central projects by remote URL, submits reviews, and replaces the PowerShell Hook's direct central-service calls.

**Architecture:** `local-client/` is a standalone Maven module with a restricted `ServerSocket` loopback HTTP server, Jackson JSON handling, blocking `HttpURLConnection` central calls, and fixed-argument Git invocation. It stores non-secret configuration in `%LOCALAPPDATA%\CodeOps`, encrypts the Agent Token with current-user DPAPI, and treats the central service as the policy and authorization authority. The PowerShell Hook becomes a thin HTTP adapter; WinSW service generation is optional at install time because no wrapper executable is committed in this repository.

**Tech Stack:** Java 21, Maven, JUnit 5, Jackson, JDK `ServerSocket` and `HttpURLConnection`, PowerShell 5+, Git, Windows DPAPI, WinSW release bundle.

**Spec:** `docs/superpowers/specs/2026-09-05-java-local-client-design.md`

## Global Constraints

- Bind the Local Client only to `127.0.0.1`; never create a wildcard listener or firewall rule.
- Never store, print, send, or pass an Agent Token in a project directory, environment variable, JSON response, or process argument.
- Run Git exclusively through `ProcessBuilder(List<String>)` with an allowlisted command shape and discovered Git root as working directory.
- Central project identity is the normalized remote URL/repository key; absolute local paths must never reach central API payloads.
- Resolve remote repository access with the central service for every review. Cache is never an authorization source.
- Preserve legacy scripts until the Java Client and Hook path are verified. Do not delete unrelated, pre-existing worktree changes.
- Do not create a Git commit unless the user explicitly requests one.

---

### Task 1: Create the Standalone Maven Client and Configuration Boundary

**Files:**
- Create: `local-client/pom.xml`
- Create: `local-client/src/main/java/com/codeops/client/CodeOpsClientApplication.java`
- Create: `local-client/src/main/java/com/codeops/client/config/ClientPaths.java`
- Create: `local-client/src/main/java/com/codeops/client/config/ClientSettings.java`
- Create: `local-client/src/main/java/com/codeops/client/config/ServerUrl.java`
- Create: `local-client/src/main/java/com/codeops/client/config/ClientSettingsStore.java`
- Create: `local-client/src/main/java/com/codeops/client/config/JsonClientSettingsStore.java`
- Test: `local-client/src/test/java/com/codeops/client/config/JsonClientSettingsStoreTest.java`

**Interfaces:**
- Consumes: `LOCALAPPDATA`, a per-user directory supplied by `ClientPaths` in tests, and `client.json` non-secret configuration.
- Produces: `ClientSettings(serverUrl, listenAddress, listenPort, requestTimeoutSeconds, failOpen)` and an executable `codeops-client.jar` with `CodeOpsClientApplication` as its main class.

- [ ] **Step 1: Write a failing settings-store test for a normalized valid configuration**

```java
@Test
void savesAndLoadsNonSecretSettingsWithANormalizedServerUrl(@TempDir Path home) throws Exception {
    var store = new JsonClientSettingsStore(new ClientPaths(home));
    var settings = new ClientSettings("https://codeops.example.com/", "127.0.0.1", 49152, 600, false);

    store.save(settings);

    assertThat(store.load()).contains(new ClientSettings(
            "https://codeops.example.com", "127.0.0.1", 49152, 600, false));
    assertThat(Files.readString(home.resolve("client.json"))).doesNotContain("token");
}
```

- [ ] **Step 2: Run the focused test and observe the missing module/class failure**

Run: `Set-Location local-client; mvn test -Dtest=JsonClientSettingsStoreTest`

Expected: Maven fails because `local-client/pom.xml` or the settings classes do not exist.

- [ ] **Step 3: Add the Maven module and settings types**

Create `pom.xml` with Java 21 compiler release, `jackson-databind`, JUnit Jupiter, AssertJ, Surefire, and Maven Shade Plugin. Configure Shade Plugin manifest main class `com.codeops.client.CodeOpsClientApplication` and final artifact `codeops-client.jar`.

```java
public record ClientSettings(String serverUrl, String listenAddress, int listenPort,
                             int requestTimeoutSeconds, boolean failOpen) {
    public ClientSettings {
        serverUrl = ServerUrl.normalize(serverUrl);
        if (!"127.0.0.1".equals(listenAddress)) throw new IllegalArgumentException("listenAddress must be 127.0.0.1");
        if (listenPort < 1 || listenPort > 65535) throw new IllegalArgumentException("listenPort must be between 1 and 65535");
        if (requestTimeoutSeconds < 1 || requestTimeoutSeconds > 3600) throw new IllegalArgumentException("requestTimeoutSeconds must be between 1 and 3600");
    }
}
```

`ClientPaths.defaultPaths()` must resolve `%LOCALAPPDATA%\CodeOps`, create it only during a write, and expose `settingsFile()`, `credentialFile()`, `repositoryCacheDirectory()`, and `serviceDirectory()`.

`JsonClientSettingsStore.save` must atomically write a JSON object containing only the record fields. `load` must return `Optional.empty()` if the settings file does not exist and reject unknown JSON properties.

`CodeOpsClientApplication.main` initially prints a usage message and returns exit `2` for missing or unsupported commands; later tasks attach commands.

- [ ] **Step 4: Re-run the focused settings test**

Run: `Set-Location local-client; mvn test -Dtest=JsonClientSettingsStoreTest`

Expected: PASS.

- [ ] **Step 5: Write and run invalid-settings tests before extending validation**

```java
@ParameterizedTest
@ValueSource(strings = {"ftp://codeops.example.com", "https://codeops.example.com/path?x=1", "not-a-url"})
void rejectsInvalidServerUrls(String serverUrl) {
    assertThatThrownBy(() -> new ClientSettings(serverUrl, "127.0.0.1", 49152, 600, false))
            .isInstanceOf(IllegalArgumentException.class);
}
```

Run: `Set-Location local-client; mvn test -Dtest=JsonClientSettingsStoreTest`

Expected before validation implementation: FAIL; after adding `ServerUrl.normalize`, PASS.

---

### Task 2: Add DPAPI Credential Storage and CLI Login Safeguards

**Files:**
- Create: `local-client/src/main/java/com/codeops/client/credential/CredentialStore.java`
- Create: `local-client/src/main/java/com/codeops/client/credential/DpapiCredentialStore.java`
- Create: `local-client/src/main/java/com/codeops/client/credential/PowerShellDpapi.java`
- Create: `local-client/src/main/java/com/codeops/client/cli/LoginCommand.java`
- Modify: `local-client/src/main/java/com/codeops/client/CodeOpsClientApplication.java`
- Test: `local-client/src/test/java/com/codeops/client/cli/LoginCommandTest.java`
- Test: `local-client/src/test/java/com/codeops/client/credential/DpapiCredentialStoreTest.java`

**Interfaces:**
- Consumes: a server URL flag and exactly one Agent Token line from standard input.
- Produces: machine-level non-secret settings plus an encrypted `credentials.dat`; `CredentialStore.loadToken()` returns the token only to in-process client code.

- [ ] **Step 1: Write a failing login command test using a fake credential store**

```java
@Test
void loginStoresTheTokenWithoutWritingItToClientSettings(@TempDir Path home) throws Exception {
    var settingsStore = new JsonClientSettingsStore(new ClientPaths(home));
    var credentials = new RecordingCredentialStore();
    int exit = new LoginCommand(settingsStore, credentials,
            new ByteArrayInputStream("cop_secret\n".getBytes(UTF_8)), new PrintWriter(output)).run(
            List.of("--server-url", "https://codeops.example.com", "--token-stdin"));

    assertThat(exit).isZero();
    assertThat(credentials.token()).isEqualTo("cop_secret");
    assertThat(Files.readString(home.resolve("client.json"))).doesNotContain("cop_secret");
}
```

- [ ] **Step 2: Run the focused test and observe the missing command failure**

Run: `Set-Location local-client; mvn test -Dtest=LoginCommandTest`

Expected: FAIL because `LoginCommand` and credential abstraction are absent.

- [ ] **Step 3: Implement the credential boundary and login command**

```java
public interface CredentialStore {
    void saveToken(String token) throws IOException;
    Optional<String> loadToken() throws IOException;
    boolean isAvailable();
}
```

`LoginCommand` must require exact arguments `--server-url <value> --token-stdin`, reject empty or additional token lines, save `ClientSettings` with default loopback values, and call `CredentialStore.saveToken`. It must output a success message with no token content.

`DpapiCredentialStore` must fail closed on non-Windows systems. On Windows, `PowerShellDpapi` starts only `powershell.exe` with a fixed script passed through `-Command`; the token travels over redirected stdin. The script uses `[System.Security.Cryptography.ProtectedData]::Protect` and `::Unprotect` with `DataProtectionScope::CurrentUser`. The encrypted Base64 file is written atomically to `ClientPaths.credentialFile()`.

In `DpapiCredentialStoreTest`, define `UnsupportedPowerShellDpapi` as a test-local implementation of the package-private DPAPI process boundary that throws `UnsupportedOperationException("Windows DPAPI is unavailable")`; production code must not contain a test-only credential implementation.

- [ ] **Step 4: Re-run the login test**

Run: `Set-Location local-client; mvn test -Dtest=LoginCommandTest`

Expected: PASS.

- [ ] **Step 5: Add credential failure tests and run them**

```java
@Test
void refusesToSaveATokenWhenCurrentUserDpapiIsUnavailable(@TempDir Path home) {
    var store = new DpapiCredentialStore(new ClientPaths(home), new UnsupportedPowerShellDpapi());

    assertThatThrownBy(() -> store.saveToken("cop_secret"))
            .hasMessageContaining("Windows DPAPI");
}
```

Run: `Set-Location local-client; mvn test -Dtest=DpapiCredentialStoreTest,LoginCommandTest`

Expected: PASS; the test must use an injected DPAPI process boundary and must not require a real user credential.

---

### Task 3: Implement Safe Local Git Repository Discovery and Change Collection

**Files:**
- Create: `local-client/src/main/java/com/codeops/client/git/GitCommandRunner.java`
- Create: `local-client/src/main/java/com/codeops/client/git/LocalRepository.java`
- Create: `local-client/src/main/java/com/codeops/client/git/RepositoryDiscovery.java`
- Create: `local-client/src/main/java/com/codeops/client/git/ChangedFile.java`
- Create: `local-client/src/main/java/com/codeops/client/git/PrePushUpdate.java`
- Create: `local-client/src/main/java/com/codeops/client/git/ChangeCollector.java`
- Test: `local-client/src/test/java/com/codeops/client/git/RepositoryDiscoveryTest.java`
- Test: `local-client/src/test/java/com/codeops/client/git/ChangeCollectorTest.java`

**Interfaces:**
- Consumes: a user-supplied filesystem path, an origin name, and parsed four-field Git pre-push updates.
- Produces: `LocalRepository(root, remoteUrl, branch, headCommit)` and `ChangedFile(relativePath, gitStatus, additions, deletions, patch)` with no absolute file paths.

- [ ] **Step 1: Write a failing real-Git repository discovery test**

```java
@Test
void resolvesGitRootAndOriginFromANestedDirectory(@TempDir Path repository) throws Exception {
    initRepository(repository, "https://git.example.com/acme/order-service.git");
    Path nested = Files.createDirectories(repository.resolve("src/main"));

    LocalRepository found = new RepositoryDiscovery(new GitCommandRunner()).discover(nested, "origin");

    assertThat(found.root()).isEqualTo(repository.toRealPath());
    assertThat(found.remoteUrl()).isEqualTo("https://git.example.com/acme/order-service.git");
}
```

- [ ] **Step 2: Run focused tests and observe the missing implementation**

Run: `Set-Location local-client; mvn test -Dtest=RepositoryDiscoveryTest,ChangeCollectorTest`

Expected: FAIL because discovery and Git runner classes do not exist.

- [ ] **Step 3: Implement fixed-argument Git invocation and repository discovery**

```java
public GitResult run(Path workingDirectory, List<String> arguments) throws IOException, InterruptedException {
    var command = new ArrayList<String>();
    command.add("git");
    command.addAll(arguments);
    Process process = new ProcessBuilder(command).directory(workingDirectory.toFile()).start();
    // capture UTF-8 stdout/stderr, enforce timeout, and return exit code
}
```

`RepositoryDiscovery` must check the requested path exists, run `git rev-parse --show-toplevel`, canonicalize the reported root with `toRealPath()`, then run `git remote get-url <remoteName>`, `git branch --show-current`, and `git rev-parse HEAD` from that root. `remoteName` must match `[A-Za-z0-9._/-]+` and must not contain whitespace, `..`, or a leading dash.

- [ ] **Step 4: Re-run the discovery test**

Run: `Set-Location local-client; mvn test -Dtest=RepositoryDiscoveryTest`

Expected: PASS.

- [ ] **Step 5: Write failing path escape and malformed update tests**

```java
@Test
void rejectsAChangedPathThatEscapesTheRepositoryRoot(@TempDir Path root) {
    assertThatThrownBy(() -> ChangedFile.requireRelativePath(root, "../secrets.txt"))
            .hasMessageContaining("must stay inside the repository");
}

@Test
void rejectsAPrePushLineWithoutExactlyFourFields() {
    assertThatThrownBy(() -> PrePushUpdate.parse("refs/heads/main abc"))
            .hasMessageContaining("four fields");
}
```

- [ ] **Step 6: Implement change collection and rerun tests**

`PrePushUpdate.parse` splits on whitespace into `localRef`, `localSha`, `remoteRef`, and `remoteSha`; a zero local SHA marks a deletion. `ChangeCollector` uses only the Git command shapes documented in the spec, omits binary/unreviewable extensions, checks each relative path with `root.resolve(path).normalize().startsWith(root)`, and truncates patches at a named constant. It must not read files directly from caller-specified absolute paths.

Run: `Set-Location local-client; mvn test -Dtest=RepositoryDiscoveryTest,ChangeCollectorTest`

Expected: PASS.

---

### Task 4: Add the Central API Client and Review Workflow

**Files:**
- Create: `local-client/src/main/java/com/codeops/client/central/CentralApiClient.java`
- Create: `local-client/src/main/java/com/codeops/client/central/HttpCentralApiClient.java`
- Create: `local-client/src/main/java/com/codeops/client/central/ProjectResolution.java`
- Create: `local-client/src/main/java/com/codeops/client/central/CentralReviewRequest.java`
- Create: `local-client/src/main/java/com/codeops/client/central/CentralReviewResponse.java`
- Create: `local-client/src/main/java/com/codeops/client/review/ReviewWorkflow.java`
- Create: `local-client/src/main/java/com/codeops/client/review/ReviewResult.java`
- Test: `local-client/src/test/java/com/codeops/client/central/HttpCentralApiClientTest.java`
- Test: `local-client/src/test/java/com/codeops/client/review/ReviewWorkflowTest.java`

**Interfaces:**
- Consumes: discovered remote URL and Git changes, `ClientSettings`, and Agent Token supplied only by `CredentialStore`.
- Produces: central project resolution and `ReviewResult(blocked, message, findings)` based on the central response.

- [ ] **Step 1: Write a failing central resolution contract test with an in-process HTTP server**

```java
@Test
void postsOnlyRemoteUrlAndBearerTokenToProjectResolution() throws Exception {
    stub.enqueueJson(200, "{\"projectId\":3,\"projectName\":\"Order Service\",\"repositoryKey\":\"git.example.com/acme/order-service\",\"role\":\"REVIEWER\",\"reviewEnabled\":true,\"prePushEnabled\":true,\"failOnSeverity\":\"HIGH\"}");

    ProjectResolution resolution = client.resolveProject("cop_token", "https://git.example.com/acme/order-service.git");

    assertThat(resolution.projectId()).isEqualTo(3L);
    assertThat(stub.lastBody()).isEqualTo("{\"remoteUrl\":\"https://git.example.com/acme/order-service.git\"}");
    assertThat(stub.lastBody()).doesNotContain("D:\\\\");
}
```

- [ ] **Step 2: Run central client tests and observe the missing implementation**

Run: `Set-Location local-client; mvn test -Dtest=HttpCentralApiClientTest,ReviewWorkflowTest`

Expected: FAIL because central client and workflow classes do not exist.

- [ ] **Step 3: Implement HTTP central client contracts**

```java
public interface CentralApiClient {
    ProjectResolution resolveProject(String token, String remoteUrl) throws IOException, InterruptedException;
    CentralReviewResponse submitReview(String token, CentralReviewRequest request) throws IOException, InterruptedException;
}
```

Use blocking `HttpURLConnection` with configured connect and read timeouts. `resolveProject` posts exactly `{ "remoteUrl": remoteUrl }` to `/api/client/projects/resolve`. `submitReview` posts the central Agent Review contract to `/api/agent/reviews`. Both add the Bearer header in memory only, reject non-2xx responses with a typed exception containing status but no token or request body, and configure Jackson to fail on unknown response fields.

- [ ] **Step 4: Re-run the central client contract test**

Run: `Set-Location local-client; mvn test -Dtest=HttpCentralApiClientTest`

Expected: PASS.

- [ ] **Step 5: Write failing workflow tests for unregistered and blocked paths**

```java
@Test
void skipsAnUnregisteredRepositoryWithoutSubmittingAReview() throws Exception {
    when(central.resolveProject("cop_token", repository.remoteUrl())).thenReturn(ProjectResolution.unregistered());

    ReviewResult result = workflow.reviewPrePush(repository, List.of(update));

    assertThat(result.blocked()).isFalse();
    assertThat(result.message()).contains("not registered");
    verify(central, never()).submitReview(anyString(), any());
}

@Test
void returnsBlockedWhenTheCentralReviewBlocksThePush() throws Exception {
    when(central.submitReview(anyString(), any())).thenReturn(new CentralReviewResponse(true, "Policy blocked", List.of()));

    assertThat(workflow.reviewPrePush(repository, List.of(update)).blocked()).isTrue();
}
```

- [ ] **Step 6: Implement review workflow and rerun tests**

`ReviewWorkflow` loads the token through `CredentialStore`, resolves the remote on every operation, skips unregistered or disabled-trigger projects, gathers changes for each non-deletion update, and submits `{projectId, repositoryKey, trigger, title, branch, headCommit, baseRef, files}`. It propagates `blocked` directly from the central response. It returns fail-open only for transient client errors when the request specifies it; authorization failures remain failed operations.

Run: `Set-Location local-client; mvn test -Dtest=HttpCentralApiClientTest,ReviewWorkflowTest`

Expected: PASS.

---

### Task 5: Serve the Restricted Loopback API and Add CLI Status/Doctor

**Files:**
- Create: `local-client/src/main/java/com/codeops/client/http/CodeOpsHttpServer.java`
- Create: `local-client/src/main/java/com/codeops/client/http/JsonHttpHandler.java`
- Create: `local-client/src/main/java/com/codeops/client/http/RepositoryResolveRequest.java`
- Create: `local-client/src/main/java/com/codeops/client/http/ReviewRequest.java`
- Create: `local-client/src/main/java/com/codeops/client/cli/StatusCommand.java`
- Create: `local-client/src/main/java/com/codeops/client/cli/DoctorCommand.java`
- Modify: `local-client/src/main/java/com/codeops/client/CodeOpsClientApplication.java`
- Test: `local-client/src/test/java/com/codeops/client/http/CodeOpsHttpServerTest.java`
- Test: `local-client/src/test/java/com/codeops/client/cli/StatusCommandTest.java`
- Test: `local-client/src/test/java/com/codeops/client/cli/DoctorCommandTest.java`

**Interfaces:**
- Consumes: validated JSON requests from loopback callers and initialized workflow/configuration services.
- Produces: `/health`, `/v1/status`, repository resolution, and review responses with no secrets or absolute local paths in central payloads.

- [ ] **Step 1: Write failing HTTP server tests**

```java
@Test
void healthEndpointReportsUpOnAnEphemeralLoopbackPort() throws Exception {
    try (var server = fixture.start(0)) {
        HttpResponse<String> response = get(server.baseUri().resolve("/health"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"status\":\"UP\"}");
    }
}

@Test
void resolveEndpointRejectsUnknownJsonFields() throws Exception {
    try (var server = fixture.start(0)) {
        assertThat(post(server, "/v1/repositories/resolve", "{\"repositoryPath\":\"C:\\\\repo\",\"unexpected\":true}").statusCode())
                .isEqualTo(400);
    }
}
```

- [ ] **Step 2: Run HTTP tests and observe the missing server failure**

Run: `Set-Location local-client; mvn test -Dtest=CodeOpsHttpServerTest`

Expected: FAIL because the loopback server and handlers are absent.

- [ ] **Step 3: Implement the loopback-only server**

Bind a `ServerSocket` with `InetAddress.getByName("127.0.0.1")`, reject non-loopback socket peers, permit only documented methods and paths, set `Content-Type: application/json; charset=utf-8`, cap header and body size, and serialize typed error envelopes without stack traces.

`/v1/repositories/resolve` validates `repositoryPath` and optional `remoteName`, discovers the repository, calls central resolution, and responds with only the central resolution fields. `/v1/reviews/pre-push`, `/v1/reviews/pre-commit`, and `/v1/reviews/post-merge` validate the trigger payload and delegate to `ReviewWorkflow` with trigger-specific fail-open behavior.

- [ ] **Step 4: Re-run HTTP tests**

Run: `Set-Location local-client; mvn test -Dtest=CodeOpsHttpServerTest`

Expected: PASS.

- [ ] **Step 5: Write failing status and doctor command tests**

```java
@Test
void statusReportsCredentialPresenceWithoutPrintingTheToken() throws Exception {
    String text = executeStatus(configuredSettings, credentialStoreContaining("cop_secret"));

    assertThat(text).contains("credential: present");
    assertThat(text).doesNotContain("cop_secret");
}
```

- [ ] **Step 6: Implement commands and rerun tests**

`StatusCommand` checks saved settings, credential presence, and loopback `/health`; it must not start the service. `DoctorCommand` also runs `git --version` and an unauthenticated loopback health check, then resolves the configured central base URI only through a non-review request. `CodeOpsClientApplication` dispatches `start`, `login`, `status`, and `doctor`, returning conventional non-zero status for invalid invocation or failed checks.

Run: `Set-Location local-client; mvn test -Dtest=CodeOpsHttpServerTest,StatusCommandTest,DoctorCommandTest`

Expected: PASS.

---

### Task 6: Migrate the PowerShell Hook to a Localhost-Only Adapter

**Files:**
- Modify: `scripts/git-review-hook.ps1`
- Modify: `scripts/install-pre-push-hook.ps1`
- Modify: `scripts/uninstall-pre-push-hook.ps1`
- Modify: `scripts/git-review-hook.Tests.ps1`
- Test: `scripts/git-review-hook.Tests.ps1`

**Interfaces:**
- Consumes: Git Hook trigger, Git pre-push stdin, and the current repository root.
- Produces: a localhost review request and exit code `1` when blocked or when the Local Client is unavailable under fail-closed policy.

- [ ] **Step 1: Replace legacy test expectations with failing thin-adapter tests**

```powershell
It 'forwards pre-push updates to the localhost Local Client without project credentials' {
    $source = Get-Content -Raw $scriptPath

    $source | Should Match '127\.0\.0\.1:49152/v1/reviews/pre-push'
    $source | Should Not Match 'CODEOPS_PROJECT_ID'
    $source | Should Not Match 'CODEOPS_AGENT_TOKEN'
    $source | Should Not Match '\.codeops\\client\.json'
    $source | Should Not Match '/api/agent/reviews'
}
```

- [ ] **Step 2: Run Pester and observe the expected failure**

Run: `Invoke-Pester .\scripts\git-review-hook.Tests.ps1 -Output Detailed`

Expected: FAIL because the current Hook still reads repository-local configuration and calls the central API.

- [ ] **Step 3: Implement the thin PowerShell adapter**

Keep parameters only for `Trigger`, `PushInput`, `RemoteName`, `ClientUrl` defaulting to `http://127.0.0.1:49152`, and optional `FailOpen` for explicit local development. Resolve the Git root using `git rev-parse --show-toplevel`; post `{repositoryPath, remoteName, updates}` to the correct localhost endpoint; print client `message` and findings; map `blocked` to exit `1` for pre-push and pre-commit. It must not import `pre-push-review.ps1`, read project configuration, or call central/LLM endpoints.

Update global Hook shell definitions only to pass Git stdin and remote name. Retain existing backup and ownership checks. Change uninstall ownership marker from `git-review-hook.ps1` only if the new hook still invokes that script; do not remove non-CodeOps user hooks.

- [ ] **Step 4: Re-run Pester tests**

Run: `Invoke-Pester .\scripts\git-review-hook.Tests.ps1 -Output Detailed`

Expected: PASS.

- [ ] **Step 5: Add and run an adapter response mapping test**

Create a temporary PowerShell-local HTTP listener that returns `{"blocked":true,"message":"Policy blocked","findings":[]}`, invoke the Hook with a temporary Git repository and one well-formed update, and assert exit code `1`. Add a corresponding `blocked:false` test for exit `0`.

Run: `Invoke-Pester .\scripts\git-review-hook.Tests.ps1 -Output Detailed`

Expected: PASS.

---

### Task 7: Add WinSW Installation Assets, End-to-End Coverage, and Documentation

**Files:**
- Create: `local-client/src/main/java/com/codeops/client/install/WinSwInstaller.java`
- Create: `local-client/src/main/resources/winsw/codeops-client.xml.template`
- Create: `local-client/src/test/java/com/codeops/client/install/WinSwInstallerTest.java`
- Create: `local-client/src/test/java/com/codeops/client/integration/PrePushClientIntegrationTest.java`
- Modify: `local-client/src/main/java/com/codeops/client/CodeOpsClientApplication.java`
- Modify: `README.md`
- Modify: `backened/README.md`
- Modify: `docs/superpowers/plans/2026-09-05-local-client-central-resolution.md`

**Interfaces:**
- Consumes: a packaged JAR, an externally provisioned `winsw.exe`, a temporary Git repository, and central HTTP stubs.
- Produces: service XML only when a valid wrapper exists; an end-to-end pre-push request that verifies no absolute local path reaches central API.

- [ ] **Step 1: Write failing installer and integration tests**

```java
@Test
void refusesInstallationWhenWinSwHasNotBeenProvisioned(@TempDir Path home) {
    var installer = new WinSwInstaller(new ClientPaths(home), executableJar);

    assertThatThrownBy(installer::install)
            .hasMessageContaining("winsw.exe");
}

@Test
void blockedCentralReviewPropagatesThroughTheLoopbackPrePushEndpoint(@TempDir Path repository) throws Exception {
    initRepositoryWithCommitAndRemote(repository, "https://git.example.com/acme/order-service.git");
    centralStub.respondToResolveWithRegisteredProject();
    centralStub.respondToReviewWithBlockedResult();

    HttpResponse<String> response = post(client, "/v1/reviews/pre-push", requestFor(repository));

    assertThat(response.body()).contains("\"blocked\":true");
    assertThat(centralStub.reviewRequestBody()).doesNotContain(repository.toString());
}
```

- [ ] **Step 2: Run focused installer and integration tests and observe missing implementation**

Run: `Set-Location local-client; mvn test -Dtest=WinSwInstallerTest,PrePushClientIntegrationTest`

Expected: FAIL because installation command and integration fixture are absent.

- [ ] **Step 3: Implement WinSW generation and CLI installation commands**

`WinSwInstaller.install` locates `winsw.exe` in the service directory, writes the XML template with resolved JAR path and `start` argument, and invokes only `[winsw.exe, install]` through `ProcessBuilder`. It must fail before side effects when the wrapper is missing. `uninstall` invokes only `[winsw.exe, uninstall]` after checking CodeOps-generated XML marker and must leave unrelated services untouched.

Wire `install` and `uninstall` into `CodeOpsClientApplication`. Keep release-bundle acquisition outside the executable; document that the distribution must include the compatible WinSW binary.

- [ ] **Step 4: Implement the integration fixture and re-run focused tests**

Start the Local Client with an ephemeral loopback port, use a temporary initialized Git repository and in-process central HTTP stub, submit an actual JSON pre-push request, and verify the sequence: Git origin read, resolution request made, review request contains remote-derived project identity and relative paths only, central `blocked` is returned unchanged.

Run: `Set-Location local-client; mvn test -Dtest=WinSwInstallerTest,PrePushClientIntegrationTest`

Expected: PASS.

- [ ] **Step 5: Update operational documentation**

Replace the new-installation instructions for `.codeops/client.json` with Java Client commands:

```powershell
Set-Location .\local-client
mvn package
Get-Content -Raw .\agent-token.txt | java -jar .\target\codeops-client.jar login --server-url http://localhost:5173 --token-stdin
java -jar .\target\codeops-client.jar start
```

Document that `install` requires a release bundle containing WinSW, the service executes as the logged-in user to access DPAPI credentials, `client.json` under `%LOCALAPPDATA%\CodeOps` contains no token, and legacy project-local configuration is only a migration fallback.

- [ ] **Step 6: Run final verification**

Run:

```powershell
Set-Location local-client
mvn test
mvn package
Set-Location ..
Invoke-Pester .\scripts\git-review-hook.Tests.ps1 -Output Detailed
Set-Location backened
mvn test
Set-Location ..\frontend
npm test -- --run
npm run build
```

Expected: all client, Hook, backend, and frontend test suites pass; Maven produces `local-client/target/codeops-client.jar`; no step prints an Agent Token.
