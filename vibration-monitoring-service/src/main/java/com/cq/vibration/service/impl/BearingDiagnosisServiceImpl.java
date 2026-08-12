package com.cq.vibration.service.impl;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.cq.vibration.dto.BearingDiagnosis;
import com.cq.vibration.dto.TriAxisWaveform;
import com.cq.vibration.service.BearingDiagnosisService;
import com.cq.vibration.service.WaveformStorageService;
import jakarta.annotation.PreDestroy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** 以训练时相同的 58 项特征调用随机森林 ONNX 模型；仅用于用户主动查看的辅助诊断。 */
@Service
public class BearingDiagnosisServiceImpl implements BearingDiagnosisService {
    private static final int WINDOW_SIZE = 1000;
    private static final int FEATURE_COUNT = 58;
    private static final float CONFIDENCE_THRESHOLD = .60F;
    private final JdbcTemplate jdbcTemplate;
    private final WaveformStorageService storage;
    private final OrtEnvironment environment = OrtEnvironment.getEnvironment();
    private final OrtSession session;

    public BearingDiagnosisServiceImpl(JdbcTemplate jdbcTemplate, WaveformStorageService storage) {
        this.jdbcTemplate = jdbcTemplate;
        this.storage = storage;
        try {
            session = environment.createSession(new ClassPathResource("models/bearing_random_forest.onnx").getInputStream().readAllBytes(), new OrtSession.SessionOptions());
        } catch (IOException | OrtException exception) {
            throw new IllegalStateException("无法加载轴承诊断 ONNX 模型", exception);
        }
    }

    /** {@inheritDoc} */
    @Override
    public BearingDiagnosis diagnose(Long batchId) {
        String waveformPath = jdbcTemplate.query("SELECT waveform_path FROM jzjc01_waveform_batch WHERE id=?", (rs, row) -> rs.getString(1), batchId)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "采集批次不存在"));
        TriAxisWaveform waveform = storage.load(waveformPath);
        validate(waveform);
        int windows = waveform.x().length / WINDOW_SIZE;
        float[] input = new float[windows * FEATURE_COUNT];
        for (int index = 0; index < windows; index++) {
            double[][] window = new double[][]{
                    Arrays.copyOfRange(waveform.x(), index * WINDOW_SIZE, (index + 1) * WINDOW_SIZE),
                    Arrays.copyOfRange(waveform.y(), index * WINDOW_SIZE, (index + 1) * WINDOW_SIZE),
                    Arrays.copyOfRange(waveform.z(), index * WINDOW_SIZE, (index + 1) * WINDOW_SIZE)
            };
            double[] features = features(window, waveform.sampleRate());
            for (int feature = 0; feature < FEATURE_COUNT; feature++) input[index * FEATURE_COUNT + feature] = (float) features[feature];
        }
        try (OnnxTensor tensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(input), new long[]{windows, FEATURE_COUNT});
             OrtSession.Result result = session.run(Map.of(session.getInputNames().iterator().next(), tensor))) {
            float[][] probabilities = (float[][]) result.get(1).getValue();
            return summarize(probabilities);
        } catch (OrtException exception) {
            throw new IllegalStateException("轴承诊断推理失败", exception);
        }
    }

    private BearingDiagnosis summarize(float[][] probabilities) {
        double[] average = new double[3]; int[] votes = new int[3];
        for (float[] row : probabilities) { int best = best(row); votes[best]++; for (int i = 0; i < 3; i++) average[i] += row[i]; }
        for (int i = 0; i < 3; i++) average[i] /= probabilities.length;
        int best = best(average); double confidence = average[best]; double stability = (double) votes[best] / probabilities.length;
        String label = new String[]{"HEALTHY", "INNER_RACE", "OUTER_RACE"}[best];
        boolean uncertain = confidence < CONFIDENCE_THRESHOLD || stability < CONFIDENCE_THRESHOLD;
        String diagnosis = uncertain ? "当前数据特征不明确" : switch (label) { case "HEALTHY" -> "健康"; case "INNER_RACE" -> "轴承内圈故障"; default -> "轴承外圈故障"; };
        String description = maintenanceAdvice(label, uncertain);
        return new BearingDiagnosis(label, diagnosis, confidence, stability, probabilities.length,
                new BearingDiagnosis.Probabilities(average[0], average[1], average[2]), description);
    }

    private void validate(TriAxisWaveform waveform) {
        if (waveform.sampleRate() <= 0 || waveform.x() == null || waveform.y() == null || waveform.z() == null
                || waveform.x().length != waveform.y().length || waveform.x().length != waveform.z().length || waveform.x().length < WINDOW_SIZE)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "该批次三轴波形不完整，无法进行智能诊断");
    }
    private int best(float[] values) { int index = 0; for (int i = 1; i < values.length; i++) if (values[i] > values[index]) index = i; return index; }
    private int best(double[] values) { int index = 0; for (int i = 1; i < values.length; i++) if (values[i] > values[index]) index = i; return index; }
    /** 按模型结论给出保守的维护建议；建议不改变当前报警等级，也不替代现场检修确认。 */
    private String maintenanceAdvice(String label, boolean uncertain) {
        if (uncertain) return "建议持续跟踪后续批次趋势，并结合现场点检、润滑和运行工况复核。";
        return switch (label) {
            case "HEALTHY" -> "建议保持现有巡检频率，持续观察振动趋势。";
            case "INNER_RACE" -> "建议安排轴承内圈及配合状态检查，复核润滑、轴向载荷和安装情况。";
            default -> "建议检查轴承外圈座、润滑和紧固状态，并结合现场点检确认。";
        };
    }

    /** 特征顺序严格对应 Python 训练脚本：X 的 17 项、Y 的 17 项、Z 的 17 项，再加 7 项三轴特征。 */
    private double[] features(double[][] axes, int rate) {
        double[] result = new double[FEATURE_COUNT]; int at = 0; double[] rms = new double[3];
        for (int axis = 0; axis < 3; axis++) { double[] feature = axisFeatures(axes[axis], rate); System.arraycopy(feature, 0, result, at, feature.length); at += feature.length; rms[axis] = feature[0]; }
        double total = Math.sqrt(rms[0] * rms[0] + rms[1] * rms[1] + rms[2] * rms[2]); result[at++] = total;
        for (double value : rms) result[at++] = value / Math.max(total, 1e-12);
        result[at++] = correlation(axes[0], axes[1]); result[at++] = correlation(axes[0], axes[2]); result[at] = correlation(axes[1], axes[2]);
        return result;
    }

    private double[] axisFeatures(double[] raw, int rate) {
        int n = raw.length; double mean = Arrays.stream(raw).average().orElse(0); double[] x = Arrays.stream(raw).map(value -> value - mean).toArray();
        double sumSquare = 0, abs = 0, max = 0, maximum = x[0], min = x[0]; for (double value : x) { sumSquare += value * value; abs += Math.abs(value); max = Math.max(max, Math.abs(value)); maximum = Math.max(maximum, value); min = Math.min(min, value); }
        double rms = Math.sqrt(sumSquare / n), std = rms, absMean = abs / n, skew = 0, kurtosis = 0;
        for (double value : x) { double normalized = value / Math.max(std, 1e-12); skew += normalized * normalized * normalized; kurtosis += normalized * normalized * normalized * normalized; }
        double[] power = new double[n / 2 + 1]; double totalPower = 0, weightedFrequency = 0; int dominant = 1;
        for (int k = 0; k <= n / 2; k++) { double re = 0, im = 0; for (int i = 0; i < n; i++) { double window = .5 - .5 * Math.cos(2 * Math.PI * i / (n - 1)); double angle = 2 * Math.PI * k * i / n; re += x[i] * window * Math.cos(angle); im -= x[i] * window * Math.sin(angle); } power[k] = re * re + im * im; if (k > 0) { totalPower += power[k]; weightedFrequency += k * (double) rate / n * power[k]; if (power[k] > power[dominant]) dominant = k; } }
        double entropy = 0; for (int k = 1; k < power.length; k++) { double p = power[k] / Math.max(totalPower, 1e-12); entropy -= p * Math.log(p + 1e-12); }
        double[] result = new double[17]; result[0] = rms; result[1] = std; result[2] = absMean; result[3] = max; result[4] = maximum - min; result[5] = max / Math.max(rms, 1e-12); result[6] = rms / Math.max(absMean, 1e-12); result[7] = skew / n; result[8] = kurtosis / n; result[9] = dominant * (double) rate / n; result[10] = weightedFrequency / Math.max(totalPower, 1e-12); result[11] = entropy;
        int[][] bands = {{0,500},{500,1000},{1000,2000},{2000,4000},{4000,5000}}; for (int i = 0; i < bands.length; i++) { double energy = 0; for (int k = 0; k < power.length; k++) { double f = k * (double) rate / n; if (f >= bands[i][0] && f < bands[i][1]) energy += power[k]; } result[12 + i] = energy / Math.max(Arrays.stream(power).sum(), 1e-12); }
        return result;
    }
    private double correlation(double[] a, double[] b) { double ma = Arrays.stream(a).average().orElse(0), mb = Arrays.stream(b).average().orElse(0), numerator = 0, aa = 0, bb = 0; for (int i = 0; i < a.length; i++) { double da = a[i] - ma, db = b[i] - mb; numerator += da * db; aa += da * da; bb += db * db; } return numerator / Math.sqrt(Math.max(aa * bb, 1e-24)); }
    @PreDestroy public void close() throws OrtException { session.close(); }
}
