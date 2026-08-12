package com.cq.vibration.service.impl;

import com.cq.vibration.dto.TriAxisWaveform;
import com.cq.vibration.service.CsvReaderService;
import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** 读取数据集 CSV 中连续的一秒三轴加速度数据。 */
@Service
public class CsvReaderServiceImpl implements CsvReaderService {
    private static final int SAMPLE_RATE = 10_000;

    /**
     * 输入：CSV 文件与片段序号，例如 segment=2 表示第 20,001～30,000 条采样。
     * 输出：包含 10,000 个 X/Y/Z 采样点的 TriAxisWaveform。
     */
    @Override
    public TriAxisWaveform readOneSecond(Path file, int segment) {
        if (segment < 0) throw new IllegalArgumentException("片段序号不能为负数");
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            reader.readLine(); // 跳过 CSV 表头
            skipRows(reader, segment * SAMPLE_RATE);
            double[] x = new double[SAMPLE_RATE];
            double[] y = new double[SAMPLE_RATE];
            double[] z = new double[SAMPLE_RATE];
            for (int index = 0; index < SAMPLE_RATE; index++) {
                String line = reader.readLine();
                if (line == null) throw new IllegalStateException("CSV 不足一秒数据");
                String[] values = line.split(",");
                if (values.length < 4) throw new IllegalStateException("CSV 行格式错误");
                x[index] = Double.parseDouble(values[1].trim());
                y[index] = Double.parseDouble(values[2].trim());
                z[index] = Double.parseDouble(values[3].trim());
            }
            return new TriAxisWaveform(x, y, z, SAMPLE_RATE, file.getFileName().toString(), segment * SAMPLE_RATE + 2);
        } catch (IOException exception) {
            throw new IllegalStateException("读取 CSV 失败: " + file, exception);
        }
    }

    /** 输入：读取器与需跳过的行数；输出：读取器定位至目标片段首行。 */
    private void skipRows(BufferedReader reader, int count) throws IOException {
        for (int index = 0; index < count; index++) {
            if (reader.readLine() == null) throw new IllegalStateException("CSV 数据不足");
        }
    }
}
