package com.rick.site.admin.web;

import com.rick.site.publish.entity.PublishRecord;
import com.rick.site.publish.service.PublishRecordService;
import com.rick.site.publish.service.PublishService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * 后台发布 Controller(TASK-1305)。
 *
 * <p>{@code GET /admin/publish} 展示发布按钮 + 发布记录列表(版本/状态/时间/错误)。
 * {@code POST /admin/publish} 触发发布,完成后重定向回列表(PRG)。失败也在记录中可见,
 * 不向用户抛 500 —— 仅展示最新记录的 error_message。
 *
 * <p>租户作用域来自 AdminContextFilter 写入的 TenantContext(认证主体,非 Host,§4)。
 *
 * @author Rick.Xu
 */
@Controller
@RequestMapping("/admin/publish")
public class AdminPublishController {

    private static final Logger log = LoggerFactory.getLogger(AdminPublishController.class);

    private final PublishService publishService;
    private final PublishRecordService recordService;

    public AdminPublishController(PublishService publishService, PublishRecordService recordService) {
        this.publishService = publishService;
        this.recordService = recordService;
    }

    @GetMapping
    public String view(Model model) {
        List<PublishRecord> records = recordService.listByTenant();
        model.addAttribute("records", records);
        model.addAttribute("latest", records.isEmpty() ? null : records.get(0));
        return "admin/publish";
    }

    @PostMapping
    public String publish() {
        try {
            publishService.publish();
        } catch (Exception e) {
            // 发布失败已记录到 publish_record,这里不重复抛错,展示页由记录呈现错误
            log.warn("发布失败(已记录): {}", e.getMessage());
        }
        return "redirect:/admin/publish";
    }
}
