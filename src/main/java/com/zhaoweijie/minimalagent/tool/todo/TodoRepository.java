package com.zhaoweijie.minimalagent.tool.todo;

import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 按用户和 Session 隔离待办事项的线程安全内存仓库。
 */
@Repository
public class TodoRepository {

    /** 以复合会话键分区、再以 Todo ID 索引的线程安全存储。 */
    private final ConcurrentMap<SessionKey, ConcurrentMap<String, TodoItem>> sessionTodos =
            new ConcurrentHashMap<>();

    /**
     * 在指定 Session 中新增待办事项。
     *
     * @param userId    当前用户标识
     * @param sessionId 当前 Session 标识
     * @param content   待办内容
     * @return 新增的待办事项
     */
    public TodoItem add(String userId, String sessionId, String content) {
        TodoItem item = new TodoItem(
                UUID.randomUUID().toString(),
                sessionId,
                content,
                TodoStatus.PENDING,
                Instant.now(),
                null
        );
        todosFor(userId, sessionId).put(item.id(), item);
        return item;
    }

    /**
     * 列出指定 Session 的全部待办事项。
     *
     * @param userId    当前用户标识
     * @param sessionId 当前 Session 标识
     * @return 按创建时间排序的待办快照
     */
    public List<TodoItem> list(String userId, String sessionId) {
        Map<String, TodoItem> todos = sessionTodos.get(new SessionKey(userId, sessionId));
        if (todos == null) {
            return List.of();
        }

        // 返回排序后的快照，避免调用方观察到并发 Map 迭代过程中的不稳定顺序。
        return todos.values().stream()
                .sorted(Comparator.comparing(TodoItem::createdAt).thenComparing(TodoItem::id))
                .toList();
    }

    /**
     * 将指定 Session 中的待办标记为完成。
     *
     * @param userId    当前用户标识
     * @param sessionId 当前 Session 标识
     * @param todoId    待办事项标识
     * @return 更新后的待办；当前 Session 不包含该 ID 时为空
     */
    public Optional<TodoItem> complete(String userId, String sessionId, String todoId) {
        Map<String, TodoItem> todos = sessionTodos.get(new SessionKey(userId, sessionId));
        if (todos == null) {
            return Optional.empty();
        }

        // computeIfPresent 保证并发完成同一事项时，状态替换是单键原子的。
        TodoItem completed = todos.computeIfPresent(todoId, (id, current) -> {
            if (current.status() == TodoStatus.COMPLETED) {
                return current;
            }
            return new TodoItem(
                    current.id(),
                    current.sessionId(),
                    current.content(),
                    TodoStatus.COMPLETED,
                    current.createdAt(),
                    Instant.now()
            );
        });
        return Optional.ofNullable(completed);
    }

    /**
     * 删除指定 Session 中的待办事项。
     *
     * @param userId    当前用户标识
     * @param sessionId 当前 Session 标识
     * @param todoId    待办事项标识
     * @return 是否成功删除
     */
    public boolean delete(String userId, String sessionId, String todoId) {
        Map<String, TodoItem> todos = sessionTodos.get(new SessionKey(userId, sessionId));
        return todos != null && todos.remove(todoId) != null;
    }

    /**
     * 获取或原子创建指定用户 Session 的内部待办 Map。
     */
    private ConcurrentMap<String, TodoItem> todosFor(String userId, String sessionId) {
        return sessionTodos.computeIfAbsent(
                new SessionKey(userId, sessionId),
                ignored -> new ConcurrentHashMap<>()
        );
    }

    /**
     * 同时包含用户与 Session 的仓库存储分区键。
     *
     * @param userId    用户标识
     * @param sessionId Session 标识
     */
    private record SessionKey(
            /** 用户标识。 */
            String userId,
            /** Session 标识。 */
            String sessionId
    ) {
    }
}
