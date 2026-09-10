package com.rick.site.publish.dao;

import com.rick.db.repository.EntityDAOImpl;
import com.rick.site.publish.entity.PublishRecord;
import org.springframework.stereotype.Repository;

/**
 * 发布记录 DAO(TASK-1301)。
 *
 * @author Rick.Xu
 */
@Repository
public class PublishRecordDAO extends EntityDAOImpl<PublishRecord, Long> {
}
