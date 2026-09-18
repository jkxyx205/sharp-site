package com.rick.site.video.dao;

import com.rick.db.repository.EntityDAOImpl;
import com.rick.site.video.entity.Video;
import org.springframework.stereotype.Repository;

/** 视频 DAO。 */
@Repository
public class VideoDAO extends EntityDAOImpl<Video, Long> {
}
