package com.rick.site.admin.web;

import com.rick.common.http.exception.BizException;
import com.rick.site.home.entity.HomeSection;
import com.rick.site.home.entity.HomeSectionI18n;
import com.rick.site.home.service.HomeSectionService;
import com.rick.site.i18n.service.DefaultLocaleResolver;
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
 * 后台首页区块管理(Phase 17):列表 + CRUD + 多语言 i18n 编辑。
 *
 * <p>首页区块(sectionKey 如 hero/company/cta)+ 多语言标题/副标题/正文。按 sectionKey 幂等 upsert。
 * 某语言 title/subtitle/content 全空时跳过(保留展示回退到默认语言)。
 *
 * @author Rick.Xu
 */
@Controller
@RequestMapping("/admin/home-sections")
public class AdminHomeSectionController {

    private final HomeSectionService sectionService;
    private final DefaultLocaleResolver localeResolver;

    public AdminHomeSectionController(HomeSectionService sectionService, DefaultLocaleResolver localeResolver) {
        this.sectionService = sectionService;
        this.localeResolver = localeResolver;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("sections", sectionService.listByTenant());
        return "admin/home-sections";
    }

    @GetMapping({"/new", "/{id}/edit"})
    public String form(@PathVariable(required = false) Long id, Model model) {
        HomeSection section;
        Map<String, HomeSectionI18n> i18nMap;
        if (id == null) {
            section = new HomeSection();
            section.setEnabled((short) 1);
            section.setSort(0);
            i18nMap = Map.of();
        } else {
            section = sectionService.selectById(id).orElse(null);
            i18nMap = (section != null) ? sectionService.loadI18nMap(id) : Map.of();
        }
        model.addAttribute("section", section);
        model.addAttribute("i18nMap", i18nMap);
        addLanguages(model);
        return "admin/home-section-form";
    }

    @PostMapping("/save")
    public String save(HomeSection section, HttpServletRequest req, RedirectAttributes ra) {
        try {
            HomeSection saved = sectionService.saveSection(section);
            for (String lang : localeResolver.supportedLanguages()) {
                String title = req.getParameter("title_" + lang);
                String subtitle = req.getParameter("subtitle_" + lang);
                String content = req.getParameter("content_" + lang);
                if (hasAnyText(title, subtitle, content)) {
                    sectionService.saveI18n(saved.getId(), HomeSectionI18n.builder()
                            .language(lang).title(title).subtitle(subtitle).content(content).build());
                }
            }
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/home-sections";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            sectionService.delete(id);
        } catch (BizException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/home-sections";
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
