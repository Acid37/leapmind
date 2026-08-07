package com.treepeople.leapmindtts.service.lesson;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.mapper.TeachingContentMapper;
import com.treepeople.leapmindtts.pojo.dto.PPTAudioInfo;
import com.treepeople.leapmindtts.pojo.dto.PPTAudioSegment;
import com.treepeople.leapmindtts.pojo.dto.PptStructureDTO;
import com.treepeople.leapmindtts.pojo.entity.TeachingContent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 新体系 (TtsBatchServiceImpl → [AUDIO_URL:] 回填 → teaching_contents.ppt_structure)
 *    透明桥接到
 * 老前端播放器接口（零改动）。解析不到则返回 null，上层 fallback 到 ppt_page_audio 老表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NarrationBridgeService {

    private static final Pattern AUDIO_URL_RE = Pattern.compile(
            "^\\s*\\[AUDIO_URL:\\s*([^\\]\\r\\n]+)\\s*\\](\\s*\\R)?", Pattern.MULTILINE);
    private static final byte[] DELIM_PREFIX = new byte[]{(byte) 0xFF, (byte) 0xA5, (byte) 0xB2, 0x7C};

    private final TeachingContentMapper teachingContentMapper;
    private final ObjectMapper objectMapper;

    @Value("${m8.base-url:http://localhost:8080}")
    private String m8BaseUrl;

    // ================================================================
    //  对外 3 个入口
    // ================================================================

    /** GET /ppt/{courseId}/page/{pageNumber} — 返回 1 条 PPTAudioSegment */
    public List<PPTAudioSegment> getSegments(String courseId, Integer pageNumber) {
        TeachingContent tc = loadContent(courseId);
        PptStructureDTO structure = parseStructure(tc);
        if (structure == null) return null;

        PptStructureDTO.SlideDTO slide = findSlide(structure, pageNumber);
        if (slide == null) return List.of();
        String[] parsed = parseNarration(slide.getNotes());
        if (parsed == null) {
            log.info("NarrationBridge P{} 未生成 TTS (notes 无 [AUDIO_URL:] 前缀), slide={}", pageNumber, slide.getTitle());
            return List.of();
        }
        String text = parsed[1];
        long durMs = Math.max(500L, (long) (text.length() * 300.0));
        return List.of(PPTAudioSegment.builder()
                .courseId(courseId).slidePageNumber(pageNumber).slideTitle(slide.getTitle())
                .slideType(slide.getType()).contentPointIndex(0).segmentIndex(0)
                .originalText(text).polishedText(text).textContent(text)
                .audioSize(32_000L * durMs / 1000L).duration(durMs)
                .audioFormat("wav").sampleRate(16000)
                .createdAt(tc.getCreatedAt() != null ? tc.getCreatedAt() : LocalDateTime.now())
                .build());
    }

    /** GET /ppt/{courseId}/page/{pageNumber}/audio — 返回分隔符包装的 WAV */
    public byte[] downloadAndWrapAudio(String courseId, Integer pageNumber) {
        PptStructureDTO structure = parseStructure(loadContent(courseId));
        if (structure == null) return null;
        PptStructureDTO.SlideDTO slide = findSlide(structure, pageNumber);
        String[] parsed = (slide == null) ? null : parseNarration(slide.getNotes());
        if (parsed == null) return null;

        byte[] wav = downloadBytes(parsed[0], 15_000, 120_000);
        if (wav == null || wav.length == 0) {
            log.warn("NarrationBridge P{} 音频下载为空或失败, url={}", pageNumber, parsed[0]);
            return null;
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(wav.length + 64)) {
            baos.write(DELIM_PREFIX);
            baos.write(intTo4BytesBE(wav.length));
            baos.write(wav);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("NarrationBridge P{} 打包失败", pageNumber, e);
            return null;
        }
    }

    /** GET /ppt/{courseId} — 整体统计 */
    public PPTAudioInfo getInfo(String courseId) {
        TeachingContent tc = loadContent(courseId);
        PptStructureDTO structure = parseStructure(tc);
        if (structure == null || structure.getSlides() == null || structure.getSlides().isEmpty()) return null;

        List<PPTAudioInfo.PPTPageInfo> pages = new ArrayList<>(structure.getSlides().size());
        int totalSegs = 0; long totalDur = 0L, totalSize = 0L;

        for (int i = 0; i < structure.getSlides().size(); i++) {
            PptStructureDTO.SlideDTO s = structure.getSlides().get(i);
            String[] parsed = parseNarration(s.getNotes());
            long dur = 0, size = 0; int segs = 0;
            if (parsed != null) {
                segs = 1;
                dur = Math.max(500L, (long) (parsed[1].length() * 300.0));
                size = 32_000L * dur / 1000L;
                totalDur += dur; totalSize += size; totalSegs += 1;
            }
            pages.add(PPTAudioInfo.PPTPageInfo.builder()
                    .pageNumber(s.getPageNum() != null ? s.getPageNum() : (i + 1))
                    .pageTitle(s.getTitle()).slideType(s.getType())
                    .segmentCount(segs).pageDuration(dur).build());
        }
        String title = tc.getTitle();
        if (title == null || title.isBlank()) title = structure.getTitle();
        if (title == null || title.isBlank()) title = "未命名PPT";

        return PPTAudioInfo.builder()
                .courseId(courseId).title(title).totalPages(pages.size())
                .totalSegments(totalSegs).totalDuration(totalDur)
                .totalAudioSize(totalSize).pages(pages).createdAt(tc.getCreatedAt()).build();
    }

    // ================================================================
    //  内部工具
    // ================================================================

    /** [url, text] 或 null */
    private static String[] parseNarration(String notes) {
        if (notes == null || notes.isBlank()) return null;
        Matcher m = AUDIO_URL_RE.matcher(notes);
        if (!m.find()) return null;
        String rawUrl = m.group(1).trim();
        if (rawUrl.contains("|||")) {
            // 长文本多段合成时暂只取第一段（播放器一页一WAV设计）
            int p = rawUrl.indexOf("|||");
            log.warn("NarrationBridge notes 含多段 audioUrl (共{}段)，暂取第一段播放",
                    rawUrl.split("\\|\\|\\|", -1).length);
            rawUrl = rawUrl.substring(0, p).trim();
        }
        String text = m.replaceFirst("").trim();
        if (text.isBlank()) text = "(旁白)";
        return new String[]{rawUrl, text};
    }

    private TeachingContent loadContent(String courseId) {
        Long id = parseLongSafe(courseId);
        if (id == null) return null;
        TeachingContent tc = null;
        try {
            tc = teachingContentMapper.selectOne(
                    new QueryWrapper<TeachingContent>()
                            .eq("prep_id", id).orderByDesc("id").last("limit 1"));
        } catch (Exception ignore) {}
        if (tc == null) {
            try { tc = teachingContentMapper.selectById(id); } catch (Exception ignore) {}
        }
        return tc;
    }

    private PptStructureDTO parseStructure(TeachingContent tc) {
        if (tc == null || tc.getPptStructure() == null || tc.getPptStructure().isBlank()) return null;
        try {
            PptStructureDTO s = PptStructureDTO.parse(objectMapper, tc.getPptStructure());
            return (s.getSlides() == null || s.getSlides().isEmpty()) ? null : s;
        } catch (Exception e) {
            log.warn("NarrationBridge 解析 ppt_structure 失败, id={} err={}", tc.getId(), e.getMessage());
            return null;
        }
    }

    private static PptStructureDTO.SlideDTO findSlide(PptStructureDTO structure, Integer pageNumber) {
        List<PptStructureDTO.SlideDTO> slides = structure.getSlides();
        if (slides == null || slides.isEmpty()) return null;
        for (PptStructureDTO.SlideDTO s : slides) if (pageNumber.equals(s.getPageNum())) return s;
        if (pageNumber >= 1 && pageNumber <= slides.size()) return slides.get(pageNumber - 1);
        return null;
    }

    private static Long parseLongSafe(String s) {
        if (s == null) return null;
        try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return null; }
    }
    private static byte[] intTo4BytesBE(int n) {
        return new byte[]{(byte)(n>>>24),(byte)(n>>>16),(byte)(n>>>8),(byte)n};
    }

    /** 支持 http(s)/file/相对路径("/api/...") 多段 3xx 重定向。开放接口无 Authorization。 */
    private byte[] downloadBytes(String urlStr, int connTimeoutMs, int readTimeoutMs) {
        if (urlStr == null || urlStr.isBlank()) return null;
        try {
            String resolved = normalizeUrl(urlStr);
            if (resolved.startsWith("file:")) return Files.readAllBytes(Paths.get(URI.create(resolved)));
            URL url = URI.create(resolved).toURL();
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(connTimeoutMs); c.setReadTimeout(readTimeoutMs);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "NarrationBridge/1.0");
            int code = c.getResponseCode(); int redirects = 0;
            while ((code == 301 || code == 302 || code == 303 || code == 307 || code == 308) && redirects < 3) {
                String loc = c.getHeaderField("Location"); if (loc == null) break;
                c.disconnect();
                url = URI.create(normalizeUrl(loc)).toURL();
                c = (HttpURLConnection) url.openConnection();
                c.setConnectTimeout(connTimeoutMs); c.setReadTimeout(readTimeoutMs);
                c.setRequestProperty("User-Agent", "NarrationBridge/1.0");
                code = c.getResponseCode(); redirects++;
            }
            try (InputStream in = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream()) {
                return in == null ? null : in.readAllBytes();
            } finally { c.disconnect(); }
        } catch (Exception e) {
            log.warn("NarrationBridge 下载失败 url={} err={}", urlStr, e.getMessage());
            return null;
        }
    }

    /** 相对路径 → 拼 m8BaseUrl；无协议 → 补 http:// */
    private String normalizeUrl(String urlStr) {
        if (urlStr == null) return "";
        String u = urlStr.trim();
        if (u.startsWith("http://") || u.startsWith("https://") || u.startsWith("file:")) return u;
        String base = (m8BaseUrl == null || m8BaseUrl.isBlank()) ? "http://localhost:8080" : m8BaseUrl.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return u.startsWith("/") ? (base + u) : (base + "/" + u);
    }
}
