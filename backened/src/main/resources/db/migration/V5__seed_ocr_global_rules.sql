-- OCR 内置规则的中文初始版本。使用幂等插入，避免覆盖用户已经编辑的全局规则。
INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'OCR 通用规则', 'QUALITY', '**', '只报告能够从变更中直接证明的问题；说明影响、证据和修复建议，避免猜测。', 1000, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'OCR 通用规则' AND path_pattern = '**');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'Java 规则', 'CORRECTNESS', '**/*.java', '检查空值处理、异常边界、资源关闭、并发安全、事务一致性、权限校验、注入风险和测试缺口。', 100, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'Java 规则' AND path_pattern = '**/*.java');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'Go 规则', 'CORRECTNESS', '**/*.go', '检查错误是否处理、goroutine 和 channel 是否可退出、取消传播、竞态、锁、资源释放、定时器和边界输入。', 100, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'Go 规则' AND path_pattern = '**/*.go');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'Python 规则', 'CORRECTNESS', '**/*.{py,ipynb}', '检查输入校验、异常处理、资源释放、权限边界、反序列化和命令执行风险；关注异步、并发和测试覆盖。', 100, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'Python 规则' AND path_pattern = '**/*.{py,ipynb}');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'JavaScript 与 TypeScript 规则', 'SECURITY', '**/*.{ts,js,tsx,jsx,mjs,cjs}', '检查类型边界、异步错误、XSS、原型污染、注入、敏感数据、状态同步和副作用；验证客户端输入和服务端响应。', 100, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'JavaScript 与 TypeScript 规则' AND path_pattern = '**/*.{ts,js,tsx,jsx,mjs,cjs}');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'Vue 规则', 'QUALITY', '**/*.{vue}', '检查组件边界、响应式状态、生命周期、异步错误、XSS、事件监听清理和不必要的副作用。', 110, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'Vue 规则' AND path_pattern = '**/*.{vue}');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'Kotlin 规则', 'CORRECTNESS', '**/*.kt', '检查空安全、协程取消、异常处理、资源释放、线程安全、Java 互操作和测试覆盖。', 100, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'Kotlin 规则' AND path_pattern = '**/*.kt');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'Rust 规则', 'CORRECTNESS', '**/*.rs', '检查所有权和借用、错误传播、并发安全、unsafe 边界、资源生命周期、panic 风险和输入校验。', 100, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'Rust 规则' AND path_pattern = '**/*.rs');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'C 与 C++ 规则', 'SECURITY', '**/*.{c,cpp,cc,cxx,hpp,hxx}', '检查缓冲区越界、释放后使用、双重释放、整数溢出、未定义行为、资源泄漏、线程安全和输入边界。', 100, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'C 与 C++ 规则' AND path_pattern = '**/*.{c,cpp,cc,cxx,hpp,hxx}');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'PHP 规则', 'SECURITY', '**/*.{php,phtml}', '检查 SQL、命令、文件和模板注入，输出编码，会话与权限校验，反序列化风险及错误信息泄漏。', 100, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'PHP 规则' AND path_pattern = '**/*.{php,phtml}');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'C# 与 Ruby 规则', 'CORRECTNESS', '**/*.{cs,rb}', '检查异常处理、输入校验、资源释放、并发安全、权限边界、序列化和测试覆盖。', 120, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'C# 与 Ruby 规则' AND path_pattern = '**/*.{cs,rb}');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'JSON 规则', 'CONFIGURATION', '**/*.{json,json5}', '检查结构合法性、重复键、敏感信息、类型一致性、过度开放的配置和环境差异。', 100, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'JSON 规则' AND path_pattern = '**/*.{json,json5}');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'YAML 规则', 'CONFIGURATION', '**/*.{yaml,yml}', '检查缩进和类型、重复键、默认值、环境覆盖、权限过宽、密钥泄漏和不安全配置。', 100, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'YAML 规则' AND path_pattern = '**/*.{yaml,yml}');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'GitHub Actions 规则', 'SECURITY', '.github/workflows/**/*.{yaml,yml}', '检查工作流权限、未固定 Action 版本、来自事件的未可信输入、脚本注入、密钥暴露和供应链风险。', 80, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'GitHub Actions 规则' AND path_pattern = '.github/workflows/**/*.{yaml,yml}');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'Properties 配置规则', 'CONFIGURATION', '**/*.properties', '检查配置键一致性、敏感信息、默认值、环境覆盖和连接超时等可靠性设置。', 100, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'Properties 配置规则' AND path_pattern = '**/*.properties');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'Maven POM 规则', 'DEPENDENCY', '**/pom.xml', '检查依赖版本、传递依赖、插件配置、仓库来源、许可证、构建可复现性和安全漏洞风险。', 90, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'Maven POM 规则' AND path_pattern = '**/pom.xml');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'Gradle 构建规则', 'DEPENDENCY', '**/build.gradle', '检查依赖来源与版本、任务输入输出、凭据泄漏、构建缓存一致性和脚本执行风险。', 90, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'Gradle 构建规则' AND path_pattern = '**/build.gradle');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'Node 包配置规则', 'DEPENDENCY', '**/package.json', '检查依赖版本和脚本、安装生命周期钩子、来源可信度、权限和敏感配置。', 90, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'Node 包配置规则' AND path_pattern = '**/package.json');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'Cargo 配置规则', 'DEPENDENCY', '**/Cargo.toml', '检查依赖来源和版本、feature 开关、构建脚本、许可证、锁文件一致性和供应链风险。', 90, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'Cargo 配置规则' AND path_pattern = '**/Cargo.toml');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'Composer 配置规则', 'DEPENDENCY', '**/composer.json', '检查依赖来源和版本、自动加载、脚本钩子、许可证、安全公告和生产依赖边界。', 90, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'Composer 配置规则' AND path_pattern = '**/composer.json');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'GitHub 配置规则', 'SECURITY', '.github/**/*.{yaml,yml}', '检查仓库自动化配置中的权限、密钥、未可信事件输入、脚本注入、Action 版本固定和发布边界。', 90, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'GitHub 配置规则' AND path_pattern = '.github/**/*.{yaml,yml}');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'MyBatis Mapper XML 规则', 'SECURITY', '**/*{mapper,dao}*.xml', '检查参数绑定、动态 SQL、字符串拼接、结果映射、分页边界、事务语义和 SQL 注入风险。', 70, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'MyBatis Mapper XML 规则' AND path_pattern = '**/*{mapper,dao}*.xml');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, '通用 XML 规则', 'CONFIGURATION', '**/*.xml', '检查结构和命名空间、配置覆盖、外部实体、参数绑定、敏感信息和兼容性。', 200, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = '通用 XML 规则' AND path_pattern = '**/*.xml');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'SQL 迁移规则', 'DATABASE', '**/*.{sql}', '检查参数化、权限、事务、索引、锁、迁移可回滚性、数据兼容性和敏感数据处理。', 100, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'SQL 迁移规则' AND path_pattern = '**/*.{sql}');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'Protocol Buffer 规则', 'API', '**/*.proto', '检查字段编号兼容性、删除字段、默认值、服务接口、权限边界和向后兼容。', 100, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'Protocol Buffer 规则' AND path_pattern = '**/*.proto');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'GraphQL 规则', 'API', '**/*.{graphql,gql}', '检查查询深度和复杂度、授权、字段暴露、输入校验、分页和敏感数据泄漏。', 100, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'GraphQL 规则' AND path_pattern = '**/*.{graphql,gql}');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'Prisma 规则', 'DATABASE', '**/*.prisma', '检查模型约束、关系和级联、索引、迁移破坏性、权限和敏感字段暴露。', 100, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'Prisma 规则' AND path_pattern = '**/*.prisma');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'Terraform 与 HCL 规则', 'INFRASTRUCTURE', '**/*.{tf,hcl,tfvars}', '检查公开资源、最小权限、密钥、网络暴露、状态管理、变量校验和变更破坏性。', 100, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'Terraform 与 HCL 规则' AND path_pattern = '**/*.{tf,hcl,tfvars}');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'Bicep 规则', 'INFRASTRUCTURE', '**/*.bicep', '检查资源公开暴露、身份权限、网络边界、密钥、参数默认值和部署可重复性。', 100, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'Bicep 规则' AND path_pattern = '**/*.bicep');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'FreeMarker 模板规则', 'SECURITY', '**/*.{ftl,ftlh,ftlx}', '检查模板注入、输出编码、脚本和 URL 上下文、未经校验的对象访问及敏感数据泄漏。', 100, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'FreeMarker 模板规则' AND path_pattern = '**/*.{ftl,ftlh,ftlx}');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'Handlebars 与 Mustache 规则', 'SECURITY', '**/*.{hbs,mustache}', '检查 HTML、JavaScript、CSS、URL 和 JSON 上下文的正确编码，避免把 HTML 转义当成通用序列化。', 100, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'Handlebars 与 Mustache 规则' AND path_pattern = '**/*.{hbs,mustache}');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'Pug 模板规则', 'SECURITY', '**/*.pug', '检查模板注入、原始 HTML、脚本上下文编码、JSON 序列化、include 来源和敏感信息。', 100, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'Pug 模板规则' AND path_pattern = '**/*.pug');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'ArkTS 规则', 'CORRECTNESS', '**/*.ets', '检查状态管理、异步生命周期、权限、输入校验、资源释放和 UI 线程安全。', 100, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'ArkTS 规则' AND path_pattern = '**/*.ets');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'Astro 规则', 'QUALITY', '**/*.astro', '检查服务端与客户端边界、过量客户端 JavaScript、输出编码、数据获取和组件权限。', 100, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'Astro 规则' AND path_pattern = '**/*.astro');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'Swift 规则', 'SECURITY', '**/*.swift', '检查可选值、错误处理、并发隔离、权限、网络输入、密钥、WKWebView 桥接和资源生命周期。', 100, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'Swift 规则' AND path_pattern = '**/*.swift');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'Solidity 规则', 'SECURITY', '**/*.sol', '检查重入、权限、整数和精度、预言机、拒绝服务、升级代理和资金转移路径。', 100, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'Solidity 规则' AND path_pattern = '**/*.sol');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'Vyper 规则', 'SECURITY', '**/*.vy', '检查重入、权限控制、整数边界、外部调用、拒绝服务、升级和资金安全。', 100, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'Vyper 规则' AND path_pattern = '**/*.vy');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'Verilog 规则', 'CORRECTNESS', '**/*.{v,sv,vh}', '检查时序、复位、竞态、位宽、未驱动信号、综合差异和状态机边界。', 100, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'Verilog 规则' AND path_pattern = '**/*.{v,sv,vh}');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'VHDL 规则', 'CORRECTNESS', '**/*.{vhd,vhdl}', '检查时序、复位、位宽、未定义状态、综合差异和接口边界。', 100, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'VHDL 规则' AND path_pattern = '**/*.{vhd,vhdl}');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'Thrift 与 Cap''n Proto 规则', 'API', '**/*.{thrift,capnp}', '检查接口兼容性、字段编号、默认值、序列化边界、权限和错误处理。', 100, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'Thrift 与 Cap''n Proto 规则' AND path_pattern = '**/*.{thrift,capnp}');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'Nix 规则', 'INFRASTRUCTURE', '**/*.nix', '检查包来源、构建可复现、权限、网络访问、秘密和不受信输入。', 100, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'Nix 规则' AND path_pattern = '**/*.nix');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'Haskell、Nim、Elm 与 Zig 规则', 'CORRECTNESS', '**/*.{hs,lhs,nim,nims,nimble,elm,zig}', '检查类型和边界、错误处理、资源生命周期、并发、外部输入和构建配置。', 120, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'Haskell、Nim、Elm 与 Zig 规则' AND path_pattern = '**/*.{hs,lhs,nim,nims,nimble,elm,zig}');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'Julia 与 R 规则', 'CORRECTNESS', '**/*.{jl,R}', '检查数据输入、类型转换、缺失值、资源和连接释放、性能热点及结果正确性。', 120, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'Julia 与 R 规则' AND path_pattern = '**/*.{jl,R}');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'MATLAB 与 Objective-C 规则', 'CORRECTNESS', '**/*.{m,mm}', '检查内存管理、边界、异常和错误码、线程安全、外部输入、权限及平台 API 生命周期。', 120, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'MATLAB 与 Objective-C 规则' AND path_pattern = '**/*.{m,mm}');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'gettext 规则', 'QUALITY', '**/*.{po,pot}', '检查消息占位符、复数形式、编码、格式化参数和翻译上下文一致性。', 120, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'gettext 规则' AND path_pattern = '**/*.{po,pot}');

INSERT INTO review_rules (scope, project_id, name, category, path_pattern, content, priority, enabled, version, created_at, updated_at)
SELECT 'GLOBAL', NULL, 'Jsonnet 规则', 'CONFIGURATION', '**/*.{jsonnet,libsonnet}', '检查变量覆盖、类型和结构、环境差异、敏感信息和生成结果的一致性。', 120, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM review_rules WHERE scope = 'GLOBAL' AND project_id IS NULL AND name = 'Jsonnet 规则' AND path_pattern = '**/*.{jsonnet,libsonnet}');
