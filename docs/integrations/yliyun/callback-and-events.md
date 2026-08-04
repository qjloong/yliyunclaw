# Callback 与事件

MateClaw 将运行状态和需要业务处理的结果回传云盘 AI Runtime。云盘 Callback Inbox 是业务消费的幂等边界。

## 事件类别

- Run：accepted、started、progress、completed、failed、cancelled。
- Team/Step：worker started、tool progress、step completed/failed。
- Human：approval required、resumed、rejected、expired。
- Artifact：candidate created、validation result、artifact ready。

## 发送约束

- 每个事件携带稳定 `eventId`、`runId`、`traceId`、事件序号和发生时间。
- Callback 使用服务身份、HMAC、时间戳和 Nonce；失败按可重试错误分类退避。
- 同一事件重发保持同一个 `eventId`，不得生成重复业务副作用。
- 终态事件不能被更早的乱序事件覆盖。

## SSE

SSE 用于用户体验和运行进度，不作为业务事实唯一来源。断线通过 `Last-Event-ID` 恢复；最终状态由可查询 Run API 和 Callback Inbox 对账。
