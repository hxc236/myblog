# GitHub OAuth 只认证唯一 Site Owner

Admin Console 只允许配置中的唯一 GitHub 身份登录，不提供公众注册、本地密码、角色管理或 legacy JWT；Spring Boot 在 OAuth allowlist 校验后签发短期随机不透明会话令牌，Vue 只在 `sessionStorage` 保存令牌，服务端只保存哈希。这个选择保留 Vue 与 Spring Boot 的部署分离，同时避免重新扩大旧账户系统的攻击面。
