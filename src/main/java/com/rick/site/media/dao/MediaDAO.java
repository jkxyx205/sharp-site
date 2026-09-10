package com.rick.site.media.dao;

import com.rick.db.repository.EntityDAOImpl;
import com.rick.site.media.entity.Media;
import org.springframework.stereotype.Repository;

/** 媒体文件 DAO。 */
@Repository
public class MediaDAO extends EntityDAOImpl<Media, Long> {
}
