/**
 * 全系统统一的振动状态颜色与名称。
 * 后续设备、测点、报警记录和统计页面均使用此配置。
 */
export const statusLevels = [
  { code: 'NORMAL', name: '正常', color: '#75be4f' },
  { code: 'WARNING', name: '预警', color: '#ffe000' },
  { code: 'ALARM', name: '报警', color: '#ff9d00' },
  { code: 'DANGER', name: '危险', color: '#cf3535' },
]
