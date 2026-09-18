package com.rick.site.admin.web;

import com.rick.common.http.exception.BizException;
import com.rick.site.media.service.MediaService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 后台素材管理(Phase 8 / DATABASE.md §15):上传 + 列表 + 删除。
 *
 * <p>上传复用 {@link MediaService}(经 sharp-fileupload,目录按租户 code 划分
 * {@code /{tenant_code}/{文件名}})。列表仅本租户(TenantContext 隔离);图片在列表内
 * 直接预览,所有文件提供可复制 URL。租户隔离/校验/逻辑删除均在 Service 层。
 *
 * @author Rick.Xu
 */
@Controller
@RequestMapping("/admin/media")
public class AdminMediaController {

    private final MediaService mediaService;

    public AdminMediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("medias", mediaService.listByTenant());
        return "admin/media";
    }

    @PostMapping("/upload")
    public String upload(@RequestParam("files") MultipartFile[] files,
                         @RequestParam(required = false) String title,
                         @RequestParam(required = false) String altText,
                         @RequestParam(required = false) String path,
                         RedirectAttributes ra) {
        try {
            for (MultipartFile file : files) {
                mediaService.upload(file, path, title, altText);
            }
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            ra.addFlashAttribute("error", "上传失败:" + e.getMessage());
        }
        return "redirect:/admin/media";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            mediaService.delete(id);
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            ra.addFlashAttribute("error", "删除失败:" + e.getMessage());
        }
        return "redirect:/admin/media";
    }
}
