package com.cq.vibration.service.impl;

import com.cq.vibration.dto.TriAxisWaveform;
import com.cq.vibration.service.WaveformStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.io.OutputStream;
import java.io.InputStream;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import java.util.zip.GZIPInputStream;

/** 将一秒三轴波形保存为 JSON_GZIP 文件。 */
@Service
public class WaveformStorageServiceImpl implements WaveformStorageService {
    private final ObjectMapper objectMapper;
    public WaveformStorageServiceImpl(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }
    /** 输入：传感器、时间、三轴波形；输出：data/waveforms 下的 json.gz 文件路径。 */
    @Override public Path save(String sensorCode, LocalDateTime time, TriAxisWaveform waveform) {
        try {
            Path path = Paths.get("data", "waveforms", sensorCode, time.toLocalDate().toString(), time.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".json.gz");
            Files.createDirectories(path.getParent());
            Map<String,Object> payload = Map.of("sensorCode",sensorCode,"collectTime",time.toString(),"sampleRate",waveform.sampleRate(),"sampleCount",waveform.x().length,"unit","g","axes",Map.of("x",waveform.x(),"y",waveform.y(),"z",waveform.z()),"source",Map.of("file",waveform.sourceFile(),"startRow",waveform.startRow()));
            try (OutputStream stream = new GZIPOutputStream(Files.newOutputStream(path))) { objectMapper.writeValue(stream, payload); }
            return path;
        } catch (Exception exception) { throw new IllegalStateException("保存三轴波形失败", exception); }
    }

    /** {@inheritDoc} */
    @Override public TriAxisWaveform load(String waveformPath) {
        try (InputStream stream = new GZIPInputStream(Files.newInputStream(Paths.get(waveformPath)))) {
            var root = objectMapper.readTree(stream);
            return new TriAxisWaveform(objectMapper.convertValue(root.at("/axes/x"), double[].class),
                    objectMapper.convertValue(root.at("/axes/y"), double[].class), objectMapper.convertValue(root.at("/axes/z"), double[].class),
                    root.path("sampleRate").asInt(), root.at("/source/file").asText(""), root.at("/source/startRow").asInt());
        } catch (Exception exception) { throw new IllegalStateException("读取三轴波形失败: " + waveformPath, exception); }
    }
}
