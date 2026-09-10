package com.rick.site;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 外贸独立站 SaaS 平台入口。
 *
 * <p>组件扫描范围仅限 com.rick.site，禁止扫描 com.rick 根包
 * （sharp-fileupload 内含 @SpringBootApplication 的 FileUploadApplication，
 * 需要其 HTTP 接口时只扫 com.rick.fileupload.client）。
 */
@SpringBootApplication
public class SiteApplication {

    public static void main(String[] args) {
        SpringApplication.run(SiteApplication.class, args);
    }
}
