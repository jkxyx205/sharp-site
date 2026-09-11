package com.rick.site.admin.web;

import com.rick.common.http.exception.BizException;
import com.rick.site.i18n.service.DefaultLocaleResolver;
import com.rick.site.page.entity.SitePage;
import com.rick.site.page.entity.SitePageI18n;
import com.rick.site.page.service.SitePageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

/**
 * 后台页面管理(TASK-0403):CRUD + 多语言 i18n 编辑。
 *
 * <p>列表/新建/编辑/保存/删除。通用字段一组 + 每支持语言一个 fieldset,
 * 字段名 {@code title_${lang}} 等,保存时遍历语言组装 i18n 调 {@link SitePageService#saveI18n}。
 * 某语言全部为空时跳过(不写空行,保留展示回退到默认语言)。租户隔离由 AdminContextFilter
 * 写入的 TenantContext + SiteDatabaseConfig 保障;跨租户 id 查不到 → 404/BizException。
 *
 * @author Rick.Xu
 */
@Controller
@RequestMapping("/admin/pages")
public class AdminPageController {

    private final SitePageService pageService;
    private final DefaultLocaleResolver localeResolver;

    public AdminPageController(SitePageService pageService, DefaultLocaleResolver localeResolver) {
        this.pageService = pageService;
        this.localeResolver = localeResolver;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("pages", pageService.listByTenant());
        return "admin/pages";
    }

    /** 新建(/new)或编辑(/{id}/edit)共用一个表单。 */
    @GetMapping({"/new", "/{id}/edit"})
    public String form(@PathVariable(required = false) Long id, Model model) {
        SitePage page;
        Map<String, SitePageI18n> i18nMap;
        if (id == null) {
            page = new SitePage();
            page.setStatus((short) 1);
            i18nMap = Map.of();
        } else {
            page = pageService.selectById(id).orElse(null);
            i18nMap = (page != null) ? pageService.loadI18nMap(id) : Map.of();
        }
        model.addAttribute("page", page);
        model.addAttribute("i18nMap", i18nMap);
        addLanguages(model);
        return "admin/page-form";
    }

    @PostMapping("/save")
    public String save(SitePage page, HttpServletRequest req, RedirectAttributes ra) {
        try {
            SitePage saved = pageService.savePage(page);
            for (String lang : localeResolver.supportedLanguages()) {
                String title = req.getParameter("title_" + lang);
                String content = req.getParameter("content_" + lang);
                String cover = req.getParameter("cover_" + lang);
                if (hasAnyText(title, content, cover)) {
                    pageService.saveI18n(saved.getId(), SitePageI18n.builder()
                            .language(lang).title(title).content(content).cover(cover).build());
                }
            }
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/pages";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            pageService.delete(id);
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/pages";
    }

    private void addLanguages(Model model) {
        model.addAttribute("languages", localeResolver.supportedLanguages());
        model.addAttribute("defaultLanguage", localeResolver.defaultLanguage());
    }

    private static boolean hasAnyText(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return true;
            }
        }
        return false;
    }
}
