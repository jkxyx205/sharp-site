package com.rick.site;

import com.rick.db.plugin.generator.TableGenerator;
import com.rick.site.video.entity.VideoI18n;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * @author Rick.Xu
 * @date 2025/11/10 16:27
 */
@SpringBootTest
public class TableGeneratorTest {

    @Autowired
    private TableGenerator tableGenerator;

    @Test
    public void testGeneratorTable() {
//        tableGenerator.createTable(Video.class);
        tableGenerator.createTable(VideoI18n.class);
    }
}

