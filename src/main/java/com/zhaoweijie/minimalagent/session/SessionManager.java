package com.zhaoweijie.minimalagent.session;

/**
 * 管理 Agent Session 生命周期的统一协议。
 */
public interface SessionManager {

    /**
     * 为用户创建具有随机唯一标识的新 Session。
     *
     * @param userId 请求用户标识
     * @return 新建 Session
     */
    AgentSession createSession(String userId);

    /**
     * 按标识读取 Session，并校验请求用户所有权。
     *
     * @param sessionId Session 标识
     * @param userId    请求用户标识
     * @return Session 快照
     */
    AgentSession getSession(String sessionId, String userId);

    /**
     * 读取指定 Session；不存在时使用给定标识创建。
     *
     * @param sessionId Session 标识；为空时生成新标识
     * @param userId    请求用户标识
     * @return 已存在或新建的 Session 快照
     */
    AgentSession getOrCreate(String sessionId, String userId);

    /**
     * 新增或保存 Session，并阻止覆盖其他用户的同名 Session。
     *
     * @param session 待保存 Session
     * @return 保存后的 Session 快照
     */
    AgentSession save(AgentSession session);

    /**
     * 更新已存在的 Session。
     *
     * @param session 待更新 Session
     * @return 更新后的 Session 快照
     */
    AgentSession update(AgentSession session);
}
