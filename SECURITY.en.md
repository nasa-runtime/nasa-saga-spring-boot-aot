# Security Policy

## Supported releases

The current release line receives security updates. Advisories for `nasa-saga`, Spring Boot, Jackson, gRPC,
protobuf, and MyBatis remain owned by their projects. Keep the target library and adapter versions aligned.

## Private reporting

Use **Security → Report a vulnerability** in the GitHub repository. Do not disclose uncoordinated details in a
public issue, discussion, or pull request. Include affected versions, deployment shape, reproduction, impact, and
possible mitigations, without keys, certificates, or production data.

## Boundary

This project only generates reachability hints. It grants no capability, executes no participant, and does not make
business effects idempotent. Message authentication, leases, fencing, database transactions, and certificate
configuration remain security boundaries of `nasa-saga` and the host application.
