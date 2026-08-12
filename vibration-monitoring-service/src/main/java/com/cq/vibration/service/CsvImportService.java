package com.cq.vibration.service;

import java.time.LocalDateTime;

/** 编排一次完整的 CSV 导入：读波形、算特征、存文件、写库并生成报警。 */
public interface CsvImportService {
    /** 输入：本次采集时间；输出：为 V-01～V-04 各写入一条批次记录。 */
    void importScheduledBatch(LocalDateTime collectTime);
}
