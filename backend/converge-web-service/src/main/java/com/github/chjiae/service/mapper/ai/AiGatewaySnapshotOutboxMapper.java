package com.github.chjiae.service.mapper.ai;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.chjiae.service.entity.ai.AiGatewaySnapshotOutbox;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 网关快照 outbox Mapper。
 */
public interface AiGatewaySnapshotOutboxMapper extends BaseMapper<AiGatewaySnapshotOutbox> {

    /**
     * 安全认领到期 outbox 事件。
     *
     * @param lockOwner 认领者
     * @param now 当前时间
     * @param staleBefore 过期锁阈值
     * @param limit 最大认领数量
     * @return 已认领事件
     */
    List<AiGatewaySnapshotOutbox> claimDue(@Param("lockOwner") String lockOwner,
                                           @Param("now") LocalDateTime now,
                                           @Param("staleBefore") LocalDateTime staleBefore,
                                           @Param("limit") int limit);

    /**
     * 标记 outbox 事件发布成功。
     *
     * @param ids outbox ID 列表
     * @param now 当前时间
     * @return 更新行数
     */
    int markPublished(@Param("ids") List<Long> ids, @Param("now") LocalDateTime now);

    /**
     * 标记 outbox 事件发布失败并设置下次重试时间。
     *
     * @param ids outbox ID 列表
     * @param errorSummary 安全错误摘要
     * @param nextAttemptAt 下一次可重试时间
     * @param now 当前时间
     * @return 更新行数
     */
    int markFailed(@Param("ids") List<Long> ids,
                   @Param("errorSummary") String errorSummary,
                   @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
                   @Param("now") LocalDateTime now);
}
