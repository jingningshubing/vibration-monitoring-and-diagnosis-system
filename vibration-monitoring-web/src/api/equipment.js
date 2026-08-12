import axios from 'axios'

/**
 * 查询全部设备。
 *
 * <p>输入：无。输出：后端返回的设备数组，每项包含 equipCode、equipName、
 * equipType 和 installPosition 等字段。</p>
 *
 * @returns {Promise<Array>} 设备列表
 */
export async function fetchEquipment() {
  const response = await axios.get('/api/vibration/equipment')
  return response.data
}

/**
 * 查询全部设备的当前状态汇总。
 *
 * <p>输入：无。输出：每台设备的总振值、状态、来源测点及采集时间。</p>
 *
 * @returns {Promise<Array>} 设备状态汇总列表
 */
export async function fetchEquipmentStatus() {
  const response = await axios.get('/api/vibration/equipment/status')
  return response.data
}

/**
 * 查询一台设备下各传感器最新批次的三轴速度有效值。
 *
 * <p>输入：设备 ID。输出：传感器的 X、Y、Z 轴 mm/s RMS 数值。</p>
 *
 * @param {number} equipmentId 设备主键
 * @returns {Promise<Array>} 传感器三轴振动列表
 */
export async function fetchSensorVibrations(equipmentId) {
  const response = await axios.get(
    `/api/vibration/equipment/${equipmentId}/sensor-vibrations`,
  )
  return response.data
}

/**
 * 查询设备总振值四档状态的数量分布。
 *
 * <p>输入：无。输出：正常、预警、报警、危险及总设备数，离线按正常统计。</p>
 *
 * @returns {Promise<Object>} 总振值状态统计
 */
export async function fetchEquipmentVibrationStatusStatistics() {
  const response = await axios.get('/api/vibration/equipment/status/statistics')
  return response.data
}

/** 查询首页测点当前报警状态的分布统计。 */
export async function fetchSensorVibrationStatusStatistics() {
  const response = await axios.get('/api/vibration/equipment/sensor-status/statistics')
  return response.data
}

export async function fetchHomeStatistics() {
  const response = await axios.get('/api/vibration/home/statistics')
  return response.data
}

/** 查询振动预警顶部的设备和传感器筛选项。 */
export async function fetchVibrationAlarmOptions() {
  const response = await axios.get('/api/vibration/alarms/options')
  return response.data
}

/** 查询振动预警表格的分页记录。 */
export async function fetchVibrationAlarms(params) {
  const response = await axios.get('/api/vibration/alarms', { params })
  return response.data
}

/** 查询当前振动预警筛选范围内的报警台数和报警次数。 */
export async function fetchVibrationAlarmStatistics(params) {
  const response = await axios.get('/api/vibration/alarms/statistics', { params })
  return response.data
}

export async function fetchDeviceAxisTrend(params) {
  const response = await axios.get('/api/vibration/device-trends/axes', { params })
  return response.data
}

/** 查询指定采集批次的三轴波形、速度波形和 FFT 频谱。 */
export async function fetchWaveformDetail(batchId, downsample = 2000) {
  const response = await axios.get(`/api/vibration/waveform-batches/${batchId}`, { params: { downsample } })
  return response.data
}

/** 按需查询指定波形批次的 ONNX 轴承状态辅助诊断，不会改变预警等级。 */
export async function fetchBearingDiagnosis(batchId) {
  const response = await axios.get(`/api/vibration/diagnosis/waveform-batches/${batchId}`)
  return response.data
}

/** 查询智能诊断页面的分页结果。 */
export async function fetchIntelligentDiagnoses(params) {
  const response = await axios.get('/api/vibration/intelligent-diagnoses', { params })
  return response.data
}

/** 查询智能诊断页面的诊断台数/次数分类统计。 */
export async function fetchIntelligentDiagnosisStatistics(params) {
  const response = await axios.get('/api/vibration/intelligent-diagnoses/statistics', { params })
  return response.data
}

/** 查询设备状态卡片所需的每台设备最新智能诊断结论。 */
export async function fetchLatestDiagnosisByEquipment() {
  const response = await axios.get('/api/vibration/intelligent-diagnoses/latest-by-equipment')
  return response.data
}
