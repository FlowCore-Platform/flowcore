# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability, please report it privately via GitHub Security Advisories.

Do not file public issues for security vulnerabilities.

## Security Features

- JWT bearer token authentication (RFC 7519)
- RBAC/ABAC hybrid authorization model
- Webhook request signing (RFC 9421)
- Sensitive data masking in logs and events
- Audit logging for all authorization decisions

## Scope

- This project validates JWTs and applies policies — it is not a full IAM product
- Production deployments should use TLS 1.2+ and consider mTLS between services
