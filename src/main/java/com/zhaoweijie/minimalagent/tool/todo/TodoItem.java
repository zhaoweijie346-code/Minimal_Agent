package com.zhaoweijie.minimalagent.tool.todo;

import java.time.Instant;

/**
 * Session 内的一条待办事项。
 *
 * @param id          待办事项唯一标识
 * @param sessionId   待办事项所属 Session 标识
 * @param content     待办内容
 * @param status      当前处理状态
 * @param createdAt   创建时间
 * @param completedAt 完成时间；未完成时为空
 */
public record TodoItem(
        /** 待办事项唯一标识。 */
        String id,
        /** 待办事项所属 Session 标识。 */
        String sessionId,
        /** 待办内容。 */
        String content,
        /** 当前处理状态。 */
        TodoStatus status,
        /** 创建时间。 */
        Instant createdAt,
        /** 完成时间；未完成时为空。 */
        Instant completedAt
) {
}
