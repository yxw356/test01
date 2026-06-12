package com.yuki.enterprise_private_rag_qa.service;

import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.io.ByteArrayOutputStream;
import java.util.List;

@Service
public class TrainingDeckExportService {

    private static final Color INK = new Color(24, 39, 51);
    private static final Color MUTED = new Color(88, 103, 114);
    private static final Color ACCENT = new Color(26, 118, 93);
    private static final Color ACCENT_DARK = new Color(18, 83, 69);
    private static final Color SOFT = new Color(233, 244, 239);
    private static final Color PANEL = new Color(246, 248, 250);

    public byte[] export(TrainingDeckService.DeckGenerationResult deck) {
        try (XMLSlideShow ppt = new XMLSlideShow(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ppt.setPageSize(new Dimension(1280, 720));
            addCoverSlide(ppt, deck);
            addAgendaSlide(ppt, deck);
            for (TrainingDeckService.DeckSlide slide : deck.slides()) {
                if (slide.index() == 1 || slide.index() == 5 || slide.index() == 9 || slide.index() == 13) {
                    addSectionSlide(ppt, deck, slide);
                }
                addContentSlide(ppt, deck, slide);
            }
            addSourceSlide(ppt, deck);
            addClosingSlide(ppt, deck);
            ppt.write(output);
            return output.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("导出 PPTX 失败: " + e.getMessage(), e);
        }
    }

    private void addCoverSlide(XMLSlideShow ppt, TrainingDeckService.DeckGenerationResult deck) {
        XSLFSlide slide = ppt.createSlide();
        addBackground(slide);
        addAccent(slide, 0, 0, 1280, 16, ACCENT);
        addTitle(slide, deck.title(), 86, 170, 1040, 104, 38);
        addText(slide, "培训对象：" + nullToDefault(deck.audience(), "部门员工"), 92, 302, 780, 42, 20, MUTED, false);
        addText(slide, "生成时间：" + deck.generatedAt(), 92, 354, 780, 36, 15, MUTED, false);
        addAccent(slide, 92, 430, 180, 8, ACCENT);
        addText(slide, "龙汇QA · 知识库培训课件", 92, 466, 780, 34, 17, ACCENT, true);
        addPanel(slide, 916, 152, 220, 300, SOFT, new Color(209, 232, 222));
        addText(slide, "KNOWLEDGE", 952, 220, 170, 30, 18, ACCENT_DARK, true);
        addText(slide, "TRAINING", 952, 256, 170, 30, 18, ACCENT_DARK, true);
        addText(slide, "DECK", 952, 292, 170, 30, 18, ACCENT_DARK, true);
    }

    private void addAgendaSlide(XMLSlideShow ppt, TrainingDeckService.DeckGenerationResult deck) {
        XSLFSlide slide = ppt.createSlide();
        addBackground(slide);
        addText(slide, "目录", 86, 62, 200, 54, 31, INK, true);
        addText(slide, "本次培训将围绕以下主题展开", 90, 120, 520, 32, 16, MUTED, false);
        XSLFTextBox box = slide.createTextBox();
        box.setAnchor(new Rectangle(118, 190, 980, 390));
        int limit = Math.min(8, deck.slides().size());
        for (int i = 0; i < limit; i++) {
            TrainingDeckService.DeckSlide item = deck.slides().get(i);
            XSLFTextParagraph paragraph = box.addNewTextParagraph();
            paragraph.setSpaceAfter(10.0);
            XSLFTextRun run = paragraph.addNewTextRun();
            run.setText(String.format("%02d  %s", i + 1, nullToDefault(item.title(), "培训要点")));
            run.setFontSize(21.0);
            run.setFontColor(INK);
            run.setFontFamily("Microsoft YaHei");
        }
        if (!box.getTextParagraphs().isEmpty()) {
            box.removeTextParagraph(box.getTextParagraphs().get(0));
        }
        addFooter(slide, deck, "目录");
    }

    private void addSectionSlide(XMLSlideShow ppt, TrainingDeckService.DeckGenerationResult deck,
                                 TrainingDeckService.DeckSlide item) {
        XSLFSlide slide = ppt.createSlide();
        addAccent(slide, 0, 0, 1280, 720, ACCENT_DARK);
        addText(slide, String.format("PART %02d", item.index()), 92, 190, 280, 38, 18, new Color(185, 223, 207), true);
        addText(slide, nullToDefault(item.title(), "培训章节"), 92, 246, 980, 84, 36, Color.WHITE, true);
        addText(slide, "围绕本章节的制度要求、执行流程与注意事项展开。", 96, 352, 780, 34, 18, new Color(219, 236, 229), false);
        addAccent(slide, 92, 426, 190, 8, new Color(185, 223, 207));
        addText(slide, "龙汇QA", 92, 642, 220, 28, 14, new Color(219, 236, 229), true);
    }

    private void addContentSlide(XMLSlideShow ppt, TrainingDeckService.DeckGenerationResult deck,
                                 TrainingDeckService.DeckSlide item) {
        XSLFSlide slide = ppt.createSlide();
        addBackground(slide);
        addAccent(slide, 0, 0, 1280, 10, ACCENT);
        addText(slide, String.format("%02d", item.index()), 76, 54, 72, 34, 18, ACCENT, true);
        addTitle(slide, nullToDefault(item.title(), "培训要点"), 150, 48, 960, 58, 27);

        XSLFTextBox body = slide.createTextBox();
        body.setAnchor(new Rectangle(126, 150, 880, 330));
        List<String> bullets = item.bullets() == null || item.bullets().isEmpty()
                ? List.of("请结合来源文件讲解本页要点")
                : item.bullets();
        for (String bullet : bullets) {
            XSLFTextParagraph paragraph = body.addNewTextParagraph();
            paragraph.setBullet(true);
            paragraph.setLeftMargin(24.0);
            paragraph.setIndent(-18.0);
            paragraph.setSpaceAfter(8.0);
            XSLFTextRun run = paragraph.addNewTextRun();
            run.setText(bullet);
            run.setFontSize(21.0);
            run.setFontColor(INK);
            run.setFontFamily("Microsoft YaHei");
        }
        if (!body.getTextParagraphs().isEmpty()) {
            body.removeTextParagraph(body.getTextParagraphs().get(0));
        }

        addPanel(slide, 126, 506, 1028, 112, PANEL, new Color(225, 230, 235));
        addText(slide, "讲师备注", 152, 526, 160, 26, 15, ACCENT, true);
        addText(slide, nullToDefault(item.speakerNotes(), "结合部门实际案例讲解。"), 152, 556, 960, 48, 15, MUTED, false);
        addText(slide, "来源：" + String.join("、", safeList(item.sourceFiles())), 76, 662, 1120, 24, 12, MUTED, false);
        addFooter(slide, deck, String.format("%02d", item.index()));
    }

    private void addSourceSlide(XMLSlideShow ppt, TrainingDeckService.DeckGenerationResult deck) {
        XSLFSlide slide = ppt.createSlide();
        addBackground(slide);
        addTitle(slide, "来源文件", 86, 60, 900, 56, 30);
        XSLFTextBox box = slide.createTextBox();
        box.setAnchor(new Rectangle(110, 145, 980, 440));
        List<String> sources = deck.sources().stream()
                .map(source -> String.valueOf(source.get("fileName")))
                .distinct()
                .toList();
        if (sources.isEmpty()) {
            sources = List.of("知识库材料");
        }
        for (String source : sources) {
            XSLFTextParagraph paragraph = box.addNewTextParagraph();
            paragraph.setBullet(true);
            paragraph.setSpaceAfter(6.0);
            XSLFTextRun run = paragraph.addNewTextRun();
            run.setText(source);
            run.setFontSize(18.0);
            run.setFontColor(INK);
            run.setFontFamily("Microsoft YaHei");
        }
        if (!box.getTextParagraphs().isEmpty()) {
            box.removeTextParagraph(box.getTextParagraphs().get(0));
        }
        addText(slide, "请以来源文件原文作为最终制度依据。", 110, 622, 860, 32, 16, MUTED, false);
        addFooter(slide, deck, "来源");
    }

    private void addClosingSlide(XMLSlideShow ppt, TrainingDeckService.DeckGenerationResult deck) {
        XSLFSlide slide = ppt.createSlide();
        addBackground(slide);
        addPanel(slide, 118, 132, 1044, 382, SOFT, new Color(209, 232, 222));
        addText(slide, "培训结束", 176, 218, 860, 68, 38, INK, true);
        addText(slide, "请结合来源文件原文复核制度边界，并将疑问沉淀为问答对或术语词条。", 180, 310, 860, 56, 20, MUTED, false);
        addAccent(slide, 180, 398, 180, 8, ACCENT);
        addText(slide, "龙汇QA · 让制度知识可检索、可培训、可追溯", 180, 434, 760, 34, 17, ACCENT_DARK, true);
        addFooter(slide, deck, "END");
    }

    private void addTitle(XSLFSlide slide, String text, int x, int y, int w, int h, int size) {
        addText(slide, text, x, y, w, h, size, INK, true);
    }

    private void addText(XSLFSlide slide, String text, int x, int y, int w, int h, int size, Color color, boolean bold) {
        XSLFTextBox box = slide.createTextBox();
        box.setAnchor(new Rectangle(x, y, w, h));
        XSLFTextParagraph paragraph = box.addNewTextParagraph();
        paragraph.setTextAlign(TextParagraph.TextAlign.LEFT);
        XSLFTextRun run = paragraph.addNewTextRun();
        run.setText(text == null ? "" : text);
        run.setFontSize((double) size);
        run.setFontColor(color);
        run.setBold(bold);
        run.setFontFamily("Microsoft YaHei");
        box.removeTextParagraph(box.getTextParagraphs().get(0));
    }

    private void addBackground(XSLFSlide slide) {
        addAccent(slide, 0, 0, 1280, 720, Color.WHITE);
        addAccent(slide, 0, 650, 1280, 70, new Color(248, 250, 252));
    }

    private void addPanel(XSLFSlide slide, int x, int y, int w, int h, Color fill, Color line) {
        var shape = slide.createAutoShape();
        shape.setAnchor(new Rectangle(x, y, w, h));
        shape.setFillColor(fill);
        shape.setLineColor(line);
    }

    private void addAccent(XSLFSlide slide, int x, int y, int w, int h, Color color) {
        var shape = slide.createAutoShape();
        shape.setAnchor(new Rectangle(x, y, w, h));
        shape.setFillColor(color);
        shape.setLineColor(color);
    }

    private void addFooter(XSLFSlide slide, TrainingDeckService.DeckGenerationResult deck, String pageLabel) {
        addText(slide, "龙汇QA", 76, 682, 160, 24, 12, MUTED, true);
        addText(slide, nullToDefault(deck.title(), "培训课件"), 238, 682, 720, 24, 12, MUTED, false);
        addText(slide, pageLabel, 1120, 682, 80, 24, 12, MUTED, true);
    }

    private String nullToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private List<String> safeList(List<String> values) {
        return values == null || values.isEmpty() ? List.of("知识库材料") : values;
    }
}
