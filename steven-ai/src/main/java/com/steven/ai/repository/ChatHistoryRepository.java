package com.steven.ai.repository;

import java.util.List;

public interface ChatHistoryRepository {

    /**
     * 保存会话记录
     * @param type 业务类型，如：chat、service、pdf
     * @param chatId 会话ID
     */
    void save(String type, String chatId);

    /**
     * 获取会话ID列表
     * @param type 业务类型，如：chat、service、pdf
     * @return 会话ID列表
     */
    List<String> getChatIds(String type);

    /**
     * 删除指定类型下的某个会话
     * @param type 业务类型
     * @param chatId 会话ID
     */
    void delete(String type, String chatId);

    /**
     * 清空指定类型的全部会话
     * @param type 业务类型
     */
    void clear(String type);
}
