package com.cq.vibration.service.impl;

import com.cq.vibration.dto.TriAxisWaveform;
import com.cq.vibration.dto.VibrationFeatures;
import com.cq.vibration.service.AlarmLevelService;
import com.cq.vibration.service.AlarmMessageService;
import com.cq.vibration.service.CsvImportService;
import com.cq.vibration.service.CsvReaderService;
import com.cq.vibration.service.EquipmentStatusAggregationService;
import com.cq.vibration.service.VibrationFeatureService;
import com.cq.vibration.service.WaveformStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 四个泵组轴承传感器的 CSV 导入编排服务。
 * 输入是一条采集时间；输出是四条波形批次、特征以及必要的报警记录。
 */
@Service
public class CsvImportServiceImpl implements CsvImportService {
    private final CsvReaderService reader;
    private final VibrationFeatureService features;
    private final WaveformStorageService storage;
    private final AlarmLevelService levels;
    private final AlarmMessageService messages;
    private final EquipmentStatusAggregationService equipmentStatusAggregationService;
    private final JdbcTemplate database;

    @Value("${vibration.replay.dataset-root:../感应电机在不同负载条件下的三轴轴承振动数据集/fm6xzxnf36-2}")
    private String datasetRoot;

    public CsvImportServiceImpl(CsvReaderService reader, VibrationFeatureService features,
                                WaveformStorageService storage, AlarmLevelService levels,
                                AlarmMessageService messages,
                                EquipmentStatusAggregationService equipmentStatusAggregationService,
                                JdbcTemplate database) {
        this.reader = reader; this.features = features; this.storage = storage;
        this.levels = levels; this.messages = messages;
        this.equipmentStatusAggregationService = equipmentStatusAggregationService;
        this.database = database;
    }

    /**
     * 输入：采集整点时间。
     * 输出：V-01～V-04 各写入一秒三轴波形、特征，并在非正常时写入报警记录。
     */
    @Override
    @Transactional
    public void importScheduledBatch(LocalDateTime collectTime) {
        int stage = collectTime.getHour() / 6;
        int segment = collectTime.getHour() % 6;
        importOne("V-01", file(stage, "healthy without pulley.csv", "0.7mm-bearing-faults/0.7inner-100watt-67V2Iv.csv", "1.1mm-bearing-faults/1.1inner-200watt.csv", "1.7mm-bearing-faults/1.7inner-300watt.csv"), segment, collectTime);
        importOne("V-02", file(stage, "Healthy with pulley.csv", "0.7mm-bearing-faults/0.7outer-100watt-lB5LIS.csv", "1.1mm-bearing-faults/1.1outer-200watt.csv", "1.7mm-bearing-faults/1.7outer-300watt.csv"), segment, collectTime);
        importOne("V-03", file(stage, "healthy without pulley.csv", "0.9mm-bearing-faults/0.9inner-100watt.csv", "1.3mm-bearing-faults/1.3inner-200watt.csv", "1.5mm-bearing-faults/1.5inner-300watt.csv"), segment, collectTime);
        importOne("V-04", file(stage, "Healthy with pulley.csv", "0.9mm-bearing-faults/0.9outer-100watt.csv", "1.3mm-bearing-faults/1.3outer-200watt.csv", "1.5mm-bearing-faults/1.5outer-300watt.csv"), segment, collectTime);
        equipmentStatusAggregationService.refreshAll(collectTime);
    }

    /** 输入：传感器、CSV 相对路径、片段、时间；输出：该传感器的一条完整导入记录。 */
    private void importOne(String sensorCode, String relativeFile, int segment, LocalDateTime time) {
        TriAxisWaveform waveform = reader.readOneSecond(Paths.get(datasetRoot, relativeFile), segment);
        VibrationFeatures result = features.calculate(waveform);
        Map<String, Object> sensor = database.queryForMap("select id,warning_threshold,alarm_threshold,danger_threshold from jzjc01_sensor_device where sensor_code=?", sensorCode);
        String level = levels.level(result.totalVibration(), (Double) sensor.get("warning_threshold"), (Double) sensor.get("alarm_threshold"), (Double) sensor.get("danger_threshold"));
        Path path = storage.save(sensorCode, time, waveform);
        long sensorId = ((Number) sensor.get("id")).longValue();
        database.update("insert into jzjc01_waveform_batch(point_id,collect_time,sample_rate,sample_count,data_format,waveform_path,detection_status) values(?,?,?,?,?,?,?)", sensorId,time,waveform.sampleRate(),waveform.x().length,"JSON_GZIP_3AXIS",path.toString(),level);
        Long batchId = database.queryForObject("select last_insert_id()", Long.class);
        database.update("insert into jzjc01_vibration_feature(batch_id,x_acceleration_rms,y_acceleration_rms,z_acceleration_rms,x_velocity_rms,y_velocity_rms,z_velocity_rms,total_vibration,max_axis) values(?,?,?,?,?,?,?,?,?)", batchId,result.xAccelerationRms(),result.yAccelerationRms(),result.zAccelerationRms(),result.xVelocityRms(),result.yVelocityRms(),result.zVelocityRms(),result.totalVibration(),result.maxAxis());
        database.update("update jzjc01_sensor_device set current_status=?,online_status=1,last_collect_time=? where id=?", level,time,sensorId);
        if (!"NORMAL".equals(level)) writeAlarm(batchId, sensorId, level, result.totalVibration(), sensor, time);
    }

    /** 输入：当前等级及振值；输出：向 jzjc01_alarm_record 新增一条图示格式的报警记录。 */
    private void writeAlarm(Long batchId, long sensorId, String level, double total, Map<String,Object> sensor, LocalDateTime time) {
        double threshold = ((Number) sensor.get(level.toLowerCase() + "_threshold")).doubleValue();
        database.update("insert into jzjc01_alarm_record(batch_id,point_id,alarm_level,alarm_type,alarm_value,message,alarm_time,status) values(?,?,?,?,?,?,?,?)", batchId,sensorId,level,"总振值异常",total,messages.format(level,total,threshold),time,"ACTIVE");
    }

    /** 输入：阶段序号和四个候选文件；输出：该阶段对应的 CSV 相对路径。 */
    private String file(int stage, String normal, String warning, String alarm, String danger) {
        String selected = new String[]{normal, warning, alarm, danger}[stage];
        return selected.contains("/") ? selected : "Healthy bearing data/" + selected;
    }
}
