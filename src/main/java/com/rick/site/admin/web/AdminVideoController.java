package com.rick.site.admin.web;

import com.rick.common.http.exception.BizException;
import com.rick.site.catalog.service.CategoryService;
import com.rick.site.common.async.AsyncRunner;
import com.rick.site.i18n.service.DefaultLocaleResolver;
import com.rick.site.tenant.context.TenantContext;
import com.rick.site.tenant.entity.Tenant;
import com.rick.site.video.entity.Video;
import com.rick.site.video.entity.VideoI18n;
import com.rick.site.video.service.VideoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

/**
 * 后台视频中心(参照 {@link AdminProductController}):列表(本租户作用域)+ CRUD + 多语言 i18n 编辑。
 *
 * <p>较产品多一个 {@code videoUrl}(视频地址链接)字段;无规格 JSON(specification)。
 * categoryId 下拉来自 {@code categoryService.listByType("VIDEO")}。
 *
 * @author Rick.Xu
 */
@Controller
@RequestMapping("/admin/videos")
public class AdminVideoController {

    private final VideoService videoService;
    private final CategoryService categoryService;
    private final DefaultLocaleResolver localeResolver;
    private final AsyncRunner asyncRunner;

    public AdminVideoController(VideoService videoService, CategoryService categoryService,
                                DefaultLocaleResolver localeResolver, AsyncRunner asyncRunner) {
        this.videoService = videoService;
        this.categoryService = categoryService;
        this.localeResolver = localeResolver;
        this.asyncRunner = asyncRunner;
    }

    @GetMapping
    public String list(@RequestParam(required = false) Long categoryId, Model model) {
        model.addAttribute("categories", categoryService.listByType("VIDEO"));
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("videos", categoryId == null ? videoService.listByTenant() : videoService.listByCategory(categoryId));
        return "admin/videos";
    }

    @GetMapping({"/new", "/{id}/edit"})
    public String form(@PathVariable(required = false) Long id,
                       @RequestParam(required = false) String lang, Model model) {
        String language = (lang == null || lang.isBlank()) ? localeResolver.defaultLanguage() : lang;
        Video video;
        Map<String, VideoI18n> i18nMap;
        if (id == null) {
            video = new Video();
            video.setStatus((short) 1);
            video.setSort(0);
            i18nMap = Map.of();
        } else {
            video = videoService.selectById(id).orElse(null);
            i18nMap = (video != null) ? videoService.loadI18nMap(id) : Map.of();
        }
        model.addAttribute("video", video);
        model.addAttribute("i18nMap", i18nMap);
        model.addAttribute("currentLang", language);
        model.addAttribute("categories", categoryService.listByType("VIDEO"));
        addLanguages(model);
        return "admin/video-form";
    }

    @PostMapping("/save")
    public String save(Video video, HttpServletRequest req, RedirectAttributes ra) {
        String language = req.getParameter("language");
        try {
            Video saved = videoService.saveVideo(video);
            String name = req.getParameter("name");
            if (name != null && !name.isBlank()) {
                VideoI18n source = VideoI18n.builder()
                        .language(language)
                        .name(name)
                        .subtitle(req.getParameter("subtitle"))
                        .description(req.getParameter("description"))
                        .content(req.getParameter("content"))
                        .seoTitle(req.getParameter("seoTitle"))
                        .seoDescription(req.getParameter("seoDescription"))
                        .build();
                videoService.saveI18n(saved.getId(), source);
                if ("true".equals(req.getParameter("syncToOtherLanguages"))) {
                    // 翻译同步逐语种调 LLM,耗时较长,提交到异步线程池;请求线程立即返回。
                    // 租户上下文不隐式继承:在此捕获,异步任务体里 set/clear。
                    Tenant tenant = TenantContext.require();
                    java.util.List<String> targets = targetLanguages(language);
                    asyncRunner.run(() -> {
                        TenantContext.set(tenant);
                        try {
                            videoService.syncToLanguages(saved.getId(), source, targets);
                        } finally {
                            TenantContext.clear();
                        }
                    });
                }
            }
            return "redirect:/admin/videos/" + saved.getId() + "/edit?lang=" + language;
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/videos";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            videoService.delete(id);
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/videos";
    }

    private void addLanguages(Model model) {
        model.addAttribute("languages", localeResolver.supportedLanguages());
        model.addAttribute("defaultLanguage", localeResolver.defaultLanguage());
    }

    /** 同步目标语种:租户启用语种去掉当前编辑语种。 */
    private java.util.List<String> targetLanguages(String currentLang) {
        return localeResolver.supportedLanguages().stream()
                .filter(l -> !l.equals(currentLang))
                .toList();
    }
}
